package com.hermes.android.voice

import android.content.Context
import com.hermes.android.domain.voice.VoiceAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** What the voice conversation needs from the open chat. */
interface VoiceHost {
    /** An agent turn is running; read straight from the chat's state, never a lagging copy. */
    fun isBusy(): Boolean

    /** The assistant bubbles of the turn the last [submit] started, in order, as far as they have streamed. */
    fun replyParts(): List<ReplyPart>

    /** Sends [text] as a spoken turn; [interrupted] when it cut Hermes off. False when refused. */
    suspend fun submit(text: String, interrupted: Boolean): Boolean

    /** Stops the running turn (the Stop button) and returns once the server has been told. */
    suspend fun interrupt()

    class ReplyPart(val id: String, val text: String)
}

enum class VoiceStatus { Off, Listening, Transcribing, Thinking, Speaking }

/** Known problems, worded by the screen in the user's language. */
enum class VoiceProblem {
    /** Neither the server nor the phone can turn speech into text. */
    NoSpeechToText,

    /** The chat had no live session to send the turn to. */
    NotSent,
}

data class VoiceUiState(
    val status: VoiceStatus = VoiceStatus.Off,
    val muted: Boolean = false,
    /** What was last heard from the user. */
    val heard: String = "",
    /** The sentence being spoken. */
    val speaking: String = "",
    /** Hermes' voices are off (no TTS provider): the phone's own voice speaks. */
    val phoneVoice: Boolean = false,
    /** No server speech-to-text: the phone's recognizer listens, and a barge-in can't keep the first words. */
    val phoneListening: Boolean = false,
    /** The user chose the phone's voice over Hermes' own. */
    val phoneVoiceChosen: Boolean = false,
    val problem: VoiceProblem? = null,
    /** Any other failure, as the system worded it. */
    val error: String? = null,
)

/**
 * Hands-free voice conversation with Hermes, ported from Hermes Desktop's
 * use-voice-conversation.ts (MIT, Nous Research): listen until the user goes quiet, transcribe,
 * send the turn, speak the reply sentence by sentence while it streams, and listen through the
 * whole turn so the user can talk over Hermes. Talking over it cuts the voice (and the turn, while
 * it is still generating), and what they said becomes the next turn, marked `interrupted` so the
 * model knows it was cut off. A spoken stop phrase ends the conversation; "wait" just stops it.
 *
 * Turns are sent with `voice_turn`, which the gateway routes to `auxiliary.voice_chat` when the
 * user set a faster model there.
 *
 * The loop runs on [scope] (the main thread); the mic runs on IO and its barge-in callback only
 * does what is safe from any thread.
 */
class VoiceConversation(
    context: Context,
    private val scope: CoroutineScope,
    private val audio: VoiceAudio,
    private val host: VoiceHost,
) {
    private val appContext = context.applicationContext
    private val _level = MutableStateFlow(0f)

    /** Mic level 0..1, for the screen. */
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private val mic = MicCapture(_level)
    private val deviceInput = DeviceSpeechInput(appContext, _level)
    private val player = VoicePlayer()
    private val route = VoiceAudioRoute(appContext)
    @Volatile private var localSpeech: LocalSpeech? = null
    private var job: Job? = null

    @Volatile private var endTakeNow = false

    /** The phone's voice, chosen on the screen; kept for the next conversation too. */
    @Volatile private var preferPhoneVoice = false
    private val unmuted = MutableStateFlow(true)

    private val anyPlaying: Boolean get() = player.isPlaying || localSpeech?.isPlaying == true

    fun start() {
        if (job?.isActive == true) return
        val previous = job
        job = scope.launch {
            // A conversation ended a moment ago may still be putting the audio back; its last
            // state ("off") must land before this one's first.
            previous?.cancelAndJoin()
            _state.value = VoiceUiState(status = VoiceStatus.Listening, phoneVoiceChosen = preferPhoneVoice)
            unmuted.value = true
            route.acquire()
            try {
                run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Voice] conversation failed")
                _state.update { it.copy(error = e.message ?: "Voice conversation failed") }
            } finally {
                player.stop()
                localSpeech?.shutdown()
                localSpeech = null
                route.release()
                _level.value = 0f
                _state.update { it.copy(status = VoiceStatus.Off, speaking = "") }
            }
        }
    }

    /** Ends the conversation; a turn still running keeps going in the chat. */
    fun end() {
        job?.cancel()
        player.stop()
        localSpeech?.stop()
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        _state.update { it.copy(muted = muted) }
        unmuted.value = !muted
    }

    /** "I'm done talking": ends the take now instead of waiting for silence. */
    fun finishTake() {
        if (_state.value.status == VoiceStatus.Listening) endTakeNow = true
    }

    /** Hermes' voice ⇄ the phone's; takes effect from the next sentence. */
    fun togglePhoneVoice() {
        preferPhoneVoice = !preferPhoneVoice
        _state.update { it.copy(phoneVoiceChosen = preferPhoneVoice) }
    }

    fun clearError() = _state.update { it.copy(error = null, problem = null) }

    private suspend fun run() {
        val serverStt = runCatching { audio.serverSttAvailable() }
            .onFailure { if (it is CancellationException) throw it else Timber.w(it, "[Voice] STT probe failed") }
            .getOrDefault(false)
        if (!serverStt && !deviceInput.available) {
            _state.update { it.copy(problem = VoiceProblem.NoSpeechToText) }
            return
        }
        _state.update { it.copy(phoneListening = !serverStt) }

        var next: Utterance? = null
        while (currentCoroutineContext().isActive) {
            if (_state.value.muted) {
                _state.update { it.copy(status = VoiceStatus.Listening, speaking = "") }
                unmuted.first { it }
            }
            val utterance = next ?: listenUnlessMuted(serverStt) ?: continue
            next = null
            _state.update { it.copy(heard = utterance.text, error = null, problem = null) }
            if (isVoiceStopCommand(utterance.text)) return
            if (isVoiceHoldCommand(utterance.text)) continue
            next = runTurn(utterance, serverStt)
        }
    }

    private class Utterance(val text: String, val interrupted: Boolean)

    /** [listen], dropped the moment Mute is tapped (the mic goes off at once). */
    private suspend fun listenUnlessMuted(serverStt: Boolean): Utterance? = coroutineScope {
        val take = async { listen(serverStt) }
        val muteWatch = launch {
            unmuted.first { !it }
            take.cancel()
        }
        try {
            take.await()
        } catch (e: CancellationException) {
            if (!isActive) throw e
            null // muted
        } finally {
            muteWatch.cancel()
        }
    }

    /** One listening take → its transcript, or null when nothing usable was said. */
    private suspend fun listen(serverStt: Boolean): Utterance? {
        endTakeNow = false
        _state.update { it.copy(status = VoiceStatus.Listening, speaking = "") }
        val text = if (serverStt) {
            val take = mic.listen(endNow = { endTakeNow })
            if (!take.heardSpeech) return null
            _state.update { it.copy(status = VoiceStatus.Transcribing) }
            transcribeOrReport(take.wav) ?: return null
        } else {
            try {
                deviceInput.listenOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Voice] device recognition failed")
                _state.update { it.copy(error = e.message) }
                delay(ERROR_BACKOFF_MS)
                return null
            }
        }
        return text.trim().takeIf { it.isNotEmpty() }?.let { Utterance(it, interrupted = false) }
    }

    private suspend fun transcribeOrReport(wav: ByteArray): String? = try {
        audio.transcribe(wav)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "[Voice] transcription failed")
        _state.update { it.copy(error = e.message ?: "Transcription failed") }
        delay(ERROR_BACKOFF_MS)
        null
    }

    /**
     * Sends [utterance], speaks the reply, and watches the mic through the whole turn.
     * Returns the interruption to send next, or null to go back to listening.
     */
    private suspend fun runTurn(utterance: Utterance, serverStt: Boolean): Utterance? {
        if (!host.submit(utterance.text, utterance.interrupted)) {
            _state.update { it.copy(problem = VoiceProblem.NotSent) }
            delay(ERROR_BACKOFF_MS)
            return null
        }
        _state.update { it.copy(status = VoiceStatus.Thinking) }

        return coroutineScope {
            val barged = CompletableDeferred<Unit>()
            val spoken = SpokenSoFar()
            // Written by the mic thread before [barged] completes, read here after it.
            var echoSource = ""
            var interruptJob: Job? = null
            var capture: ByteArray? = null
            val speakJob = launch { speakReply(spoken) }
            val bargeJob = launch {
                if (_state.value.muted) return@launch
                // Mute turns the mic off here too: no barge-in while muted.
                // A failure is caught inside: a failed async child would take the whole turn down.
                val monitor = async {
                    try {
                        mic.monitorBarge(
                            isPlaying = { anyPlaying },
                            onSpeech = {
                                // On the mic thread: only what is safe from any thread.
                                // Snapshot first: what was audible is what an echo would match.
                                echoSource = if (anyPlaying) spoken.text else ""
                                if (host.isBusy()) interruptJob = scope.launch { host.interrupt() }
                                barged.complete(Unit)
                                speakJob.cancel()
                                player.stop()
                                localSpeech?.stop()
                                _state.update { it.copy(status = VoiceStatus.Listening, speaking = "") }
                            },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // No barge-in this turn; the reply still plays.
                        Timber.w(e, "[Voice] barge-in monitor failed")
                        null
                    }
                }
                val muteWatch = launch {
                    unmuted.first { !it }
                    monitor.cancel()
                }
                try {
                    capture = monitor.await()
                } catch (e: CancellationException) {
                    if (!isActive) throw e // muted: no capture
                } finally {
                    muteWatch.cancel()
                }
            }
            // Whichever comes first: the user talks over it, or the reply is spoken to the end.
            val bargedIn = select<Boolean> {
                barged.onAwait { true }
                speakJob.onJoin { barged.isCompleted }
            }
            if (!bargedIn || !serverStt) {
                // Spoken to the end; or the phone's recognizer, which can't take the clip,
                // listens afresh right away.
                bargeJob.cancelAndJoin()
                interruptJob?.let { withTimeoutOrNull(INTERRUPT_SETTLE_TIMEOUT_MS) { it.join() } }
                return@coroutineScope null
            }
            bargeJob.join()
            val wav = capture ?: return@coroutineScope null
            _state.update { it.copy(status = VoiceStatus.Transcribing) }
            val text = transcribeOrReport(wav)?.trim().orEmpty()
            if (text.isEmpty()) return@coroutineScope null
            // Hermes hearing itself through the speaker: treat it as silence.
            if (echoSource.isNotEmpty() && isTtsEcho(text, echoSource)) return@coroutineScope null
            // The server hears about the interruption before the next turn goes.
            interruptJob?.let { withTimeoutOrNull(INTERRUPT_SETTLE_TIMEOUT_MS) { it.join() } }
            Utterance(text, interrupted = true)
        }
    }

    /** The sentences handed to the voice so far; read from the mic thread on a barge-in. */
    private class SpokenSoFar {
        @Volatile var text: String = ""
            private set

        fun add(sentence: String) {
            text = if (text.isEmpty()) sentence else "$text $sentence"
        }
    }

    /**
     * Speaks the reply while it streams: complete sentences go out as they form, and the next
     * sentence's audio is fetched while the current one plays. Each bubble is read from where it
     * was left, so a bubble that changes once sealed doesn't shift the others.
     */
    private suspend fun speakReply(spoken: SpokenSoFar) = coroutineScope {
        val sentences = Channel<String>(Channel.UNLIMITED)
        launch {
            val buffer = IncrementalSpeechSentenceBuffer()
            val consumed = HashMap<String, Int>()
            suspend fun feed() {
                for (part in host.replyParts()) {
                    val text = stripReasoningForSpeech(part.text)
                    val done = consumed[part.id]
                    if (done == null) {
                        // A new bubble starts a new paragraph.
                        if (consumed.isNotEmpty()) buffer.append("\n\n").forEach { sentences.send(it) }
                        consumed[part.id] = 0
                    }
                    val from = (consumed[part.id] ?: 0).coerceAtMost(text.length)
                    if (text.length > from) {
                        buffer.append(text.substring(from)).forEach { sentences.send(it) }
                    }
                    consumed[part.id] = maxOf(from, text.length)
                }
            }
            while (true) {
                val busy = host.isBusy()
                feed()
                if (!busy) {
                    // Read once more after the turn ended: the last text lands with it.
                    feed()
                    buffer.flush().forEach { sentences.send(it) }
                    sentences.close()
                    break
                }
                delay(POLL_MS)
            }
        }
        val clips = Channel<Pair<String, Deferred<VoiceAudio.SpokenAudio?>>>(capacity = 1)
        launch {
            for (sentence in sentences) clips.send(sentence to async { fetchServerVoice(sentence) })
            clips.close()
        }
        for ((sentence, clip) in clips) {
            _state.update { it.copy(status = VoiceStatus.Speaking, speaking = sentence) }
            spoken.add(sentence)
            val audioClip = clip.await()
            try {
                if (audioClip != null) {
                    player.play(audioClip.bytes)
                } else {
                    val local = localSpeech ?: LocalSpeech(appContext).also { localSpeech = it }
                    local.speak(sentence)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Voice] playback failed")
                _state.update { it.copy(error = e.message ?: "Playback failed") }
            }
        }
        _state.update { it.copy(speaking = "") }
    }

    /** Hermes' voice for [sentence], or null to use the phone's (and keep using it from then on). */
    private suspend fun fetchServerVoice(sentence: String): VoiceAudio.SpokenAudio? {
        if (preferPhoneVoice || _state.value.phoneVoice) return null
        return try {
            audio.speak(sentence)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Voice] server TTS failed; using the phone's voice")
            _state.update { it.copy(phoneVoice = true) }
            null
        }
    }

    private companion object {
        const val POLL_MS = 150L
        const val ERROR_BACKOFF_MS = 1_500L
        const val INTERRUPT_SETTLE_TIMEOUT_MS = 5_000L
    }
}

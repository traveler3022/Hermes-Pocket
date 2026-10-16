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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** What the voice conversation needs from the open chat. */
interface VoiceHost {
    /** An agent turn is running. */
    val busy: StateFlow<Boolean>

    /** The assistant text of the turn the last [submit] started, as far as it has streamed. */
    fun replyText(): String

    /** Sends [text] as a spoken turn; [interrupted] when it cut Hermes off. False when refused. */
    suspend fun submit(text: String, interrupted: Boolean): Boolean

    /** Stops the running turn (the Stop button). */
    fun interrupt()
}

enum class VoiceStatus { Off, Listening, Transcribing, Thinking, Speaking }

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
    private var localSpeech: LocalSpeech? = null
    private var job: Job? = null

    @Volatile private var endTakeNow = false
    private val unmuted = MutableStateFlow(true)

    val active: Boolean get() = job?.isActive == true

    fun start() {
        if (active) return
        _state.value = VoiceUiState(status = VoiceStatus.Listening)
        job = scope.launch {
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
        job = null
        player.stop()
        localSpeech?.stop()
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        _state.update { it.copy(muted = muted) }
        unmuted.value = !muted
        if (muted) endTakeNow = true
    }

    /** "I'm done talking": ends the take now instead of waiting for silence. */
    fun finishTake() {
        if (_state.value.status == VoiceStatus.Listening) endTakeNow = true
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private suspend fun run() {
        val serverStt = runCatching { audio.serverSttAvailable() }
            .onFailure { Timber.w(it, "[Voice] STT probe failed") }
            .getOrDefault(false)
        if (!serverStt && !deviceInput.available) {
            _state.update { it.copy(error = NO_STT) }
            return
        }
        _state.update { it.copy(phoneListening = !serverStt) }

        var next: Utterance? = null
        while (currentCoroutineContext().isActive) {
            if (_state.value.muted) {
                _state.update { it.copy(status = VoiceStatus.Listening) }
                unmuted.first { it }
            }
            val utterance = next ?: listen(serverStt) ?: continue
            next = null
            _state.update { it.copy(heard = utterance.text, error = null) }
            if (isVoiceStopCommand(utterance.text)) return
            if (isVoiceHoldCommand(utterance.text)) continue
            next = runTurn(utterance, serverStt)
        }
    }

    private class Utterance(val text: String, val interrupted: Boolean)

    /** One listening take → its transcript, or null when nothing usable was said. */
    private suspend fun listen(serverStt: Boolean): Utterance? {
        endTakeNow = false
        _state.update { it.copy(status = VoiceStatus.Listening, speaking = "") }
        val text = if (serverStt) {
            val take = mic.listen(endNow = { endTakeNow })
            if (_state.value.muted) return null
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
        if (_state.value.muted) return null
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
            _state.update { it.copy(error = "Hermes did not take the message") }
            delay(ERROR_BACKOFF_MS)
            return null
        }
        _state.update { it.copy(status = VoiceStatus.Thinking) }

        return coroutineScope {
            val barged = CompletableDeferred<Unit>()
            var echoSource = ""
            val spoken = StringBuilder()
            val speakJob = launch { speakReply(spoken) }
            val bargeJob = launch {
                if (_state.value.muted) return@launch
                try {
                    captured = mic.monitorBarge(
                        isPlaying = { player.isPlaying || localSpeech?.isPlaying == true },
                        onSpeech = {
                            // Snapshot first: what was audible is what an echo would match.
                            echoSource = if (player.isPlaying || localSpeech?.isPlaying == true) spoken.toString() else ""
                            barged.complete(Unit)
                            player.stop()
                            localSpeech?.stop()
                            speakJob.cancel()
                            if (host.busy.value) host.interrupt()
                            _state.update { it.copy(status = VoiceStatus.Listening, speaking = "") }
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // No barge-in this turn; the reply still plays.
                    Timber.w(e, "[Voice] barge-in monitor failed")
                }
            }
            // Whichever comes first: the user talks over it, or the reply is spoken to the end.
            val first = kotlinx.coroutines.selects.select<Boolean> {
                barged.onAwait { true }
                speakJob.onJoin { barged.isCompleted }
            }
            if (!first) {
                bargeJob.cancelAndJoin()
                return@coroutineScope null
            }
            if (!serverStt) {
                // The phone's recognizer can't take the clip; it listens afresh right away.
                bargeJob.cancelAndJoin()
                return@coroutineScope null
            }
            bargeJob.join()
            val wav = captured ?: return@coroutineScope null
            captured = null
            _state.update { it.copy(status = VoiceStatus.Transcribing) }
            val text = transcribeOrReport(wav)?.trim().orEmpty()
            if (text.isEmpty()) return@coroutineScope null
            // Hermes hearing itself through the speaker: treat it as silence.
            if (echoSource.isNotEmpty() && isTtsEcho(text, sanitizeTextForSpeech(echoSource))) return@coroutineScope null
            // The interrupted turn has to settle before the next one can go.
            withTimeoutOrNull(INTERRUPT_SETTLE_TIMEOUT_MS) { host.busy.first { !it } }
            Utterance(text, interrupted = true)
        }
    }

    @Volatile private var captured: ByteArray? = null

    /**
     * Speaks the reply while it streams: complete sentences go out as they form, and the next
     * sentence's audio is fetched while the current one plays.
     */
    private suspend fun speakReply(spoken: StringBuilder) = coroutineScope {
        val sentences = Channel<String>(Channel.UNLIMITED)
        launch {
            val buffer = IncrementalSpeechSentenceBuffer()
            var consumed = 0
            while (true) {
                val text = host.replyText()
                if (text.length < consumed) consumed = text.length
                if (text.length > consumed) {
                    buffer.append(text.substring(consumed)).forEach { sentences.send(it) }
                    consumed = text.length
                }
                if (!host.busy.value) {
                    // One more read: the last delta may have landed with the turn's end.
                    val last = host.replyText()
                    if (last.length > consumed) buffer.append(last.substring(consumed)).forEach { sentences.send(it) }
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
            spoken.append(sentence).append(' ')
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
        if (_state.value.phoneVoice) return null
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
        const val NO_STT = "No speech-to-text: set one on the server (hermes tools → Speech-to-Text), " +
            "or install a speech recognizer on the phone."
    }
}

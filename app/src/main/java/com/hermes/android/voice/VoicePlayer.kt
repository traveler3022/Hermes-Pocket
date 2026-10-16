package com.hermes.android.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Speech is played like a voice call, so the mic's echo canceller knows what to take out. */
private val SPEECH_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/**
 * Plays Hermes' spoken sentences one at a time. [play] suspends until the clip ends; [stop] cuts it
 * (barge-in) and makes the waiting [play] return.
 */
class VoicePlayer {
    @Volatile private var stopCurrent: (() -> Unit)? = null

    /** True while audio is coming out of the speaker (drives the barge-in trigger). */
    @Volatile var isPlaying: Boolean = false
        private set

    suspend fun play(bytes: ByteArray): Unit = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val player = MediaPlayer()
            val done = java.util.concurrent.atomic.AtomicBoolean(false)
            fun finish(error: Throwable? = null) {
                if (!done.compareAndSet(false, true)) return
                stopCurrent = null
                isPlaying = false
                runCatching { player.release() }
                if (cont.isActive) if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
            }
            stopCurrent = { finish() }
            try {
                player.setAudioAttributes(SPEECH_ATTRIBUTES)
                player.setDataSource(BytesDataSource(bytes))
                player.setOnCompletionListener { finish() }
                player.setOnErrorListener { _, what, extra -> finish(IOException("Playback failed ($what/$extra)")); true }
                player.setOnPreparedListener {
                    if (done.get()) return@setOnPreparedListener
                    isPlaying = true
                    it.start()
                }
                player.prepareAsync()
            } catch (e: Exception) {
                finish(e)
            }
            cont.invokeOnCancellation { finish() }
        }
    }

    /** Cuts the clip that is playing; its [play] returns normally. Safe from any thread. */
    fun stop() {
        stopCurrent?.invoke()
    }

    private class BytesDataSource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val n = minOf(size.toLong(), data.size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, n)
            return n
        }

        override fun getSize(): Long = data.size.toLong()
        override fun close() = Unit
    }
}

/**
 * The phone's own text-to-speech: the voice when Hermes has none to give (no TTS provider, or a
 * runtime that can't reach it). Free and on-device.
 */
class LocalSpeech(context: Context) {
    private val ready = kotlinx.coroutines.CompletableDeferred<Boolean>()
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }

    @Volatile var isPlaying: Boolean = false
        private set

    suspend fun speak(text: String) {
        if (!ready.await()) throw IOException("This phone has no text-to-speech engine")
        tts.setAudioAttributes(SPEECH_ATTRIBUTES)
        suspendCancellableCoroutine<Unit> { cont ->
            val id = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == id) isPlaying = true
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != id) return
                    isPlaying = false
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId != id) return
                    isPlaying = false
                    if (cont.isActive) cont.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId != id) return
                    isPlaying = false
                    if (cont.isActive) cont.resumeWithException(IOException("Text-to-speech failed"))
                }
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            cont.invokeOnCancellation { stop() }
        }
    }

    fun stop() {
        isPlaying = false
        runCatching { tts.stop() }
    }

    fun shutdown() {
        stop()
        runCatching { tts.shutdown() }
    }
}

/**
 * The phone's audio as a hands-free call while the conversation runs: communication mode turns
 * on the echo canceller's reference path, and the loudspeaker is chosen unless a headset is in.
 * [release] puts things back as they were.
 */
class VoiceAudioRoute(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false
    private var held = false

    fun acquire() {
        if (held) return
        held = true
        previousMode = audio.mode
        runCatching {
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audio.availableCommunicationDevices
                val headset = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                val target = headset ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (target != null) audio.setCommunicationDevice(target)
            } else {
                @Suppress("DEPRECATION")
                previousSpeaker = audio.isSpeakerphoneOn
                @Suppress("DEPRECATION")
                if (!audio.isWiredHeadsetOn && !audio.isBluetoothScoOn) audio.isSpeakerphoneOn = true
            }
        }.onFailure { Timber.w(it, "[Voice] audio route") }
    }

    fun release() {
        if (!held) return
        held = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audio.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audio.isSpeakerphoneOn = previousSpeaker
            }
            audio.mode = previousMode
        }.onFailure { Timber.w(it, "[Voice] audio route release") }
    }
}

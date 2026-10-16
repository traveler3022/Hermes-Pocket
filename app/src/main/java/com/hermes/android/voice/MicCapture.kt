package com.hermes.android.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The microphone for the voice conversation: one [AudioRecord] at a time, opened as a voice call
 * (VOICE_COMMUNICATION, with the device's echo canceller and noise suppressor when it has them) so
 * Hermes' own voice is taken out of what the mic hears.
 *
 * Its level (0..1, for the screen) goes to the flow it is given. Levels are on the desktop's scale (byte-domain RMS / 42; int16 RMS / 10752), so its tuned
 * thresholds carry over unchanged.
 */
class MicCapture(private val _level: MutableStateFlow<Float>) {

    /** Result of one listening turn. */
    class Take(val wav: ByteArray, val heardSpeech: Boolean, val durationMs: Long)

    /**
     * Records until the speaker goes quiet: [silenceMs] of silence after speech ends the take,
     * [idleSilenceMs] with no speech at all ends it empty, [maxMs] caps it (the desktop's 60 s
     * turn timeout). Mirrors use-mic-recorder's endpointing.
     */
    suspend fun listen(
        silenceLevel: Float = SILENCE_LEVEL,
        silenceMs: Long = SILENCE_MS,
        idleSilenceMs: Long = 12_000,
        maxMs: Long = 60_000,
        /** Tapped "done": end the take now. */
        endNow: () -> Boolean = { false },
    ): Take = withRecorder { record, frame ->
        val pcm = ByteArrayOutputStream()
        val started = now()
        var heard = false
        var silenceStart = -1L
        while (true) {
            coroutineContext.ensureActive()
            val n = record.read(frame, 0, frame.size)
            if (n < 0) throw IOException("Microphone read failed ($n)")
            val level = levelOf(frame, n)
            _level.value = level
            pcm.writeShorts(frame, n)
            val t = now()
            if (level >= silenceLevel) {
                heard = true
                silenceStart = -1
            } else if (heard) {
                if (silenceStart < 0) silenceStart = t
                if (t - silenceStart >= silenceMs) break
            } else if (t - started >= idleSilenceMs) {
                break
            }
            if (t - started >= maxMs || endNow()) break
        }
        Take(wav(pcm.toByteArray()), heard, now() - started)
    }

    /**
     * Full-duplex barge-in monitor (Hermes Desktop's voice-barge-in.ts): watches the mic while
     * Hermes thinks AND while it speaks, and fires [onSpeech] the moment the user talks over it.
     * Then it keeps recording until they go quiet and returns the whole interruption, its first
     * syllable included (pre-roll). Cancelled before anyone spoke, it never returns.
     *
     * The quiet floor is calibrated from quiet samples only and held through playback; while
     * audio plays the trigger is clamped up so the speaker's bleed alone can't trip it, but
     * capped so speech still can; a short grace follows playback onset; and the trip needs a
     * sustained majority of loud frames, so one dip inside a word doesn't reset it.
     */
    suspend fun monitorBarge(
        isPlaying: () -> Boolean,
        onSpeech: () -> Unit,
        thresholdMultiplier: Double? = null,
        silenceMs: Long = SILENCE_MS,
    ): ByteArray = withRecorder { record, frame ->
        val scale = thresholdMultiplier?.takeIf { it > 0 }?.let { it / DEFAULT_BARGE_MULTIPLIER } ?: 1.0
        val floorMultiplier = FLOOR_MULTIPLIER * scale
        val playbackMinTrigger = PLAYBACK_MIN_TRIGGER * scale

        val preRoll = ArrayDeque<ShortArray>()
        val capture = ByteArrayOutputStream()
        val floorSamples = ArrayDeque<Float>()
        val recent = ArrayDeque<Pair<Boolean, Long>>()
        var calibratedSince = -1L
        var floorLocked = false
        var quietFloor = 0f
        var wasPlaying = false
        var playbackSeen = false
        var lastPlayingAt = 0L
        var graceUntil = 0L
        var tripped = false
        var trippedAt = 0L
        var quietSince = -1L

        fun pushFloor(level: Float) {
            floorSamples.addLast(level)
            if (floorSamples.size > FLOOR_SAMPLE_CAP) floorSamples.removeFirst()
            quietFloor = floorSamples.sorted()[floorSamples.size shr 1]
        }

        while (true) {
            coroutineContext.ensureActive()
            val n = record.read(frame, 0, frame.size)
            if (n < 0) throw IOException("Microphone read failed ($n)")
            val level = levelOf(frame, n)
            _level.value = level
            val t = now()
            if (!tripped) {
                preRoll.addLast(frame.copyOf(n))
                while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()

                val playing = isPlaying()
                if (!floorLocked) {
                    if (!playing) {
                        if (calibratedSince < 0) calibratedSince = t
                        pushFloor(level)
                    }
                    if (playing || (calibratedSince >= 0 && t - calibratedSince >= CALIBRATION_MS)) floorLocked = true
                }
                if (playing && !wasPlaying) {
                    if (!playbackSeen || t - lastPlayingAt >= PLAYBACK_GAP_FOR_GRACE_MS) graceUntil = t + PLAYBACK_GRACE_MS
                    playbackSeen = true
                }
                wasPlaying = playing
                if (playing) lastPlayingAt = t

                var trigger = max(MIN_TRIGGER, (quietFloor * floorMultiplier).toFloat())
                if (playing) trigger = min(max(trigger, playbackMinTrigger.toFloat()), TRIGGER_CEILING)
                if (floorLocked && !playing && level < trigger) pushFloor(level)

                val above = floorLocked && level >= trigger && t >= graceUntil
                recent.addLast(above to t)
                while (recent.isNotEmpty() && t - recent.first().second > SUSTAINED_MS) recent.removeFirst()
                val aboveCount = recent.count { it.first }
                val span = if (recent.isEmpty()) 0 else t - recent.first().second
                if (above && span >= SUSTAINED_MS * SUSTAINED_MAJORITY && aboveCount >= recent.size * SUSTAINED_MAJORITY) {
                    tripped = true
                    trippedAt = t
                    quietSince = -1
                    preRoll.forEach { capture.writeShorts(it, it.size) }
                    preRoll.clear()
                    onSpeech()
                }
            } else {
                // Playback and generation are cut by now, so plain silence ends the utterance.
                capture.writeShorts(frame, n)
                if (level >= MIN_TRIGGER) quietSince = -1 else if (quietSince < 0) quietSince = t
                if ((quietSince >= 0 && t - quietSince >= silenceMs) || t - trippedAt >= UTTERANCE_MAX_MS) break
            }
        }
        wav(capture.toByteArray())
    }

    @SuppressLint("MissingPermission") // the screen asks for RECORD_AUDIO before starting
    private suspend fun <T> withRecorder(block: suspend (AudioRecord, ShortArray) -> T): T = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuffer, FRAME_SAMPLES * 2 * 8),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IOException("The microphone is not available")
        }
        val effects = listOfNotNull(
            runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId) else null }.getOrNull(),
            runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId) else null }.getOrNull(),
        )
        effects.forEach { runCatching { it.enabled = true } }
        try {
            record.startRecording()
            block(record, ShortArray(FRAME_SAMPLES))
        } finally {
            _level.value = 0f
            runCatching { record.stop() }.onFailure { Timber.w(it, "[Voice] mic stop") }
            record.release()
            effects.forEach { runCatching { it.release() } }
        }
    }

    private fun levelOf(frame: ShortArray, n: Int): Float {
        if (n <= 0) return 0f
        var sum = 0.0
        for (i in 0 until n) {
            val v = frame[i].toDouble()
            sum += v * v
        }
        return min(1.0, sqrt(sum / n) / INT16_LEVEL_DIVISOR).toFloat()
    }

    private fun ByteArrayOutputStream.writeShorts(frame: ShortArray, n: Int) {
        val buf = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) buf.putShort(frame[i])
        write(buf.array())
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val FRAME_SAMPLES = 320 // 20 ms

        /** The desktop's byte-domain /42 on int16 samples: 32768 / 128 * 42. */
        private const val INT16_LEVEL_DIVISOR = 10_752.0

        const val SILENCE_LEVEL = 0.075f
        const val SILENCE_MS = 1_250L

        private const val CALIBRATION_MS = 400L
        private const val SUSTAINED_MS = 300L
        private const val SUSTAINED_MAJORITY = 0.8
        private const val MIN_TRIGGER = 0.075f
        private const val FLOOR_MULTIPLIER = 3.5
        private const val PLAYBACK_MIN_TRIGGER = 0.14
        private const val TRIGGER_CEILING = 0.37f
        private const val DEFAULT_BARGE_MULTIPLIER = 3.0
        private const val PLAYBACK_GRACE_MS = 500L
        private const val PLAYBACK_GAP_FOR_GRACE_MS = 1_000L
        private const val FLOOR_SAMPLE_CAP = 200
        private const val UTTERANCE_MAX_MS = 30_000L

        /** 1.5 s kept before the trip so the first syllable is in the capture. */
        private const val PRE_ROLL_FRAMES = 75

        /** PCM16 mono [pcm] as a WAV file. */
        fun wav(pcm: ByteArray): ByteArray {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(36 + pcm.size)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(SAMPLE_RATE)
                putInt(SAMPLE_RATE * 2)
                putShort(2)
                putShort(16)
                put("data".toByteArray())
                putInt(pcm.size)
            }
            return header.array() + pcm
        }
    }
}

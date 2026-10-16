package com.hermes.android.domain.voice

/**
 * Hermes' own speech-to-text and text-to-speech for the voice conversation: the providers the
 * profile set under `stt.` and `tts.` in config.yaml, through the dashboard's `/api/audio/` routes
 * (the desktop's relay path). The built-in Linux answers the same routes in-process.
 */
interface VoiceAudio {

    /** Whether the server has a speech-to-text provider (`voice.toggle` status). */
    suspend fun serverSttAvailable(): Boolean

    /** The transcript of a WAV recording; "" when nothing was said. Throws when it failed. */
    suspend fun transcribe(wav: ByteArray): String

    /** [text] as audio in the profile's voice. Throws when the server could not speak it. */
    suspend fun speak(text: String): SpokenAudio

    class SpokenAudio(val bytes: ByteArray, val mimeType: String)
}

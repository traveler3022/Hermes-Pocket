package com.hermes.android.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Listening through the phone's own speech recognizer, for a server with no speech-to-text
 * provider. It has its own endpointing; "" means nothing was said. It can't transcribe a clip,
 * so a barge-in on this path only stops Hermes and then listens afresh.
 *
 * The recognizer is named explicitly, never Hermes' own: while Hermes is the phone's assistant
 * the system may make HermesRecognitionService (which only answers with an error) the default.
 * Seeing the others needs the RecognitionService `<queries>` entry in the manifest.
 */
class DeviceSpeechInput(private val context: Context, private val level: MutableStateFlow<Float>) {

    /** The phone's speech recognizer: the user's default unless that is Hermes' own, else any other. */
    private fun recognizer(): ComponentName? {
        val own = context.packageName
        val default = Settings.Secure.getString(context.contentResolver, VOICE_RECOGNITION_SERVICE)
            ?.let(ComponentName::unflattenFromString)
            ?.takeIf { it.packageName != own }
        if (default != null) return default
        return context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
            .firstOrNull { it.packageName != own }
    }

    val available: Boolean get() = recognizer() != null

    /** The recognizer of the take under way; touched on the main thread only. */
    private var active: SpeechRecognizer? = null

    /** Ends the take under way now; what was said so far still comes back from [listenOnce]. */
    fun finish() {
        active?.stopListening()
    }

    suspend fun listenOnce(): String = withContext(Dispatchers.Main) {
        val component = recognizer() ?: throw IOException("This phone has no speech recognizer")
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context, component)
        active = recognizer
        try {
            suspendCancellableCoroutine<String> { cont ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        level.value = 0f
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (cont.isActive) cont.resume(text.trim())
                    }

                    override fun onError(error: Int) {
                        level.value = 0f
                        if (!cont.isActive) return
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> cont.resume("")
                            else -> cont.resumeWithException(IOException("Speech recognition failed ($error)"))
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // About -2..10 dB from most recognizers.
                        level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    }

                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                recognizer.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
                )
                // Cancellation lands in the finally below, on the main thread the recognizer needs.
            }
        } finally {
            if (active === recognizer) active = null
            level.value = 0f
            recognizer.destroy()
        }
    }

    private companion object {
        /** Settings.Secure.VOICE_RECOGNITION_SERVICE, hidden from the SDK. */
        const val VOICE_RECOGNITION_SERVICE = "voice_recognition_service"
    }
}

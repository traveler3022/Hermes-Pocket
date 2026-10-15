package com.hermes.android.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * The system takes an assistant only when it names a recognition service
 * (voice_interaction_service.xml); Hermes recognizes no speech itself — the chat uses
 * the phone's own recognizer through RecognizerIntent. Up to Android 11 the phone may
 * make this the default recognizer while Hermes is the assistant, so a caller gets an
 * error at once instead of waiting forever.
 */
class HermesRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onStopListening(listener: Callback?) = Unit

    override fun onCancel(listener: Callback?) = Unit
}

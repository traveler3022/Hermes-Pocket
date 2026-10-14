package com.hermes.android.assistant

import android.service.voice.VoiceInteractionService

/**
 * Makes Hermes a choice for the phone's digital assistant (Settings → Default apps →
 * Digital assistant app). The system keeps it bound while Hermes holds that role;
 * each invocation is handled by [HermesAssistantSessionService].
 */
class HermesAssistantService : VoiceInteractionService()

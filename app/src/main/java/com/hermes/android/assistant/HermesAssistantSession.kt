package com.hermes.android.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import timber.log.Timber

/**
 * Has no UI of its own: every invocation opens the app into a new chat with voice
 * input started, then ends. onShow comes with every invocation and onHandleAssist
 * with the screen's assist data; both go through one guarded launch so the app
 * opens once. The screen's content is never read.
 *
 * Adapted from Hy4ri/hermes-mobile (Apache-2.0).
 */
class HermesAssistantSession(context: Context) : VoiceInteractionSession(context) {
    private var launched = false

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        openHermesAndFinish()
    }

    // The 3-argument form is what the default onHandleAssist(AssistState) forwards to.
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        openHermesAndFinish()
    }

    private fun openHermesAndFinish() {
        if (launched) return
        launched = true
        // The shown session may start an activity: the system lets the
        // current assistant do so from the background.
        runCatching { context.startActivity(AssistantLaunch.intent(context)) }
            .onFailure { Timber.w(it, "[Assistant] could not open the app") }
        finish()
    }
}

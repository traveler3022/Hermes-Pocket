package com.hermes.android.assistant

import android.content.Context
import android.content.Intent
import com.hermes.android.MainActivity

/**
 * How the phone's assistant gesture (long-press Home, corner swipe) reaches the app:
 * the session below, or a plain ACTION_ASSIST on phones that send that instead, opens
 * MainActivity into a new chat with voice input started.
 */
object AssistantLaunch {
    const val ACTION = "com.hermes.android.action.ASSISTANT"

    fun isAssistIntent(intent: Intent?): Boolean =
        intent?.action == ACTION || intent?.action == Intent.ACTION_ASSIST

    /** CLEAR_TOP | SINGLE_TOP: a running app takes it in onNewIntent instead of a second copy. */
    fun intent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
}

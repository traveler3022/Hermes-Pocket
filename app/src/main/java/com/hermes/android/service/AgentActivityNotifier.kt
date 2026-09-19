package com.hermes.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hermes.android.MainActivity
import com.hermes.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.hermes.android.ui.i18n.tForContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proactive agent-activity notifications (delegation v1).
 *
 * When a turn or background task finishes while no Activity is visible, the
 * user gets a tappable notification carrying a preview of the result. Tapping
 * opens MainActivity with [EXTRA_SESSION_ID] so the nav host can resume the
 * session the result belongs to.
 *
 * Mirrors [ApprovalNotificationManager]'s structure (channel-per-concern,
 * notification id derived from the stable key) so the service layer stays
 * consistent.
 */
@Singleton
class AgentActivityNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    init {
        createNotificationChannel()
    }

    /** A live chat turn finished while the app was backgrounded. */
    fun showTurnComplete(sessionId: String?, preview: String) {
        show(
            key = sessionId ?: "turn",
            title = tForContext(context, "Hermes finished a reply", "هرمس پاسخ را تمام کرد"),
            preview = preview,
            sessionId = sessionId,
        )
    }

    /** Fallback body when a finished session has no preview to show. */
    fun taskFinishedText(): String = tForContext(context, "Task finished", "کار تمام شد")

    /** A prompt.background task finished (result is ephemeral — show it). */
    fun showBackgroundTaskComplete(taskId: String, sessionId: String?, preview: String) {
        show(
            key = "bg_$taskId",
            title = tForContext(context, "Background task finished", "کار پس‌زمینه تمام شد"),
            preview = preview,
            sessionId = sessionId,
        )
    }

    private fun show(key: String, title: String, preview: String, sessionId: String?) {
        val text = preview.trim().take(400).ifEmpty { return }
        Timber.i("[AgentNotify] $title (key=$key, session=$sessionId)")

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!sessionId.isNullOrBlank()) putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            key.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(key.hashCode(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                tForContext(context, "Agent Activity", "فعالیت ایجنت"),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = tForContext(
                    context,
                    "Results of tasks and replies that finish while the app is in the background",
                    "نتیجهٔ کارها و پاسخ‌هایی که وقتی اپ در پس‌زمینه است تمام می‌شوند",
                )
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "hermes_agent_activity"

        /** Intent extra: session to resume when the notification is tapped. */
        const val EXTRA_SESSION_ID = "hermes.notification.session_id"
    }
}

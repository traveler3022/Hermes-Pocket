package com.hermes.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hermes.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.hermes.android.i18n.tForContext
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
    fun showTurnComplete(sessionId: String?, preview: String, sessionTitle: String? = null) {
        show(
            replyable = true,
            key = sessionId ?: "turn",
            title = sessionTitle?.takeIf { it.isNotBlank() }
                ?: tForContext(context, "Hermes replied", "پاسخ Hermes آماده است"),
            status = tForContext(context, "Done", "تمام شد"),
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
            status = tForContext(context, "Background", "پس‌زمینه"),
            preview = preview,
            sessionId = sessionId,
        )
    }

    private fun show(
        key: String,
        title: String,
        status: String,
        preview: String,
        sessionId: String?,
        replyable: Boolean = false,
    ) {
        val text = plainText(preview).take(600).ifEmpty { return }
        Timber.i("[AgentNotify] $title (key=$key, session=$sessionId)")
        val id = key.hashCode()

        val notification = HermesNotifications.builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first().take(140))
            .setSubText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(HermesNotifications.openApp(context, sessionId))
            .setGroup(GROUP_RESULTS)
            .setShowWhen(true)
            .setAutoCancel(true)
            .apply {
                // Answer the chat from the shade, as in a messenger.
                if (replyable && !sessionId.isNullOrBlank()) {
                    addAction(NotificationReplyReceiver.replyAction(context, sessionId, id, title))
                }
            }
            .build()

        manager().notify(id, notification)
    }

    /** A reply typed into notification [id] is on its way; shows the text so it is not lost from view. */
    fun showReplySending(id: Int, sessionId: String, title: String, text: String) {
        val notification = HermesNotifications.builder(context, CHANNEL_ID)
            .setContentTitle(title.ifBlank { "Hermes" })
            .setContentText(text.lineSequence().first().take(140))
            .setSubText(tForContext(context, "Sending…", "در حال ارسال…"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(HermesNotifications.openApp(context, sessionId))
            .setGroup(GROUP_RESULTS)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .build()
        manager().notify(id, notification)
    }

    /**
     * A reply from notification [id] did not reach Hermes: keeps [text] on screen with
     * Retry (the same text) and Reply (a new one), and a tap that opens the chat.
     */
    fun showReplyFailed(id: Int, sessionId: String, title: String, text: String, reason: String) {
        val heading = tForContext(context, "Reply not sent", "پاسخ ارسال نشد")
        val notification = HermesNotifications.builder(context, CHANNEL_ID)
            .setContentTitle(if (title.isBlank()) heading else "$heading · $title")
            .setContentText(text.lineSequence().first().take(140))
            .setSubText(reason.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n\n$reason"))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(HermesNotifications.openApp(context, sessionId))
            .setGroup(GROUP_RESULTS)
            .setShowWhen(true)
            .addAction(NotificationReplyReceiver.retryAction(context, sessionId, id, title, text))
            .addAction(NotificationReplyReceiver.replyAction(context, sessionId, id, title))
            .build()
        manager().notify(id, notification)
    }

    fun cancel(id: Int) = manager().cancel(id)

    private fun manager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Replies are Markdown; a notification shows plain text. */
    private fun plainText(markdown: String): String = markdown
        .replace(Regex("```[a-zA-Z0-9]*\\n?"), "")
        .replace(Regex("(?m)^#{1,6}\\s+"), "")
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
        .replace(Regex("\\*\\*|__|`"), "")
        .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                tForContext(context, "Replies & results", "پاسخ‌ها و نتیجه‌ها"),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = tForContext(
                    context,
                    "Results of tasks and replies that finish while the app is in the background",
                    "نتیجهٔ کارها و پاسخ‌هایی که وقتی اپ در پس‌زمینه است تمام می‌شوند",
                )
            }
            HermesNotifications.ensureChannel(context, channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "hermes_agent_activity"
        private const val GROUP_RESULTS = "hermes_results"

        /** Intent extra: session to resume when the notification is tapped. */
        const val EXTRA_SESSION_ID = "hermes.notification.session_id"
    }
}

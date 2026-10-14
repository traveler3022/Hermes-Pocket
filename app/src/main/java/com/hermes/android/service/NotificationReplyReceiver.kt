package com.hermes.android.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.hermes.android.domain.session.ChatSessions
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.i18n.tForContext
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * "Reply" on a "Hermes replied" notification: sends the typed text as the next turn of that
 * chat without opening the app (adapted from Hy4ri/hermes-mobile (Apache-2.0)).
 *
 * The app may be closed, the connection down, or (built-in Linux) Hermes itself not running:
 * the gateway service is started as when the app opens, and the reply waits for it. The chat
 * is attached through [ChatSessions] first, since its live session may be gone. The text is
 * never dropped: a failed send leaves a notification holding it, with Retry.
 */
@AndroidEntryPoint
class NotificationReplyReceiver : BroadcastReceiver() {

    @Inject
    lateinit var chatSessions: ChatSessions

    @Inject
    lateinit var gatewayClient: GatewayClient

    @Inject
    lateinit var notifier: AgentActivityNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() } ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, sessionId.hashCode())
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = when (intent.action) {
            ACTION_RETRY -> intent.getStringExtra(EXTRA_TEXT)
            else -> RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT_REPLY)?.toString()
        }?.trim()
        if (text.isNullOrEmpty()) return

        Timber.i("[NotifyReply] reply for session=$sessionId (${text.length} chars)")
        // Also ends the system's spinner on the inline reply field.
        notifier.showReplySending(notificationId, sessionId, title, text)

        // Keeps the process alive until the reply is sent: the app may be idle or closed.
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(REPLY_DEADLINE_MS) {
                    awaitGateway(context)
                    chatSessions.replyFromOutside(sessionId, text)
                }
                // The turn's own "replied" notification comes when it finishes.
                notifier.cancel(notificationId)
            } catch (e: Exception) {
                Timber.w(e, "[NotifyReply] reply to $sessionId failed")
                val reason = if (e is TimeoutCancellationException) {
                    tForContext(context, "Hermes did not answer in time", "Hermes به‌موقع پاسخ نداد")
                } else {
                    e.message ?: tForContext(context, "Unknown error", "خطای نامشخص")
                }
                notifier.showReplyFailed(notificationId, sessionId, title, text, reason)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Brings the connection up the way opening the app does: the gateway service starts the
     * runtime (the built-in Linux one boots Hermes in this process) and connects. A tap on a
     * notification lets a background app start it. When the start is refused, the request's
     * own dial-on-demand still tries the last address.
     */
    private suspend fun awaitGateway(context: Context) {
        if (gatewayClient.connectionState.value is ConnectionState.Connected) return
        val started = runCatching { HermesGatewayService.start(context) }
            .onFailure { Timber.w(it, "[NotifyReply] could not start the gateway service") }
            .isSuccess
        if (!started) return
        withTimeoutOrNull(CONNECT_WAIT_MS) {
            gatewayClient.connectionState.first { it is ConnectionState.Connected }
        } ?: Timber.w("[NotifyReply] gateway not connected after ${CONNECT_WAIT_MS}ms; trying anyway")
    }

    companion object {
        private const val ACTION_REPLY = "com.hermes.android.NOTIFICATION_REPLY"
        private const val ACTION_RETRY = "com.hermes.android.NOTIFICATION_REPLY_RETRY"
        private const val KEY_TEXT_REPLY = "hermes.reply.text"
        private const val EXTRA_SESSION_ID = "hermes.reply.session_id"
        private const val EXTRA_NOTIFICATION_ID = "hermes.reply.notification_id"
        private const val EXTRA_TITLE = "hermes.reply.title"
        private const val EXTRA_TEXT = "hermes.reply.retry_text"

        /** Within the ~60 s Android gives a background broadcast; a cold built-in Linux may need most of it. */
        private const val REPLY_DEADLINE_MS = 50_000L
        private const val CONNECT_WAIT_MS = 35_000L

        /** The inline "Reply" action for the notification [notificationId] of [sessionId]. */
        fun replyAction(context: Context, sessionId: String, notificationId: Int, title: String): NotificationCompat.Action {
            val label = tForContext(context, "Reply", "پاسخ")
            val intent = baseIntent(context, ACTION_REPLY, sessionId, notificationId, title)
            // RemoteInput fills the text into the intent, so it must be mutable (API 31+).
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pendingIntent = PendingIntent.getBroadcast(context, notificationId, intent, flags)
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(tForContext(context, "Message Hermes", "پیام به Hermes"))
                .build()
            return NotificationCompat.Action.Builder(0, label, pendingIntent)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()
        }

        /** Sends [text] again, unchanged: on the notification of a reply that failed. */
        fun retryAction(context: Context, sessionId: String, notificationId: Int, title: String, text: String): NotificationCompat.Action {
            val intent = baseIntent(context, ACTION_RETRY, sessionId, notificationId, title)
                .putExtra(EXTRA_TEXT, text)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Action.Builder(0, tForContext(context, "Retry", "تلاش دوباره"), pendingIntent)
                .setShowsUserInterface(false)
                .build()
        }

        private fun baseIntent(context: Context, action: String, sessionId: String, notificationId: Int, title: String): Intent =
            Intent(context, NotificationReplyReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_TITLE, title)
    }
}

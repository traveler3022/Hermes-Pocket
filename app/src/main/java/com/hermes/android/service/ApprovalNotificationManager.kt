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
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages tool approval notifications (ADR-003).
 *
 * When the agent wants to execute a tool that requires user approval
 * (e.g. terminal_tool with a risky command), a high-priority notification
 * is shown with Approve / Deny action buttons.
 *
 * The user's response is sent back to the gateway via a broadcast intent
 * that [ApprovalActionReceiver] picks up.
 *
 * Reference: ADR-003 (Tool Approval via Notifications),
 *            Phase 1.5 Rule 1 (depends only on gateway interface)
 */
@Singleton
class ApprovalNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    init {
        createNotificationChannel()
    }

    /**
     * Show an approval notification for a tool call.
     *
     * @param requestId unique ID for this approval request (from gateway)
     * @param toolName name of the tool (e.g. "terminal")
     * @param command the command/text to approve
     * @param description human-readable description of what the tool does
     */
    fun showApprovalRequest(
        requestId: String,
        sessionId: String?,
        toolName: String,
        command: String,
        description: String,
    ) {
        Timber.i("[Approval] Showing approval notification for $toolName (request=$requestId, session=$sessionId)")

        val approveIntent = ApprovalActionReceiver.createIntent(
            context = context,
            requestId = requestId,
            sessionId = sessionId,
            approved = true,
        )
        val denyIntent = ApprovalActionReceiver.createIntent(
            context = context,
            requestId = requestId,
            sessionId = sessionId,
            approved = false,
        )

        val approvePendingIntent = PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val denyPendingIntent = PendingIntent.getBroadcast(
            context,
            (requestId + "_deny").hashCode(),
            denyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Tool approval required: $toolName")
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$description\n\nCommand:\n$command")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(android.R.drawable.checkbox_on_background, "Approve", approvePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Deny", denyPendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(requestId.hashCode(), notification)
    }

    /**
     * Cancel an approval notification (e.g. after user responds or timeout).
     */
    fun cancelApproval(requestId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(requestId.hashCode())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_approval_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_approval_desc)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "hermes_approval"
    }
}

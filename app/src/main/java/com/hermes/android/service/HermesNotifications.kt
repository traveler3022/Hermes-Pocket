package com.hermes.android.service

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hermes.android.MainActivity
import com.hermes.android.R

/**
 * One look for every Hermes notification: the Hermes glyph, the brand color, one channel
 * group, and a tap that opens the app (on a session when there is one).
 */
object HermesNotifications {
    const val BRAND_COLOR = 0xFF5B4BDB.toInt()
    private const val GROUP_ID = "hermes"

    /** Base builder every notifier starts from. */
    fun builder(context: Context, channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_hermes)
            .setColor(BRAND_COLOR)
            .setContentIntent(openApp(context, sessionId = null))

    /** Opens the app; with [sessionId] the nav host resumes that session. */
    fun openApp(context: Context, sessionId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!sessionId.isNullOrBlank()) putExtra(AgentActivityNotifier.EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getActivity(
            context,
            sessionId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Creates [channel] inside the shared "Hermes" group (idempotent). */
    fun ensureChannel(context: Context, channel: NotificationChannel) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannelGroup(NotificationChannelGroup(GROUP_ID, "Hermes"))
        channel.group = GROUP_ID
        manager.createNotificationChannel(channel)
    }

}

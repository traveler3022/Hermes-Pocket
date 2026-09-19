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
import com.hermes.android.ui.i18n.tForContext

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

    /** Human label for the step an agent is on, from the tool it is running. */
    fun stepLabel(context: Context, toolName: String?): String {
        val name = toolName.orEmpty().lowercase()
        return when {
            name.isEmpty() -> tForContext(context, "Thinking…", "در حال فکر کردن…")
            name.startsWith("terminal") || name == "process" || name.startsWith("shell") ->
                tForContext(context, "Running a command…", "در حال اجرای دستور…")
            name.contains("file") || name == "patch" || name.startsWith("search_files") ->
                tForContext(context, "Working with files…", "در حال کار با فایل‌ها…")
            name.startsWith("web") -> tForContext(context, "Searching the web…", "در حال جستجو در وب…")
            name.startsWith("browser") -> tForContext(context, "Using the browser…", "در حال کار با مرورگر…")
            name.startsWith("delegate") || name.startsWith("subagent") ->
                tForContext(context, "Working with a sub-agent…", "در حال کار با زیرعامل…")
            name.contains("image") || name.contains("vision") ->
                tForContext(context, "Working with images…", "در حال کار با تصویر…")
            name.contains("memory") -> tForContext(context, "Updating memory…", "در حال به‌روزرسانی حافظه…")
            else -> tForContext(context, "Using $toolName…", "در حال استفاده از $toolName…")
        }
    }
}

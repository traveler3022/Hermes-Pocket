package com.hermes.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hermes.android.i18n.tForContext
import timber.log.Timber

/**
 * Keeps the app in the foreground for the length of one server sign-in.
 *
 * The sign-in finishes in the browser, which redirects to a listener on 127.0.0.1 inside this app.
 * By then the app is in the background; Android 12+ freezes cached processes (and newer versions
 * cut their network), so the redirect would never be read. Started right before the browser opens
 * and stopped when the sign-in ends, either way.
 */
class RemoteSignInService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, tForContext(this, "Sign-in", "ورود"), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = HermesNotifications.builder(this, CHANNEL_ID)
            .setContentTitle("Hermes")
            .setContentText(tForContext(this, "Signing in to your server…", "در حال ورود به سرور…"))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            Timber.w(e, "[RemoteSignIn] Could not enter the foreground")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "hermes_remote_signin"
        private const val NOTIFICATION_ID = 4711

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, RemoteSignInService::class.java)) }
                .onFailure { Timber.w(it, "[RemoteSignIn] Could not start") }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteSignInService::class.java))
        }
    }
}

package com.hermes.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hermes.android.MainActivity
import com.hermes.android.R
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.runtime.DetectionResult
import com.hermes.android.runtime.RuntimeState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.hermes.android.ui.i18n.tForContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class HermesGatewayService : Service() {

    @Inject
    lateinit var gatewayClient: GatewayClient

    @Inject
    lateinit var hermesRuntime: com.hermes.android.runtime.HermesRuntime

    @Inject
    lateinit var agentEventObserver: AgentEventObserver

    private val scope = CoroutineScope(SupervisorJob())
    private var connectionWatchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("[GatewayService] onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("[GatewayService] onStartCommand")
        startForeground(NOTIFICATION_ID, buildNotification(tr("Connecting to Hermes gateway…", "در حال اتصال به گیت‌وی هرمس…")))

        // Proactive notifications: watch gateway events for the whole life of
        // the background connection (ChatViewModel's collector dies with the
        // UI; this one doesn't). Idempotent across restarts.
        agentEventObserver.start(scope)

        connectionWatchJob?.cancel()
        connectionWatchJob = scope.launch {
            launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                gatewayClient.connectionState.collect { state ->
                    if (state is ConnectionState.Connected) {
                        // Connected is normal — dismiss notification completely so it doesn't clutter the screen
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        return@collect
                    }
                    val text = when (state) {
                        is ConnectionState.Disconnected -> tr("Disconnected", "قطع شد")
                        is ConnectionState.Connecting -> tr("Connecting…", "در حال اتصال…")
                        is ConnectionState.Connected -> return@collect
                        // Show WHY — an endless "attempt N" with no reason is
                        // undebuggable from the phone.
                        is ConnectionState.Reconnecting ->
                            tr("Reconnecting (attempt ${state.attempt})", "اتصال دوباره (تلاش ${state.attempt})") +
                                (state.lastError?.let { ": $it" } ?: "…")
                        is ConnectionState.Failed -> tr("Connection failed", "اتصال ناموفق بود") + ": ${state.reason}"
                    }
                    updateNotification(text)
                }
            }

            launch {
                try {
                    ensureRuntimeGatewayStarted()
                    gatewayClient.connect(url = hermesRuntime.getWebSocketUrl())
                } catch (e: Exception) {
                    Timber.e(e, "[GatewayService] Failed to start/connect gateway")
                    updateNotification(tr("Gateway unavailable", "گیت‌وی در دسترس نیست") + ": ${e.message ?: tr("unknown error", "خطای نامشخص")}")
                }
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun ensureRuntimeGatewayStarted() {
        when (val state = hermesRuntime.state.value) {
            is RuntimeState.Running -> return
            is RuntimeState.Installed -> {
                updateNotification(tr("Starting Hermes gateway…", "در حال راه‌اندازی گیت‌وی هرمس…"))
                hermesRuntime.startGateway()
                return
            }
            is RuntimeState.NotDetected,
            is RuntimeState.Error -> {
                updateNotification(tr("Detecting Hermes runtime…", "در حال شناسایی محیط اجرای هرمس…"))
                when (val detection = hermesRuntime.detect()) {
                    is DetectionResult.Missing -> {
                        updateNotification(tr("Termux setup required", "نیاز به راه‌اندازی Termux"))
                        throw IllegalStateException(detection.title)
                    }
                    is DetectionResult.Incompatible -> {
                        updateNotification(tr("Runtime incompatible", "محیط اجرا سازگار نیست"))
                        throw IllegalStateException(detection.reason)
                    }
                    is DetectionResult.Available -> Unit
                }
                if (hermesRuntime.state.value is RuntimeState.Installed) {
                    updateNotification(tr("Starting Hermes gateway…", "در حال راه‌اندازی گیت‌وی هرمس…"))
                    hermesRuntime.startGateway()
                    return
                }
                updateNotification(tr("Hermes install required", "نیاز به نصب هرمس"))
                throw IllegalStateException("Hermes is not installed yet")
            }
            is RuntimeState.Detected -> {
                updateNotification(tr("Hermes install required", "نیاز به نصب هرمس"))
                throw IllegalStateException("Hermes is not installed yet")
            }
            RuntimeState.Detecting,
            RuntimeState.Installing -> {
                updateNotification(tr("Runtime is busy…", "محیط اجرا مشغول است…"))
                throw IllegalStateException("Runtime is busy: $state")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("[GatewayService] onDestroy")
        connectionWatchJob?.cancel()
        scope.launch { gatewayClient.disconnect() }
        scope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.i("[GatewayService] onTaskRemoved — stopping service")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Clean up legacy channel to avoid cached LOW importance on existing installs
            try {
                manager.deleteNotificationChannel("hermes_gateway")
            } catch (_: Exception) { }

            val channel = NotificationChannel(
                CHANNEL_ID,
                tr("Gateway Service", "سرویس گیت‌وی"),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = tr("Keeps the Hermes gateway running in the background", "گیت‌وی هرمس را در پس‌زمینه فعال نگه می‌دارد")
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(tr("Hermes Gateway", "گیت‌وی هرمس"))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // Notification text follows the in-app language choice, not only the
    // device locale, so it matches what the user sees inside the app.
    private fun tr(en: String, fa: String): String = tForContext(this, en, fa)

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "hermes_gateway_service"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, HermesGatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HermesGatewayService::class.java))
        }
    }
}

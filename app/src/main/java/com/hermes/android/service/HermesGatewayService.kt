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
import android.os.PowerManager
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
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class HermesGatewayService : Service() {

    @Inject
    lateinit var gatewayClient: GatewayClient

    @Inject
    lateinit var hermesRuntime: com.hermes.android.runtime.HermesRuntime

    private val scope = CoroutineScope(SupervisorJob())
    private var connectionWatchJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("[GatewayService] onCreate")
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("[GatewayService] onStartCommand")
        startForeground(NOTIFICATION_ID, buildNotification("Connecting to Hermes gateway…"))

        connectionWatchJob?.cancel()
        connectionWatchJob = scope.launch {
            launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                gatewayClient.connectionState.collect { state ->
                    val text = when (state) {
                        is ConnectionState.Disconnected -> "Disconnected"
                        is ConnectionState.Connecting -> "Connecting…"
                        is ConnectionState.Connected -> "Gateway running"
                        is ConnectionState.Reconnecting ->
                            "Reconnecting (attempt ${state.attempt})…"
                        is ConnectionState.Failed -> "Connection failed: ${state.reason}"
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
                    updateNotification("Gateway unavailable: ${e.message ?: "unknown error"}")
                }
            }
        }

        return START_STICKY
    }

    private suspend fun ensureRuntimeGatewayStarted() {
        when (val state = hermesRuntime.state.value) {
            is RuntimeState.Running -> return
            is RuntimeState.Installed -> {
                updateNotification("Starting Hermes gateway…")
                hermesRuntime.startGateway()
                return
            }
            is RuntimeState.NotDetected,
            is RuntimeState.Error -> {
                updateNotification("Detecting Hermes runtime…")
                when (val detection = hermesRuntime.detect()) {
                    is DetectionResult.Missing -> {
                        updateNotification("Termux setup required")
                        throw IllegalStateException(detection.title)
                    }
                    is DetectionResult.Incompatible -> {
                        updateNotification("Runtime incompatible")
                        throw IllegalStateException(detection.reason)
                    }
                    is DetectionResult.Available -> Unit
                }
                if (hermesRuntime.state.value is RuntimeState.Installed) {
                    updateNotification("Starting Hermes gateway…")
                    hermesRuntime.startGateway()
                    return
                }
                updateNotification("Hermes install required")
                throw IllegalStateException("Hermes is not installed yet")
            }
            is RuntimeState.Detected -> {
                updateNotification("Hermes install required")
                throw IllegalStateException("Hermes is not installed yet")
            }
            RuntimeState.Detecting,
            RuntimeState.Installing -> {
                updateNotification("Runtime is busy…")
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
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── WakeLock ─────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hermes:gateway").apply {
            acquire()
        }
        Timber.i("[GatewayService] WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
            Timber.i("[GatewayService] WakeLock released")
        }
        wakeLock = null
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_gateway_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_gateway_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            .setContentTitle(getString(R.string.gateway_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "hermes_gateway"
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

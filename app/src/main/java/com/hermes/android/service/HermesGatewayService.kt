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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hermes.android.MainActivity
import com.hermes.android.R
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Foreground service that keeps the Hermes gateway connection alive in the
 * background (ADR-004: Foreground Service + WorkManager + Termux:Boot).
 *
 * Responsibilities:
 * - Maintains the WebSocket connection to tui_gateway
 * - Shows a persistent notification (required by Android for foreground services)
 * - Auto-reconnects if the connection drops (handled by GatewayClient)
 *
 * Does NOT:
 * - Run the Python gateway itself (that's Termux's job during migration)
 * - Implement business logic (this is infrastructure only)
 *
 * Reference: ADR-004 (Background Execution), Phase 1.5 Rule 1 (Strict Layer
 * Dependency — service depends only on GatewayClient interface)
 */
@AndroidEntryPoint
class HermesGatewayService : Service() {

    @Inject
    lateinit var gatewayClient: GatewayClient

    @Inject
    lateinit var hermesRuntime: com.hermes.android.runtime.HermesRuntime

    private val scope = CoroutineScope(SupervisorJob())
    private var connectionWatchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("[GatewayService] onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("[GatewayService] onStartCommand")
        startForeground(NOTIFICATION_ID, buildNotification("Connecting to Hermes gateway…"))

        // Start gateway connection and watch state
        connectionWatchJob?.cancel()
        connectionWatchJob = scope.launch {
            // Watch connection state and update notification
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

            // Ensure the Termux-side dashboard process exists, then connect.
            // The service is a keeper, not just a socket client: after boot or
            // process death there may be no `hermes dashboard` process to dial.
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

        // START_STICKY — Android will restart the service if killed
        // Fix S6F01: Schedule periodic health check via WorkManager (every 15 min)
        scheduleHealthCheck()
        return START_STICKY
    }


    private suspend fun ensureRuntimeGatewayStarted() {
        updateNotification("Connecting to Hermes gateway…")
        val ready = hermesRuntime.ensureGatewayReady()
        if (!ready) {
            updateNotification("Hermes not ready — Termux setup required")
            throw IllegalStateException("ensureGatewayReady() returned false — runtime not ready")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("[GatewayService] onDestroy")
        connectionWatchJob?.cancel()
        scope.launch { gatewayClient.disconnect() }
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        private const val HEALTH_CHECK_WORK_NAME = "hermes_gateway_health_check"

        /**
         * Start the gateway service from an Activity or other component.
         */
        fun start(context: Context) {
            val intent = Intent(context, HermesGatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the gateway service.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, HermesGatewayService::class.java))
            // Cancel health check work
            WorkManager.getInstance(context).cancelUniqueWork(HEALTH_CHECK_WORK_NAME)
        }
    }

    // Fix S6F01/S6F02: Schedule periodic health check
    private fun scheduleHealthCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val healthCheckWork = PeriodicWorkRequestBuilder<GatewayHealthWorker>(
            15, TimeUnit.MINUTES // WorkManager minimum is 15 min
        ).setConstraints(constraints).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            HEALTH_CHECK_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            healthCheckWork,
        )
        Timber.i("[GatewayService] Health check scheduled every 15 min")
    }
}

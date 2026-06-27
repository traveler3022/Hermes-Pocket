package com.hermes.android.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.ConnectionState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * WorkManager worker that periodically checks gateway health.
 *
 * Fix S6F01: Auto-restart if gateway dies (via WorkManager)
 * Fix S6F02: Health check via WebSocket ping every 60s
 *
 * If the gateway is not connected, this worker restarts the foreground service.
 *
 * Reference: ADR-004 (Foreground Service + WorkManager + Termux:Boot)
 */
@HiltWorker
class GatewayHealthWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val gatewayClient: GatewayClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val state = gatewayClient.connectionState.value
            Timber.d("[GatewayHealth] Checking... state=$state")

            when (state) {
                is ConnectionState.Connected -> {
                    // Gateway is connected — try a lightweight request to verify
                    try {
                        gatewayClient.request("session.most_recent", timeoutMs = 5000)
                        Timber.d("[GatewayHealth] Gateway responding")
                    } catch (e: Exception) {
                        Timber.w("[GatewayHealth] Gateway not responding, restarting service")
                        HermesGatewayService.start(applicationContext)
                    }
                }
                is ConnectionState.Disconnected,
                is ConnectionState.Failed -> {
                    Timber.w("[GatewayHealth] Gateway disconnected, restarting service")
                    HermesGatewayService.start(applicationContext)
                }
                else -> {
                    // Connecting or Reconnecting — let it finish
                    Timber.d("[GatewayHealth] Gateway is $state, waiting")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "[GatewayHealth] Health check failed")
            Result.retry()
        }
    }
}

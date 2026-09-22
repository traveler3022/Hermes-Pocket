package com.hermes.android.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import javax.inject.Inject

/** "Stop" on the "Hermes is working" notification: interrupts every running session. */
@AndroidEntryPoint
class AgentStopReceiver : BroadcastReceiver() {

    @Inject
    lateinit var gatewayClient: GatewayClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val sessionIds = intent.getStringArrayExtra(EXTRA_SESSION_IDS).orEmpty()
        val pending = goAsync()
        scope.launch {
            try {
                for (id in sessionIds) {
                    runCatching {
                        gatewayClient.request(GatewayMethods.SESSION_INTERRUPT, mapOf("session_id" to JsonPrimitive(id)))
                    }.onFailure { Timber.w(it, "[AgentStop] interrupt failed for $id") }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_SESSION_IDS = "session_ids"

        fun pendingIntent(context: Context, sessionIds: Collection<String>): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                STOP_REQUEST_CODE,
                Intent(context, AgentStopReceiver::class.java)
                    .putExtra(EXTRA_SESSION_IDS, sessionIds.toTypedArray()),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private const val STOP_REQUEST_CODE = 0x5709
    }
}

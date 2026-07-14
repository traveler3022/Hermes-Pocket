package com.hermes.android.service

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-lifetime gateway event watcher for proactive notifications.
 *
 * ChatViewModel also collects gateway events, but it only lives while the UI
 * does — once the Activity is killed, nobody was listening and results
 * arriving over the (service-kept) WebSocket vanished silently. This observer
 * is started from [HermesGatewayService] (which owns the background
 * connection) so completions reach the user as notifications whenever no
 * Activity is visible.
 *
 * Scope note: collection runs on the service's scope; when the service dies
 * the connection dies with it, so there is nothing to observe anyway.
 */
@Singleton
class AgentEventObserver @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val foregroundState: AppForegroundState,
    private val notifier: AgentActivityNotifier,
) {

    private val started = AtomicBoolean(false)

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        Timber.i("[AgentObserver] started")
        scope.launch {
            try {
                gatewayClient.events.collect { event ->
                    when (event) {
                        is GatewayEvent.MessageComplete -> {
                            if (!foregroundState.isForeground) {
                                notifier.showTurnComplete(event.sessionId, event.text)
                            }
                        }
                        is GatewayEvent.BackgroundComplete -> {
                            if (!foregroundState.isForeground) {
                                notifier.showBackgroundTaskComplete(
                                    taskId = event.taskId,
                                    sessionId = event.sessionId,
                                    preview = event.text,
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            } finally {
                // Allow a future service restart to re-attach a collector.
                started.set(false)
                Timber.i("[AgentObserver] stopped")
            }
        }
    }
}

package com.hermes.android.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayEventHelpers
import com.hermes.android.gateway.GatewayMethods
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-lifetime completion watcher for proactive notifications.
 *
 * Two delivery paths, because events alone are NOT reliable on mobile:
 *
 * 1. **Live events** (instant): while the WebSocket happens to be up,
 *    message.complete / background.complete raise a notification immediately.
 *
 * 2. **Sync on reconnect** (guaranteed-eventually): when the socket is dead —
 *    screen off, Doze, network handoff — the server still finishes the work
 *    and persists it; the completion EVENT however is emitted into a detached
 *    transport and lost forever. So this observer keeps a `watched` set of
 *    sessions known to have an in-flight turn, and on every reconnect
 *    reconciles it against `session.active_list`: a watched session that is
 *    no longer streaming finished while we weren't listening → notify from
 *    the synced state (Telegram model: trust server state, not event luck).
 *
 * What this still cannot do: wake a phone in deep Doze the moment the server
 * finishes. That requires an out-of-band push channel (FCM); without it the
 * notification lands on the next reconnect (screen-on / network return).
 */
/** What Hermes is doing right now: the running sessions (id → title), since when. */
data class AgentWork(
    val sessions: Map<String, String>,
    val startedAt: Long,
)

@Singleton
class AgentEventObserver @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val foregroundState: AppForegroundState,
    private val notifier: AgentActivityNotifier,
    private val completionTracker: com.hermes.android.data.TaskCompletionTracker,
) {

    private val started = AtomicBoolean(false)

    /** Live session id → last known title, for turns believed in-flight. */
    private val watched = ConcurrentHashMap<String, String>()

    /** Sessions whose message.complete arrived since the last active_list request went out. */
    private val endedDuringSync: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private var reconcileJob: Job? = null

    private val _work = MutableStateFlow<AgentWork?>(null)

    /** Non-null while any turn is in flight — drives the "Hermes is working" notification. */
    val work: StateFlow<AgentWork?> = _work.asStateFlow()

    private fun publishWork() {
        val sessions = watched.toMap()
        _work.value = if (sessions.isEmpty()) {
            null
        } else {
            AgentWork(sessions, _work.value?.startedAt ?: System.currentTimeMillis())
        }
    }

    private fun rememberTitle(id: String, title: String) {
        watched.compute(id) { _, old -> if (old.isNullOrBlank()) title else old }
    }

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        Timber.i("[AgentObserver] started")

        scope.launch {
            gatewayClient.events.collect { event ->
                when (event) {
                    is GatewayEvent.MessageStart -> {
                        event.sessionId?.let { watched.putIfAbsent(it, "") }
                        publishWork()
                        // A lost completion event must not leave "working" up forever.
                        if (gatewayClient.connectionState.value is ConnectionState.Connected) ensureReconcileLoop(scope)
                    }
                    is GatewayEvent.ToolStart -> {
                        // Tool steps do not change the notification (one quiet
                        // "working" card, not a live play-by-play) — they only
                        // matter for a session nobody told us about yet.
                        event.sessionId?.let {
                            if (watched.putIfAbsent(it, "") == null) publishWork()
                        }
                    }
                    is GatewayEvent.MessageComplete -> {
                        event.sessionId?.let { endedDuringSync.add(it) }
                        val title = event.sessionId?.let { watched.remove(it) }
                        publishWork()
                        if (!foregroundState.isForeground) {
                            notifier.showTurnComplete(event.sessionId, event.text, title)
                        }
                    }
                    is GatewayEvent.BackgroundComplete -> {
                        // claim() so the periodic worker won't re-notify the
                        // same completion on its next window.
                        if (!foregroundState.isForeground &&
                            completionTracker.claim(event.taskId)
                        ) {
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
        }

        scope.launch {
            gatewayClient.connectionState.collect { state ->
                if (state is ConnectionState.Connected) ensureReconcileLoop(scope)
            }
        }
    }

    /**
     * Runs while connected AND something is worth watching; exits when the
     * watch set drains. New in-flight turns observed while connected are
     * completed via the live-event path, and any reconnect re-arms this loop.
     */
    private fun ensureReconcileLoop(scope: CoroutineScope) {
        if (reconcileJob?.isActive == true) return
        reconcileJob = scope.launch {
            // Give the low-level auto-resume a moment to settle so
            // active_list reflects the re-attached reality.
            delay(RECONCILE_SETTLE_MS)
            while (gatewayClient.connectionState.value is ConnectionState.Connected) {
                try {
                    reconcile()
                } catch (e: Exception) {
                    Timber.w(e, "[AgentObserver] reconcile failed")
                }
                if (watched.isEmpty()) break
                delay(RECONCILE_INTERVAL_MS)
            }
        }
    }

    private suspend fun reconcile() {
        endedDuringSync.clear()
        val result = gatewayClient.request(GatewayMethods.SESSION_ACTIVE_LIST)
        val rows = ((result as? JsonObject)?.get("sessions") as? JsonArray)
            ?.mapNotNull { it as? JsonObject } ?: return

        fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.content ?: ""

        val streamingNow = mutableMapOf<String, JsonObject>()
        for (row in rows) {
            val id = row.str("id")
            if (id.isEmpty()) continue
            if (GatewayEventHelpers.isBusySessionStatus(row.str("status"))) streamingNow[id] = row
        }

        // Anything streaming server-side deserves watching (covers turns that
        // started while this process wasn't alive to see message.start) —
        // except a turn whose message.complete already landed: the gateway
        // sends it before it clears `running`, so a row read in that gap still
        // says busy, and watching it again would bring back "working" and
        // announce the same reply a second time.
        for ((id, row) in streamingNow) {
            if (id in endedDuringSync) continue
            rememberTitle(id, row.str("title"))
        }

        // A watched session that is no longer streaming finished while we
        // weren't listening — its completion event died with the old socket.
        // The result itself is safe in the server's session store; notify
        // from the synced preview.
        for ((id, title) in watched) {
            if (streamingNow.containsKey(id)) continue
            // The live message.complete may have taken it meanwhile, and notified.
            if (watched.remove(id) == null) continue
            val row = rows.firstOrNull { it.str("id") == id }
            val preview = row?.str("preview").orEmpty().ifBlank { title }
            Timber.i("[AgentObserver] session $id completed while offline — notifying from sync")
            if (!foregroundState.isForeground) {
                notifier.showTurnComplete(id, preview.ifBlank { notifier.taskFinishedText() }, title)
            }
        }
        publishWork()
    }

    private companion object {
        const val RECONCILE_SETTLE_MS = 2_000L
        const val RECONCILE_INTERVAL_MS = 5_000L
    }
}

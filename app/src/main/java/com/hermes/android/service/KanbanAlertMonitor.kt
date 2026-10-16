package com.hermes.android.service

import com.hermes.android.domain.kanban.KanbanRepository
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kanban alerts while the app is in the background: a task on the selected board that
 * reaches `done`, or stops at `blocked` or `review`, raises a notification.
 *
 * It compares board snapshots instead of reading event kinds, so the built-in Linux (no
 * event socket) works the same as a server. A server's event stream triggers a fresh
 * snapshot, and its reopen after a drop catches what changed meanwhile; the built-in Linux
 * is read every minute. The first snapshot after (re)connecting is only the baseline, so
 * nothing old is announced. Runs for the life of [HermesGatewayService], like
 * [AgentEventObserver].
 */
@Singleton
class KanbanAlertMonitor @Inject constructor(
    private val kanban: KanbanRepository,
    private val gatewayClient: GatewayClient,
    private val foregroundState: AppForegroundState,
    private val notifier: AgentActivityNotifier,
) {
    /** The service's scope dies with it; a new service instance starts this again. */
    private var job: Job? = null

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            combine(kanban.switchedOn, gatewayClient.connectionState, kanban.selectedBoard) { on, state, board ->
                Triple(on, state is ConnectionState.Connected, board)
            }
                .distinctUntilChanged()
                .collectLatest { (on, connected, _) ->
                    if (on && connected && kanban.available) watch()
                }
        }
    }

    private suspend fun watch() {
        var baseline: Map<String, KanbanRepository.Task>? = null

        suspend fun refresh() {
            val tasks = try {
                kanban.board().values.flatten().associateBy { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.d(e, "[KanbanAlerts] board read failed")
                return
            }
            val before = baseline
            baseline = tasks
            if (before == null || foregroundState.visible.value) return
            tasks.values.forEach { task ->
                val old = before[task.id] ?: return@forEach
                if (old.status != task.status && task.status in ALERT_STATUSES) {
                    try {
                        notifier.showKanbanTask(task.id, task.title, task.status, task.lastError.ifBlank { task.summary })
                    } catch (e: Exception) {
                        Timber.w(e, "[KanbanAlerts] notification refused")
                    }
                }
            }
        }

        if (kanban.hasLiveChanges) {
            while (true) {
                try {
                    kanban.changes().conflate().collect { refresh() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.d(e, "[KanbanAlerts] event stream dropped")
                }
                delay(RECONNECT_MS)
            }
        } else {
            while (true) {
                refresh()
                delay(POLL_MS)
            }
        }
    }

    private companion object {
        val ALERT_STATUSES = setOf("done", "blocked", "review")
        const val POLL_MS = 60_000L
        const val RECONNECT_MS = 30_000L
    }
}

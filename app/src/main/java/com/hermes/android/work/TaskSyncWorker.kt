package com.hermes.android.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hermes.android.data.SessionRepository
import com.hermes.android.data.TaskCompletionTracker
import com.hermes.android.service.AgentActivityNotifier
import com.hermes.android.service.AppForegroundState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Zero-config completion delivery (Milestone C).
 *
 * The live WebSocket dies in deep Doze, so a task that finishes while the
 * phone is asleep never delivers its live message.complete. This worker is
 * the fallback that needs NO user setup, NO Google, NO server change:
 * WorkManager wakes it in Android's Doze maintenance windows (which grant a
 * brief network window), it syncs task state with the server, and notifies
 * anything the live path missed. Latency is bounded by the maintenance-window
 * cadence (~15+ min) — the price of not using an out-of-band push channel.
 *
 * Idempotent with the live path via [TaskCompletionTracker]: a completion is
 * surfaced exactly once regardless of which path sees it first.
 */
@HiltWorker
class TaskSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: SessionRepository,
    private val completionTracker: TaskCompletionTracker,
    private val notifier: AgentActivityNotifier,
    private val foregroundState: AppForegroundState,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The user is looking at the app — the live event path (and the
        // in-app observer) own delivery; a background poll would only race
        // them. Reschedule naturally on the next window.
        if (foregroundState.isForeground) return Result.success()

        return try {
            val finished = repository.finishedTasks()
            var notifiedCount = 0
            for (task in finished) {
                if (completionTracker.claim(task.id)) {
                    notifier.showBackgroundTaskComplete(
                        taskId = task.id,
                        sessionId = task.id,
                        preview = task.preview.ifBlank { task.title },
                    )
                    notifiedCount++
                }
            }
            Timber.i("[TaskSync] checked ${finished.size} finished, notified $notifiedCount")
            Result.success()
        } catch (e: Exception) {
            // Transient (no network in this window, gateway down) — let
            // WorkManager retry on its backoff.
            Timber.w(e, "[TaskSync] sync failed; will retry")
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "hermes_task_sync"

        /**
         * Idempotent: schedules one periodic sync. KEEP policy so app launches
         * don't reset the cadence. 15 min is WorkManager's minimum period.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TaskSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

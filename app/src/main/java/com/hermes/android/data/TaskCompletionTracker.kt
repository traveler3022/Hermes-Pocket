package com.hermes.android.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which task completions have already been surfaced, so the two
 * delivery paths never double-notify:
 *
 * - [com.hermes.android.service.AgentEventObserver] — instant, while the
 *   WebSocket is up (live message.complete).
 * - [com.hermes.android.work.TaskSyncWorker] — the zero-config fallback, a
 *   periodic worker that wakes in Doze maintenance windows and syncs with the
 *   server for anything the live path missed.
 *
 * Both consult this one persisted set. A task id enters it the first time
 * either path notifies about it and never fires again.
 */
@Singleton
class TaskCompletionTracker @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("hermes_task_completion", Context.MODE_PRIVATE)
    private val notified: MutableSet<String> = java.util.Collections.synchronizedSet(
        (prefs.getStringSet(KEY_NOTIFIED, emptySet()) ?: emptySet()).toMutableSet()
    )

    /** True if this id had not been notified before; marks it notified. */
    fun claim(taskId: String): Boolean {
        synchronized(notified) {
            if (taskId in notified) return false
            notified.add(taskId)
            prefs.edit().putStringSet(KEY_NOTIFIED, notified.toSet()).apply()
            return true
        }
    }

    fun alreadyNotified(taskId: String): Boolean = taskId in notified

    /**
     * Records everything already finished without announcing any of it, once per install.
     *
     * The background sync asks the server for finished tasks and notifies about each one
     * this set does not know. On the very first sync it knows nothing, so every task the
     * user ever ran — read, acted on and forgotten months ago — arrived at once as a
     * "task complete" notification. Nothing there is news; the set simply had no way to
     * say "I was not watching yet" as distinct from "this just happened".
     *
     * Returns true when it did the seeding, which is the caller's signal that this sync
     * has nothing to announce.
     */
    fun seedOnFirstSync(taskIds: Collection<String>): Boolean {
        synchronized(notified) {
            if (prefs.getBoolean(KEY_SEEDED, false)) return false
            notified.addAll(taskIds)
            prefs.edit()
                .putStringSet(KEY_NOTIFIED, notified.toSet())
                .putBoolean(KEY_SEEDED, true)
                .apply()
            return true
        }
    }

    private companion object {
        const val KEY_NOTIFIED = "notified_task_ids"
        const val KEY_SEEDED = "seeded_existing_tasks"
    }
}

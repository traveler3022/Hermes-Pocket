package com.hermes.android.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which sessions were launched as tasks from this app.
 *
 * The gateway's `session.active_list` carries no `source` field, so telling
 * "work I delegated" apart from "every live session" is the client's job.
 * Stored db keys persist across app restarts and live-id rotation (live ids
 * are minted per attach; keys are stable); live ids cover the window before a
 * session has a db row.
 *
 * Interface + prefs impl so contract tests can run on the JVM with an
 * in-memory registry.
 */
interface TaskRegistry {
    fun register(liveId: String, storedKey: String?)
    fun isTask(liveId: String, sessionKey: String): Boolean
}

@Singleton
class PrefsTaskRegistry @Inject constructor(
    @ApplicationContext context: Context,
) : TaskRegistry {

    private val prefs = context.getSharedPreferences("hermes_tasks", Context.MODE_PRIVATE)
    private val storedKeys: MutableSet<String> = java.util.Collections.synchronizedSet(
        (prefs.getStringSet(KEY_TASK_KEYS, emptySet()) ?: emptySet()).toMutableSet()
    )
    private val liveIds: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())

    override fun register(liveId: String, storedKey: String?) {
        liveIds.add(liveId)
        if (!storedKey.isNullOrBlank()) {
            storedKeys.add(storedKey)
            prefs.edit().putStringSet(KEY_TASK_KEYS, storedKeys.toSet()).apply()
        }
    }

    override fun isTask(liveId: String, sessionKey: String): Boolean =
        liveId in liveIds || (sessionKey.isNotBlank() && sessionKey in storedKeys)

    private companion object {
        const val KEY_TASK_KEYS = "task_session_keys"
    }
}

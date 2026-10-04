package com.hermes.android.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings › General › Kanban: off unless the user turns it on. With a remote server it
 * only shows the board (the server runs it anyway); on the built-in Linux it also runs the
 * board on the phone (runtime/linux/LinuxKanban), which is heavy.
 */
@Singleton
class KanbanSwitch @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kanban", Context.MODE_PRIVATE)

    private val _on = MutableStateFlow(prefs.getBoolean(KEY_ON, false))
    val on: StateFlow<Boolean> = _on.asStateFlow()

    fun set(on: Boolean) {
        prefs.edit().putBoolean(KEY_ON, on).apply()
        _on.value = on
    }

    private companion object {
        const val KEY_ON = "on"
    }
}

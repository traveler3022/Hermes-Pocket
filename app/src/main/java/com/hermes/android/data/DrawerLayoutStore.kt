package com.hermes.android.data

import android.content.Context
import com.hermes.android.domain.drawer.DrawerLayout
import com.hermes.android.domain.drawer.DrawerPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import javax.inject.Inject
import javax.inject.Singleton

/** [DrawerPrefs] in this device's preferences; every screen reading [prefs] sees a change at once. */
@Singleton
class DrawerLayoutStore @Inject constructor(
    @ApplicationContext context: Context,
) : DrawerLayout {

    private val store = context.getSharedPreferences("drawer_layout", Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(read())
    override val prefs: StateFlow<DrawerPrefs> = _prefs.asStateFlow()

    override fun update(edit: (DrawerPrefs) -> DrawerPrefs) {
        val next = _prefs.updateAndGet(edit)
        store.edit()
            .putBoolean(KEY_PAGES, next.pages)
            .putStringSet(KEY_HIDDEN_PAGES, next.hiddenPages)
            .putBoolean(KEY_TOPICS, next.topics)
            .putBoolean(KEY_PINNED, next.pinned)
            .putBoolean(KEY_PROJECTS, next.projects)
            .putBoolean(KEY_FILTERS, next.filters)
            .putBoolean(KEY_RUNNING, next.runningFilter)
            .putBoolean(KEY_NEEDS_YOU, next.needsYouFilter)
            .apply()
    }

    private fun read(): DrawerPrefs {
        val defaults = DrawerPrefs()
        return DrawerPrefs(
            pages = store.getBoolean(KEY_PAGES, defaults.pages),
            // A copy: the set SharedPreferences hands back must not be kept or changed.
            hiddenPages = store.getStringSet(KEY_HIDDEN_PAGES, null)?.toSet().orEmpty(),
            topics = store.getBoolean(KEY_TOPICS, defaults.topics),
            pinned = store.getBoolean(KEY_PINNED, defaults.pinned),
            projects = store.getBoolean(KEY_PROJECTS, defaults.projects),
            filters = store.getBoolean(KEY_FILTERS, defaults.filters),
            runningFilter = store.getBoolean(KEY_RUNNING, defaults.runningFilter),
            needsYouFilter = store.getBoolean(KEY_NEEDS_YOU, defaults.needsYouFilter),
        )
    }

    private companion object {
        const val KEY_PAGES = "pages"
        const val KEY_HIDDEN_PAGES = "hidden_pages"
        const val KEY_TOPICS = "topics"
        const val KEY_PINNED = "pinned"
        const val KEY_PROJECTS = "projects"
        const val KEY_FILTERS = "filters"
        const val KEY_RUNNING = "filter_running"
        const val KEY_NEEDS_YOU = "filter_needs_you"
    }
}

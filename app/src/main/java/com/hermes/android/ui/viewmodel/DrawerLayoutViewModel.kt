package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.hermes.android.domain.drawer.DrawerLayout
import com.hermes.android.domain.drawer.DrawerPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** What the chat drawer shows: read by the drawer, changed on Settings › Appearance › Drawer. */
@HiltViewModel
class DrawerLayoutViewModel @Inject constructor(
    private val layout: DrawerLayout,
) : ViewModel() {
    val prefs: StateFlow<DrawerPrefs> = layout.prefs

    fun update(edit: (DrawerPrefs) -> DrawerPrefs) = layout.update(edit)
}

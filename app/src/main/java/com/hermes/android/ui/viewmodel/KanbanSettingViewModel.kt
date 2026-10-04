package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.KanbanSwitch
import com.hermes.android.runtime.RuntimeState
import com.hermes.android.runtime.RuntimeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Settings › General › Kanban. */
@HiltViewModel
class KanbanSettingViewModel @Inject constructor(
    private val switch: KanbanSwitch,
    private val runtime: HermesRuntime,
) : ViewModel() {

    val on: StateFlow<Boolean> = switch.on

    private val _applying = MutableStateFlow(false)

    /** The built-in Linux gateway is restarting to apply the switch. */
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    /**
     * With a remote server this only shows or hides the board. The built-in Linux runs
     * Kanban itself and reads the switch when its gateway starts, so a running one is
     * restarted (a reply in progress there is cut off).
     */
    fun set(on: Boolean) {
        if (_applying.value || on == switch.on.value) return
        switch.set(on)
        if (runtime.type != RuntimeType.PROOT_LINUX || runtime.state.value !is RuntimeState.Running) return
        viewModelScope.launch {
            _applying.value = true
            try {
                runtime.stopGateway()
                runtime.startGateway()
            } catch (e: Exception) {
                Timber.w(e, "[Kanban] gateway restart failed")
            } finally {
                _applying.value = false
            }
        }
    }
}

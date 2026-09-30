package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.JunkKind
import com.hermes.android.data.StorageCleaner
import com.hermes.android.data.StorageScan
import com.hermes.android.runtime.HermesRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class StorageUiState(
    val calculating: Boolean = true,
    val scan: StorageScan? = null,
    /** Everything starts selected, as Telegram's Arrays.fill(selected, true) after each count. */
    val selected: Set<JunkKind> = JunkKind.entries.toSet(),
    val collapsed: Boolean = true,
    /** 0..1 while clearing, null otherwise. */
    val clearing: Float? = null,
) {
    fun size(kind: JunkKind): Long = scan?.sizes?.get(kind) ?: 0L

    val selectedBytes: Long get() = selected.sumOf { size(it) }
}

sealed interface StorageEvent {
    data class Cleared(val bytes: Long) : StorageEvent

    /** Hermes is being installed or updated; its caches are in use. */
    data object Busy : StorageEvent
}

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val cleaner: StorageCleaner,
    private val runtime: HermesRuntime,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<StorageEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StorageEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.clearing != null) return
        _state.update { it.copy(calculating = true) }
        viewModelScope.launch {
            val scan = scanOrNull()
            _state.update { it.copy(calculating = false, scan = scan, selected = JunkKind.entries.toSet()) }
        }
    }

    /** Whether [kinds] may all be switched off: at least one section has to stay selected. */
    fun canDeselect(kinds: Set<JunkKind>): Boolean {
        val state = _state.value
        return state.selected.any { it !in kinds && state.size(it) > 0 }
    }

    fun setSelected(kinds: Set<JunkKind>, selected: Boolean) = _state.update {
        it.copy(selected = if (selected) it.selected + kinds else it.selected - kinds)
    }

    fun toggleCollapsed() = _state.update { it.copy(collapsed = !it.collapsed) }

    fun clear() {
        val state = _state.value
        if (state.clearing != null || state.calculating) return
        if (runtime.installProgress.value != null) {
            _events.tryEmit(StorageEvent.Busy)
            return
        }
        val kinds = state.selected.filter { state.size(it) > 0 }.toSet()
        if (kinds.isEmpty()) return
        _state.update { it.copy(clearing = 0f) }
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val freed = try {
                cleaner.clear(kinds) { progress -> _state.update { it.copy(clearing = progress) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Storage] Clearing failed")
                0L
            }
            val scan = scanOrNull()
            // Telegram keeps its clearing sheet up for at least a second so it doesn't flash.
            delay((MIN_CLEARING_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            _state.update { it.copy(clearing = null, scan = scan ?: it.scan, selected = JunkKind.entries.toSet()) }
            _events.emit(StorageEvent.Cleared(freed))
        }
    }

    private suspend fun scanOrNull(): StorageScan? = try {
        cleaner.scan()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "[Storage] Scan failed")
        null
    }

    private companion object {
        const val MIN_CLEARING_MS = 1_000L
    }
}

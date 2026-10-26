package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.storage.SpaceExplorer
import com.hermes.android.domain.storage.SpaceItem
import com.hermes.android.domain.storage.SpaceOverview
import com.hermes.android.domain.storage.SpacePlace
import com.hermes.android.domain.storage.SpaceVerdict
import com.hermes.android.runtime.HermesRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

/** Where the page is: the places, one place's top entries, or a folder inside. */
sealed interface SpaceLevel {
    data object Places : SpaceLevel
    data class Place(val place: SpacePlace) : SpaceLevel
    data class Folder(val place: SpacePlace, val guestPath: String, val name: String) : SpaceLevel
}

data class StorageSpaceUiState(
    val scanning: Boolean = true,
    val overview: SpaceOverview? = null,
    /** The way down from the places; the last one is on screen. */
    val levels: List<SpaceLevel> = listOf(SpaceLevel.Places),
    val loadingItems: Boolean = false,
    val items: List<SpaceItem> = emptyList(),
    /** Picked entries (guest path → item); nothing is picked for the user. */
    val picked: Map<String, SpaceItem> = emptyMap(),
    /** 0..1 while deleting, null otherwise. */
    val deleting: Float? = null,
) {
    val level: SpaceLevel get() = levels.last()
    val pickedBytes: Long get() = picked.values.sumOf { it.size }
    val pickedCareful: Boolean get() = picked.values.any { it.place.verdict == SpaceVerdict.Careful }
}

sealed interface StorageSpaceEvent {
    data class Deleted(val bytes: Long) : StorageSpaceEvent

    /** Hermes is being installed or updated; its folders are in use. */
    data object Busy : StorageSpaceEvent
}

@HiltViewModel
class StorageSpaceViewModel @Inject constructor(
    private val explorer: SpaceExplorer,
    private val runtime: HermesRuntime,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageSpaceUiState())
    val state: StateFlow<StorageSpaceUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<StorageSpaceEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StorageSpaceEvent> = _events.asSharedFlow()

    private var itemsJob: Job? = null

    init {
        rescan()
    }

    private fun rescan() {
        _state.update { it.copy(scanning = true) }
        viewModelScope.launch {
            val overview = try {
                explorer.scan()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Storage] Space scan failed")
                null
            }
            _state.update { it.copy(scanning = false, overview = overview ?: it.overview) }
            loadItems()
        }
    }

    fun openPlace(place: SpacePlace) = go(_state.value.levels + SpaceLevel.Place(place))

    fun openFolder(item: SpaceItem) {
        if (!item.isDirectory) return
        go(_state.value.levels + SpaceLevel.Folder(item.place, item.guestPath, item.name))
    }

    /** One level up; false at the top, where back leaves the page. */
    fun back(): Boolean {
        val levels = _state.value.levels
        if (levels.size <= 1) return false
        go(levels.dropLast(1))
        return true
    }

    private fun go(levels: List<SpaceLevel>) {
        _state.update { it.copy(levels = levels) }
        loadItems()
    }

    private fun loadItems() {
        itemsJob?.cancel()
        when (val level = _state.value.level) {
            SpaceLevel.Places -> _state.update { it.copy(items = emptyList(), loadingItems = false) }
            is SpaceLevel.Place -> _state.update {
                it.copy(items = it.overview?.roots?.get(level.place).orEmpty(), loadingItems = false)
            }
            is SpaceLevel.Folder -> {
                _state.update { it.copy(items = emptyList(), loadingItems = true) }
                itemsJob = viewModelScope.launch {
                    val items = try {
                        explorer.list(level.guestPath)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "[Storage] Listing a folder failed")
                        emptyList()
                    }
                    _state.update { it.copy(items = items, loadingItems = false) }
                }
            }
        }
    }

    fun togglePick(item: SpaceItem) {
        if (!item.deletable) return
        _state.update { state ->
            val picked = if (item.guestPath in state.picked) {
                state.picked - item.guestPath
            } else {
                // A folder covers what was picked inside it; something inside a picked folder is already covered.
                if (state.picked.keys.any { item.guestPath.startsWith("$it/") }) return@update state
                state.picked.filterKeys { !it.startsWith(item.guestPath + "/") } + (item.guestPath to item)
            }
            state.copy(picked = picked)
        }
    }

    fun clearPicks() = _state.update { it.copy(picked = emptyMap()) }

    fun deletePicked() {
        val state = _state.value
        if (state.deleting != null || state.picked.isEmpty()) return
        if (runtime.installProgress.value != null) {
            _events.tryEmit(StorageSpaceEvent.Busy)
            return
        }
        val paths = state.picked.keys.toList()
        _state.update { it.copy(deleting = 0f) }
        viewModelScope.launch {
            val freed = try {
                explorer.delete(paths) { progress -> _state.update { it.copy(deleting = progress) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Storage] Deleting failed")
                0L
            }
            _state.update { it.copy(deleting = null, picked = emptyMap()) }
            _events.emit(StorageSpaceEvent.Deleted(freed))
            rescan()
        }
    }
}

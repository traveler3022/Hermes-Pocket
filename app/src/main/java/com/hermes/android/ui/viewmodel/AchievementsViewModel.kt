package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.achievements.Achievement
import com.hermes.android.domain.achievements.Achievements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Settings › Achievements: Hermes' badges from the session history, by category. */
@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val achievements: Achievements,
) : ViewModel() {

    data class UiState(
        val items: List<Achievement> = emptyList(),
        val unlocked: Int = 0,
        val total: Int = 0,
        val loading: Boolean = true,
        /** Hermes is reading the session history; the list refreshes until it is done. */
        val scanning: Boolean = false,
        /** The Rescan button's own call is running. */
        val rescanning: Boolean = false,
        val loadError: String? = null,
        /** Hermes could not read the history (shown above the list). */
        val scanError: String? = null,
        /** Category shown; null = all. */
        val filter: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Loads the board, then asks again every few seconds while Hermes is still scanning. */
    fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(loading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                var polls = 0
                while (true) {
                    val board = achievements.board()
                    apply(board.items, board.unlocked, board.total, board.scanning, board.error)
                    if (!board.scanning) break
                    if (++polls > MAX_POLLS) {
                        _uiState.update { it.copy(scanning = false) }
                        break
                    }
                    delay(POLL_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Achievements] load failed")
                _uiState.update { it.copy(loading = false, scanning = false, loadError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun rescan() {
        if (_uiState.value.rescanning) return
        loadJob?.cancel()
        _uiState.update { it.copy(rescanning = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                val board = achievements.rescan()
                apply(board.items, board.unlocked, board.total, board.scanning, board.error)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Achievements] rescan failed")
                _uiState.update { it.copy(loadError = e.message ?: e.javaClass.simpleName) }
            } finally {
                _uiState.update { it.copy(rescanning = false) }
            }
        }
    }

    fun setFilter(category: String?) {
        _uiState.update { it.copy(filter = category) }
    }

    private fun apply(items: List<Achievement>, unlocked: Int, total: Int, scanning: Boolean, error: String?) {
        _uiState.update { state ->
            state.copy(
                items = items,
                unlocked = unlocked,
                total = total,
                loading = false,
                scanning = scanning,
                scanError = error,
                filter = state.filter?.takeIf { f -> items.any { it.category == f } },
            )
        }
    }

    private companion object {
        const val POLL_MS = 3_000L

        /** About three minutes; a longer scan shows its result on the next visit or Reload. */
        const val MAX_POLLS = 60
    }
}

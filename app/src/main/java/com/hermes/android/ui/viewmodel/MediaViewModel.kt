package com.hermes.android.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.files.Downloads
import com.hermes.android.domain.files.SavedFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** The Media screen: what the user downloaded to the phone's Download/Hermes. */
@HiltViewModel
class MediaViewModel @Inject constructor(
    private val downloads: Downloads,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val files: List<SavedFile>) : UiState
        data class Failed(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loading: Job? = null

    /** Reads the folder again; called whenever the screen comes back, as files may have come or gone. */
    fun refresh() {
        loading?.cancel()
        loading = viewModelScope.launch {
            _uiState.value = try {
                UiState.Loaded(downloads.list())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Media] Could not list downloads")
                // Keep what is on screen; only a first load shows the error.
                (_uiState.value as? UiState.Loaded) ?: UiState.Failed(e.message ?: e.toString())
            }
        }
    }

    suspend fun thumbnail(uri: String, sizePx: Int): Bitmap? = downloads.thumbnail(uri, sizePx)
}

package com.hermes.android.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.PetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Pet gallery: adopt / rename / remove / disable. */
@HiltViewModel
class PetViewModel @Inject constructor(
    private val repository: PetRepository,
) : ViewModel() {

    data class PetUiState(
        val enabled: Boolean = false,
        val activeSlug: String = "",
        val pets: List<PetRepository.PetEntry> = emptyList(),
        val thumbnails: Map<String, Bitmap> = emptyMap(),
        val isLoading: Boolean = false,
        val isBusy: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(PetUiState())
    val uiState = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            refreshGallery()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /** Suspends until the gallery list itself is refreshed — awaitable from
     *  action handlers so isBusy doesn't clear before the reload lands. */
    private suspend fun refreshGallery() {
        try {
            val gallery = repository.gallery()
            _uiState.value = _uiState.value.copy(
                enabled = gallery.enabled,
                activeSlug = gallery.activeSlug,
                pets = gallery.pets,
            )
            gallery.pets.forEach { loadThumbnail(it.slug, it.thumbUrl) }
        } catch (e: Exception) {
            Timber.w(e, "[Pet] load failed")
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

    private fun loadThumbnail(slug: String, sourceUrl: String) {
        if (_uiState.value.thumbnails.containsKey(slug)) return
        viewModelScope.launch {
            val bmp = try {
                repository.thumbnail(slug, sourceUrl)
            } catch (e: Exception) {
                Timber.w(e, "[Pet] thumbnail failed for $slug")
                null
            }
            if (bmp != null) {
                _uiState.value = _uiState.value.copy(
                    thumbnails = _uiState.value.thumbnails + (slug to bmp),
                )
            }
        }
    }

    fun select(slug: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            try {
                repository.select(slug)
                refreshGallery()
                _uiState.value = _uiState.value.copy(isBusy = false)
            } catch (e: Exception) {
                Timber.e(e, "[Pet] select failed")
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.message)
            }
        }
    }

    fun remove(slug: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            try {
                repository.remove(slug)
                refreshGallery()
                _uiState.value = _uiState.value.copy(isBusy = false)
            } catch (e: Exception) {
                Timber.e(e, "[Pet] remove failed")
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.message)
            }
        }
    }

    fun rename(slug: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            try {
                repository.rename(slug, name)
                refreshGallery()
                _uiState.value = _uiState.value.copy(isBusy = false)
            } catch (e: Exception) {
                Timber.e(e, "[Pet] rename failed")
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.message)
            }
        }
    }

    fun disable() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            try {
                repository.disable()
                refreshGallery()
                _uiState.value = _uiState.value.copy(isBusy = false)
            } catch (e: Exception) {
                Timber.e(e, "[Pet] disable failed")
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

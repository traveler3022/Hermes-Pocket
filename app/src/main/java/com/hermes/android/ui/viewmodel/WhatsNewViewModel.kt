package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.updates.AppRelease
import com.hermes.android.domain.updates.AppUpdates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The one-time "What's new" after the app was updated: the release notes of the
 * version now running. A fresh install never sees it; offline, it waits for the
 * next launch. Asked once per activity, since the ViewModel outlives rotation.
 */
@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    private val updates: AppUpdates,
) : ViewModel() {

    private val _release = MutableStateFlow<AppRelease?>(null)

    /** The release to show, or null when there is nothing to show. */
    val release = _release.asStateFlow()

    init {
        viewModelScope.launch { _release.value = updates.pendingWhatsNew() }
    }

    fun dismiss() {
        updates.markWhatsNewSeen()
        _release.value = null
    }
}

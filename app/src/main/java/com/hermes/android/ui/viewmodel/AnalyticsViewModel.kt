package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.analytics.UsageAnalytics
import com.hermes.android.domain.analytics.UsageAnalytics.Report
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AnalyticsUiState(
    val days: Int = DEFAULT_DAYS,
    val loading: Boolean = false,
    /** Null until the first report arrives; kept while a newer window loads. */
    val report: Report? = null,
    val loadError: String? = null,
) {
    companion object {
        const val DEFAULT_DAYS = 30
        const val TODAY = UsageAnalytics.TODAY
        val WINDOWS = listOf(TODAY, 7, 30, 90, 365)
    }
}

/** Token and cost statistics for today or over 7, 30, 90 or 365 days, read from the server's analytics routes. */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val analytics: UsageAnalytics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun selectDays(days: Int) {
        if (days == _uiState.value.days) return
        _uiState.update { it.copy(days = days) }
        refresh()
    }

    fun refresh() {
        // A newer window replaces a slower older one, so its answer can't land on top.
        loadJob?.cancel()
        val days = _uiState.value.days
        _uiState.update { it.copy(loading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                val report = analytics.load(days)
                _uiState.update { it.copy(loading = false, report = report) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Analytics] load failed")
                _uiState.update { it.copy(loading = false, loadError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }
}

package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Cron Scheduler screen.
 *
 * Uses cron.manage RPC (server.py:12176-12201).
 * Actions: list, add, remove, pause, resume.
 * Response shape from cronjob_tools.py:678: {"success":true, "count":N, "jobs":[...]}
 * Job fields from _format_job (cronjob_tools.py:483-520):
 *   job_id, name, schedule, prompt_preview, next_run_at, last_run_at,
 *   last_status, enabled, state
 *
 * Reference: ADR-008, Phase 1.5 Rule 1
 */
@HiltViewModel
class CronViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CronUiState())
    val uiState: StateFlow<CronUiState> = _uiState.asStateFlow()

    init {
        loadJobs()
    }

    fun loadJobs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val params = buildJsonObject { put("action", "list") }
                val result = gatewayClient.request(GatewayMethods.CRON_MANAGE, params.toMap())
                val jobs = parseJobs(result)
                _uiState.value = _uiState.value.copy(
                    jobs = jobs,
                    isLoading = false,
                )
                Timber.i("[Cron] Loaded ${jobs.size} jobs")
            } catch (e: GatewayException) {
                Timber.e(e, "[Cron] Failed to load")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load cron jobs: ${e.message}",
                )
            }
        }
    }

    private fun parseJobs(result: kotlinx.serialization.json.JsonElement): List<CronJob> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            val arr = obj["jobs"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            arr.mapNotNull { item ->
                val j = item as? JsonObject ?: return@mapNotNull null
                CronJob(
                    // _format_job uses "job_id" (not "id")
                    id = j["job_id"]?.let { (it as? JsonPrimitive)?.content } ?: "",
                    name = j["name"]?.let { (it as? JsonPrimitive)?.content } ?: "Untitled",
                    schedule = j["schedule"]?.let { (it as? JsonPrimitive)?.content } ?: "",
                    promptPreview = j["prompt_preview"]?.let { (it as? JsonPrimitive)?.content } ?: "",
                    enabled = j["enabled"]?.let { (it as? JsonPrimitive)?.content } != "false",
                    lastRunAt = j["last_run_at"]?.let { (it as? JsonPrimitive)?.content },
                    nextRunAt = j["next_run_at"]?.let { (it as? JsonPrimitive)?.content },
                    lastStatus = j["last_status"]?.let { (it as? JsonPrimitive)?.content },
                    state = j["state"]?.let { (it as? JsonPrimitive)?.content } ?: "scheduled",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun createJob(name: String, schedule: String, prompt: String) {
        viewModelScope.launch {
            try {
                // server.py:12185: action="add", params: name, schedule, prompt
                val params = buildJsonObject {
                    put("action", "add")
                    put("name", name)
                    put("schedule", schedule)
                    put("prompt", prompt)
                }
                gatewayClient.request(GatewayMethods.CRON_MANAGE, params.toMap())
                Timber.i("[Cron] Job created: $name")
                _uiState.value = _uiState.value.copy(showCreateDialog = false)
                loadJobs()
            } catch (e: Exception) {
                Timber.e(e, "[Cron] Create failed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to create job: ${e.message}",
                )
            }
        }
    }

    fun toggleJob(jobId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                // server.py:12199: action in {"remove", "pause", "resume"}
                val action = if (enabled) "resume" else "pause"
                val params = buildJsonObject {
                    put("action", action)
                    put("name", jobId)
                }
                gatewayClient.request(GatewayMethods.CRON_MANAGE, params.toMap())
                Timber.i("[Cron] Job $jobId -> $action")
                _uiState.value = _uiState.value.copy(
                    jobs = _uiState.value.jobs.map {
                        if (it.id == jobId) it.copy(enabled = enabled) else it
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "[Cron] Toggle failed")
            }
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            try {
                // server.py:12199: action="remove", param name=job_id
                val params = buildJsonObject {
                    put("action", "remove")
                    put("name", jobId)
                }
                gatewayClient.request(GatewayMethods.CRON_MANAGE, params.toMap())
                Timber.i("[Cron] Job removed: $jobId")
                loadJobs()
            } catch (e: Exception) {
                Timber.e(e, "[Cron] Remove failed")
            }
        }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class CronUiState(
    val jobs: List<CronJob> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showCreateDialog: Boolean = false,
)

data class CronJob(
    val id: String,
    val name: String,
    val schedule: String,
    val promptPreview: String,
    val enabled: Boolean,
    val lastRunAt: String?,
    val nextRunAt: String?,
    val lastStatus: String?,
    val state: String,
)

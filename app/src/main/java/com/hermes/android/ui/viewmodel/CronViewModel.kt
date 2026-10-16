package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.cron.CronBlueprints
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.json.contentOrNull

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
    private val blueprints: CronBlueprints,
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
                // Paused jobs are left out unless asked for; a toggle would read as deletion.
                val params = buildJsonObject { put("action", "list"); put("include_disabled", true) }
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
                    id = j["job_id"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "",
                    name = j["name"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "Untitled",
                    schedule = j["schedule"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "",
                    promptPreview = j["prompt_preview"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "",
                    enabled = j["enabled"]?.let { (it as? JsonPrimitive)?.contentOrNull } != "false",
                    lastRunAt = j["last_run_at"]?.let { (it as? JsonPrimitive)?.contentOrNull },
                    nextRunAt = j["next_run_at"]?.let { (it as? JsonPrimitive)?.contentOrNull },
                    lastStatus = j["last_status"]?.let { (it as? JsonPrimitive)?.contentOrNull },
                    state = j["state"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "scheduled",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun createJob(name: String, schedule: String, prompt: String) {
        viewModelScope.launch {
            try {
                addJob(name, schedule, prompt)
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

    /**
     * cron.manage has no update verb, so the edit goes through `hermes cron edit` (cli.exec),
     * as the dashboard's PUT does: the job keeps its id, run history, skills, delivery target
     * and model pin, which the old add-then-remove lost.
     */
    fun updateJob(jobId: String, name: String, schedule: String, prompt: String) {
        viewModelScope.launch {
            try {
                // `--opt=value`: a prompt that starts with "-" is not taken for a flag.
                val argv = listOf("cron", "edit", jobId, "--name=$name", "--schedule=$schedule") +
                    listOfNotNull(prompt.takeIf { it.isNotBlank() }?.let { "--prompt=$it" })
                cli(argv, 60)
                Timber.i("[Cron] Job updated: $jobId")
                _uiState.value = _uiState.value.copy(editingJob = null)
                loadJobs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Cron] Update failed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update job: ${e.message}",
                )
            }
        }
    }

    /**
     * Run now: `hermes cron run` runs the job to the end in that process (the dashboard's
     * trigger does the same), so this waits for it; the outcome line is shown afterwards.
     */
    fun runJobNow(jobId: String) {
        if (jobId in _uiState.value.runningJobIds) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(runningJobIds = _uiState.value.runningJobIds + jobId)
            try {
                val out = cli(listOf("cron", "run", jobId), RUN_TIMEOUT_S)
                // The CLI's last line is its verdict ("Ran now: succeeded.", "Running in background.").
                val verdict = out.lines().lastOrNull { it.isNotBlank() }?.trim()
                _uiState.value = _uiState.value.copy(notice = verdict)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Cron] Run failed")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to run job: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(runningJobIds = _uiState.value.runningJobIds - jobId)
            }
            loadJobs()
        }
    }

    /** `hermes <argv>` through cli.exec; a non-zero exit throws with the CLI's last line. */
    private suspend fun cli(argv: List<String>, timeoutS: Int): String {
        val result = gatewayClient.request(
            GatewayMethods.CLI_EXEC,
            mapOf(
                "argv" to kotlinx.serialization.json.JsonArray(argv.map { JsonPrimitive(it) }),
                "timeout" to JsonPrimitive(timeoutS),
            ),
            timeoutMs = timeoutS * 1000L + 30_000L,
            trackSession = false,
        ) as? JsonObject ?: throw IllegalStateException("cli.exec: no result")
        fun str(key: String) = (result[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (str("blocked") == "true") throw IllegalStateException(str("hint"))
        val output = str("output")
        if (str("code") != "0") {
            throw IllegalStateException(output.lines().lastOrNull { it.isNotBlank() }?.trim() ?: "hermes ${argv.take(2).joinToString(" ")} failed")
        }
        return output
    }

    /** Returns the new job id. A rejected job comes back as {"error": …} inside a success reply. */
    private suspend fun addJob(name: String, schedule: String, prompt: String): String? {
        val result = manage(buildJsonObject {
            put("action", "add")
            put("name", name)
            put("schedule", schedule)
            put("prompt", prompt)
        })
        return (result["job_id"] as? JsonPrimitive)?.contentOrNull
    }

    private suspend fun manage(params: JsonObject): JsonObject {
        val result = gatewayClient.request(GatewayMethods.CRON_MANAGE, params.toMap()) as? JsonObject
            ?: throw IllegalStateException("Empty reply from cron.manage")
        (result["error"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { throw IllegalStateException(it) }
        return result
    }

    /**
     * Open the editor with the job's full prompt. The list only carries
     * `prompt_preview` — the first 100 characters plus "..." — and the editor
     * used to start from that, so saving any edit (even just the schedule)
     * replaced a longer prompt with its truncated preview for good. There is
     * no RPC for the full prompt; it is read from the cron store instead, and
     * when that fails the editor stays closed rather than truncate the job.
     */
    fun startEditJob(job: CronJob) {
        if (!job.promptPreview.endsWith("...")) {
            _uiState.value = _uiState.value.copy(editingJob = job.copy(fullPrompt = job.promptPreview))
            return
        }
        viewModelScope.launch {
            try {
                val out = gatewayClient.execPython(
                    """
                    import base64, json, pathlib
                    jid = base64.b64decode('${b64(job.id)}').decode()
                    p = pathlib.Path.home() / '.hermes' / 'cron' / 'jobs.json'
                    data = json.loads(p.read_text(encoding='utf-8-sig'), strict=False) if p.exists() else {}
                    jobs = data.get('jobs', []) if isinstance(data, dict) else data
                    job = next((j for j in jobs or [] if isinstance(j, dict) and str(j.get('id')) == jid), None)
                    if job is None:
                        raise SystemExit('job not found in ~/.hermes/cron/jobs.json')
                    print(json.dumps({'prompt': str(job.get('prompt') or '')}))
                    """.trimIndent()
                )
                val prompt = ((kotlinx.serialization.json.Json.parseToJsonElement(out) as? JsonObject)
                    ?.get("prompt") as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalStateException("no prompt in the cron store")
                _uiState.value = _uiState.value.copy(editingJob = job.copy(fullPrompt = prompt))
            } catch (e: Exception) {
                Timber.e(e, "[Cron] Could not read the full prompt of ${job.id}")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Can't edit: couldn't read the full prompt (${e.message})",
                )
            }
        }
    }

    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(editingJob = null)
    }

    fun toggleJob(jobId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                // server.py:12199: action in {"remove", "pause", "resume"}
                val action = if (enabled) "resume" else "pause"
                // manage(): a refusal comes back as {"error": …} inside a success reply.
                manage(buildJsonObject {
                    put("action", action)
                    put("name", jobId)
                })
                Timber.i("[Cron] Job $jobId -> $action")
                _uiState.value = _uiState.value.copy(
                    jobs = _uiState.value.jobs.map {
                        if (it.id == jobId) it.copy(enabled = enabled) else it
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "[Cron] Toggle failed")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to ${if (enabled) "resume" else "pause"} job: ${e.message}")
            }
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            try {
                // server.py:12199: action="remove", param name=job_id
                manage(buildJsonObject {
                    put("action", "remove")
                    put("name", jobId)
                })
                Timber.i("[Cron] Job removed: $jobId")
                loadJobs()
            } catch (e: Exception) {
                Timber.e(e, "[Cron] Remove failed")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to delete job: ${e.message}")
            }
        }
    }

    // ── Blueprints: ready-made jobs from Hermes' catalog ──────────────────

    /** Opens the template list; loads it the first time (and after a failed load). */
    fun openBlueprints() {
        _uiState.value = _uiState.value.copy(showBlueprints = true)
        if (_uiState.value.blueprints.isEmpty() && !_uiState.value.blueprintsLoading) loadBlueprints()
    }

    fun closeBlueprints() {
        _uiState.value = _uiState.value.copy(showBlueprints = false)
    }

    fun loadBlueprints() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(blueprintsLoading = true, blueprintsError = null)
            try {
                val list = blueprints.list()
                _uiState.value = _uiState.value.copy(blueprints = list, blueprintsLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Cron] Blueprints failed to load")
                _uiState.value = _uiState.value.copy(
                    blueprintsLoading = false,
                    blueprintsError = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    fun pickBlueprint(blueprint: CronBlueprints.Blueprint?) {
        _uiState.value = _uiState.value.copy(activeBlueprint = blueprint, blueprintError = null)
    }

    /** Creates the job; on success closes the form and the list and reloads the jobs. */
    fun createFromBlueprint(key: String, values: Map<String, String>) {
        if (_uiState.value.creatingFromBlueprint) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(creatingFromBlueprint = true, blueprintError = null)
            try {
                blueprints.create(key, values)
                Timber.i("[Cron] Job created from blueprint $key")
                _uiState.value = _uiState.value.copy(
                    creatingFromBlueprint = false,
                    activeBlueprint = null,
                    showBlueprints = false,
                )
                loadJobs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Cron] Blueprint $key refused")
                // Shown inside the form: it is usually a value to fix (a malformed time).
                _uiState.value = _uiState.value.copy(
                    creatingFromBlueprint = false,
                    blueprintError = e.message ?: e.javaClass.simpleName,
                )
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

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    private companion object {
        /** cli.exec's own ceiling. */
        const val RUN_TIMEOUT_S = 600
    }
}

data class CronUiState(
    val jobs: List<CronJob> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** A one-line result to show, such as a manual run's verdict. */
    val notice: String? = null,
    /** Jobs whose manual run is still going. */
    val runningJobIds: Set<String> = emptySet(),
    val showCreateDialog: Boolean = false,
    val editingJob: CronJob? = null,
    val showBlueprints: Boolean = false,
    val blueprints: List<CronBlueprints.Blueprint> = emptyList(),
    val blueprintsLoading: Boolean = false,
    val blueprintsError: String? = null,
    /** The blueprint whose form is open. */
    val activeBlueprint: CronBlueprints.Blueprint? = null,
    val creatingFromBlueprint: Boolean = false,
    /** Why the server refused the form's values. */
    val blueprintError: String? = null,
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
    /** The whole prompt, set only on the job handed to the editor. */
    val fullPrompt: String? = null,
)

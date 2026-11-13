package com.hermes.android.data

import com.hermes.android.domain.cron.CronJobs
import com.hermes.android.domain.cron.CronJobs.CatchUp
import com.hermes.android.domain.cron.CronJobs.ModelPin
import com.hermes.android.domain.cron.CronJobs.RunOutcome
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The dashboard's job routes (`hermes_cli/web_routers/cron.py`): `PUT /api/cron/jobs/{id}`
 * with `{"updates": {...}}` (a string schedule is parsed by `cron.jobs.update_job`) and
 * `POST /api/cron/jobs/{id}/trigger`, which runs the job to the end and answers with the
 * refreshed job, or 409 when it is already running. `POST .../cancel` stops a live run.
 */
@Singleton
class CronJobsRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : CronJobs {

    override suspend fun update(
        jobId: String,
        name: String,
        schedule: String,
        prompt: String,
        model: ModelPin?,
        catchUp: CatchUp?,
    ) {
        val body = buildJsonObject {
            put("updates", buildJsonObject {
                put("name", name)
                put("schedule", schedule)
                if (prompt.isNotBlank()) put("prompt", prompt)
                when (model) {
                    null -> Unit
                    // `pinned: false` releases provider and model together (cron.jobs._apply_pin_update).
                    ModelPin.Main -> put("pinned", false)
                    is ModelPin.Fixed -> {
                        put("provider", model.provider)
                        put("model", model.model)
                    }
                }
                when (catchUp) {
                    null -> Unit
                    CatchUp.DEFAULT -> put("catch_up", JsonNull)
                    CatchUp.RUN_ONCE -> put("catch_up", true)
                    CatchUp.SKIP -> put("catch_up", false)
                }
            })
        }
        dashboard.call("PUT", "/api/cron/jobs/${encode(jobId)}", body)
    }

    override suspend fun catchUp(jobId: String): CatchUp {
        val job = dashboard.call("GET", "/api/cron/jobs/${encode(jobId)}") as? JsonObject
        return when ((job?.get("catch_up") as? JsonPrimitive)?.booleanOrNull) {
            true -> CatchUp.RUN_ONCE
            false -> CatchUp.SKIP
            null -> CatchUp.DEFAULT
        }
    }

    override suspend fun cancelRun(jobId: String): Boolean {
        val result = dashboard.call("POST", "/api/cron/jobs/${encode(jobId)}/cancel") as? JsonObject
        return (result?.get("cancelled") as? JsonPrimitive)?.booleanOrNull == true
    }

    override suspend fun runNow(jobId: String): RunOutcome {
        val job = dashboard.call("POST", "/api/cron/jobs/${encode(jobId)}/trigger", slow = true) as? JsonObject
        fun str(key: String) = (job?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return RunOutcome(str("last_status"), str("last_error"))
    }

    private fun encode(id: String) = URLEncoder.encode(id, "UTF-8").replace("+", "%20")
}

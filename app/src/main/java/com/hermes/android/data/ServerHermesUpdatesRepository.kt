package com.hermes.android.data

import com.hermes.android.domain.updates.ServerHermesUpdates
import com.hermes.android.domain.updates.ServerHermesUpdates.Check
import com.hermes.android.domain.updates.ServerHermesUpdates.Start
import com.hermes.android.domain.updates.ServerHermesUpdates.Status
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The dashboard's update routes (`hermes_cli/web_routers/actions.py`):
 * `GET /api/hermes/update/check`, `POST /api/hermes/update` (spawns `hermes update`, or answers
 * `ok: false` with the reason when this install can't update itself, e.g. Termux APT or
 * Docker), and `GET /api/actions/hermes-update/status`, whose `receipt` is the durable
 * outcome that survives the dashboard restarting during the update.
 */
@Singleton
class ServerHermesUpdatesRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : ServerHermesUpdates {

    override suspend fun check(): Check {
        val o = dashboard.call("GET", "/api/hermes/update/check?force=true", slow = true) as? JsonObject ?: JsonObject(emptyMap())
        return Check(
            currentVersion = o.str("current_version"),
            updateAvailable = o.bool("update_available") == true,
            behind = (o["behind"] as? JsonPrimitive)?.intOrNull,
            canApply = o.bool("can_apply") == true,
            message = o.str("message"),
            updateCommand = o.str("update_command"),
        )
    }

    override suspend fun start(): Start {
        val o = dashboard.call("POST", "/api/hermes/update") as? JsonObject ?: JsonObject(emptyMap())
        if (o.bool("ok") == false) {
            val reason = listOfNotNull(o.str("message") ?: o.str("error"), o.str("update_command")).joinToString("\n")
            return Start.Refused(reason.ifBlank { "the server refused the update" })
        }
        return Start.Started(o.str("action_id"))
    }

    override suspend fun status(actionId: String?): Status {
        val o = dashboard.call("GET", "/api/actions/hermes-update/status?lines=40") as? JsonObject ?: return Status.Running(null)
        val lastLine = (o["lines"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            ?.lastOrNull { it.isNotEmpty() && !it.startsWith("===") }
        if (o.bool("running") == true) return Status.Running(lastLine)
        val receipt = (o["receipt"] as? JsonObject)?.takeIf { actionId == null || it.str("action_id") == actionId }
        val outcome = receipt?.str("outcome")
        val exitCode = (o["exit_code"] as? JsonPrimitive)?.intOrNull
        return when {
            outcome == "success" || (exitCode == 0 && outcome != "running") -> Status.Succeeded(receipt?.str("post_version"))
            outcome == "running" || (exitCode == null && outcome == null) -> Status.Running(lastLine)
            else -> Status.Failed(lastLine ?: outcome ?: "exit code $exitCode")
        }
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

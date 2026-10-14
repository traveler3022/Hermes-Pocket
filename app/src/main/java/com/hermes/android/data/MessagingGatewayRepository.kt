package com.hermes.android.data

import com.hermes.android.domain.messaging.GatewayActionResult
import com.hermes.android.domain.messaging.GatewayStatus
import com.hermes.android.domain.messaging.MessagingGateway
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The dashboard's gateway lifecycle routes, as its own sidebar uses them: state from
 * `GET /api/status`, `POST /api/gateway/start|stop|restart` (hermes_cli/web_routers/status.py,
 * ops.py, actions.py). Each spawns `hermes gateway <verb>` in the background and answers at
 * once, so a refused verb only shows as a state that doesn't change.
 */
@Singleton
class MessagingGatewayRepository @Inject constructor(
    private val dashboard: DashboardApi,
    private val runtime: HermesRuntime,
) : MessagingGateway {

    override val available: Boolean get() = runtime.type != RuntimeType.PROOT_LINUX

    override suspend fun status(): GatewayStatus = parseGatewayStatus(dashboard.call("GET", "/api/status"))

    override suspend fun start(): GatewayActionResult = act {
        // `hermes gateway start` only asks a service manager (systemd, launchd…). With none, as
        // on Termux or a plain Linux login, it exits 1 into a log nobody reads. `restart` falls
        // back to running the gateway itself (hermes_cli/gateway.py _cmd_start, _cmd_restart).
        val started = runtime.type != RuntimeType.TERMUX && run {
            dashboard.call("POST", "/api/gateway/start")
            runningWithin(START_WAIT_MS)
        }
        if (!started) dashboard.call("POST", "/api/gateway/restart")
    }

    override suspend fun stop(): GatewayActionResult = act { dashboard.call("POST", "/api/gateway/stop") }

    override suspend fun restart(): GatewayActionResult = act { dashboard.call("POST", "/api/gateway/restart") }

    /** A refusal (409: a profile served by the shared gateway) comes back as IOException with Hermes' reason. */
    private suspend fun act(call: suspend () -> Unit): GatewayActionResult = try {
        call()
        GatewayActionResult.Sent
    } catch (e: IOException) {
        GatewayActionResult.Failed(e.message ?: "Hermes refused")
    }

    private suspend fun runningWithin(ms: Long): Boolean {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) {
            delay(POLL_MS)
            val running = try {
                status().running
            } catch (_: IOException) {
                false
            }
            if (running) return true
        }
        return false
    }

    private companion object {
        const val START_WAIT_MS = 8_000L
        const val POLL_MS = 1_500L
    }
}

/** `GET /api/status`: `gateway_running`, `gateway_state`, `gateway_platforms: {name: {state, error_message}}`… */
internal fun parseGatewayStatus(result: JsonElement): GatewayStatus {
    val root = result as? JsonObject ?: JsonObject(emptyMap())
    val platforms = (root["gateway_platforms"] as? JsonObject).orEmpty().map { (name, value) ->
        val entry = value as? JsonObject
        GatewayStatus.PlatformState(
            name = name,
            state = entry?.text("state"),
            error = entry?.text("error_message"),
        )
    }
    return GatewayStatus(
        running = (root["gateway_running"] as? JsonPrimitive)?.booleanOrNull == true,
        state = root.text("gateway_state"),
        exitReason = root.text("gateway_exit_reason"),
        platforms = platforms,
        sharedWith = (root["gateway_shared_with"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) },
    )
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

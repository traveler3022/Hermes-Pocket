package com.hermes.android.domain.messaging

/**
 * The messaging gateway (`hermes gateway`): the process that runs the user's Telegram,
 * Discord and Slack bots. Not the app's own connection to Hermes, which keeps working
 * while this one is stopped.
 */
interface MessagingGateway {
    /** False on the built-in Linux: it runs no messaging gateway. */
    val available: Boolean

    suspend fun status(): GatewayStatus

    suspend fun start(): GatewayActionResult

    suspend fun stop(): GatewayActionResult

    suspend fun restart(): GatewayActionResult
}

data class GatewayStatus(
    val running: Boolean,
    /** Hermes' word for it (running, starting, draining, degraded, startup_failed, stopped); null when it says none. */
    val state: String?,
    /** Why it last exited, when it did on its own. */
    val exitReason: String?,
    val platforms: List<PlatformState>,
    /** Every profile one shared gateway process serves (a restart takes them all down for a moment); empty otherwise. */
    val sharedWith: List<String>,
) {
    data class PlatformState(val name: String, val state: String?, val error: String?)
}

sealed interface GatewayActionResult {
    /** Hermes took the request; the gateway changes state over the next seconds. */
    data object Sent : GatewayActionResult
    data class Failed(val message: String) : GatewayActionResult
}

package com.hermes.android.domain.messaging

/**
 * Hermes' webhook routes: an outside service (GitHub, GitLab, a script) POSTs a signed event
 * to `<base>/webhooks/<name>`, and the agent runs the route's prompt on it, then delivers the
 * answer to a messaging platform or only logs it. The routes are served by the messaging
 * gateway's `webhook` platform, which must be switched on first.
 */
interface Webhooks {
    /** False on the built-in Linux: it runs no messaging gateway, so nothing can receive a webhook. */
    val available: Boolean

    suspend fun list(): WebhookList

    /** Switches the `webhook` platform on and restarts the messaging gateway. */
    suspend fun enablePlatform(): EnableResult

    /** The answer holds the route's secret, which the server shows only this once. */
    suspend fun create(draft: Draft): Created

    suspend fun delete(name: String)

    /** A switched-off route stays saved; the gateway answers its events with 403. */
    suspend fun setEnabled(name: String, enabled: Boolean)

    data class WebhookList(
        val platformEnabled: Boolean,
        val baseUrl: String,
        val routes: List<Route>,
    )

    data class Route(
        val name: String,
        val description: String,
        /** Empty means every event is accepted. */
        val events: List<String>,
        /** A platform name (telegram, discord, …) or `log`. */
        val deliver: String,
        /** Forwards the rendered prompt as is, without running the agent. */
        val deliverOnly: Boolean,
        val prompt: String,
        val url: String,
        val enabled: Boolean,
    )

    data class Draft(
        val name: String,
        val description: String,
        val events: List<String>,
        val prompt: String,
        val deliver: String,
        val deliverOnly: Boolean,
        val deliverChatId: String,
    )

    data class Created(val route: Route, val secret: String)

    /** [needsRestart]: the gateway could not be restarted, so the user has to do it. */
    data class EnableResult(val needsRestart: Boolean)
}

package com.hermes.android.domain.portal

/**
 * The Nous Portal account Hermes is signed in to (Settings › Providers does the sign-in), and the
 * apps that account has connected ("connectors": Gmail, GitHub and so on, hosted by Nous; these
 * are not MCP servers).
 */
interface NousPortal {

    /** What Hermes knows about the Portal account, read from disk on the server. */
    suspend fun status(): PortalStatus

    /** The apps the account can connect, each with its connection state. */
    suspend fun connectors(): ConnectorList

    /** The accounts already connected, across all apps. */
    suspend fun accounts(): List<ConnectorAccount>

    /** Starts connecting [slug]; the answer carries the page to open in the browser. */
    suspend fun connect(slug: String, reconnect: Boolean): ConnectorOperation

    /** Where the operation [opId] stands now. */
    suspend fun operation(opId: String): ConnectorOperation

    /** Tells Hermes the browser came back, so it reads the result now. */
    suspend fun wake(opId: String)

    /** Disconnects one connected account. */
    suspend fun removeAccount(connectionId: String)
}

data class PortalStatus(
    val loggedIn: Boolean,
    val freeTier: Boolean,
    val accountTier: String?,
    /** The model provider Hermes uses now. */
    val provider: String,
    val portalUrl: String?,
    val subscriptionUrl: String?,
    val features: List<PortalFeature>,
)

/** One Portal-backed capability (web search, image generation, …) and who serves it. */
data class PortalFeature(val label: String, val state: String)

data class ConnectorList(
    /** False when this Hermes has connectors switched off. */
    val available: Boolean,
    val connectors: List<Connector>,
)

data class Connector(
    val slug: String,
    val name: String,
    val description: String,
    val category: String,
    val enabled: Boolean,
    val connected: Boolean,
    /** `pending`, `active`, `failed`, `expired`, `revoked`, `inactive`, or null. */
    val status: String?,
)

data class ConnectorAccount(
    val connectionId: String,
    val connector: String,
    val label: String,
    val alias: String?,
    val status: String,
    val active: Boolean,
)

data class ConnectorOperation(
    val opId: String,
    val settled: Boolean,
    /** Seconds since the epoch; the server gives up then. */
    val deadlineAt: Double,
    /** The page that asks the user to allow the connection, when one is waiting. */
    val connectUrl: String?,
    /** The server's words for a target that failed. */
    val failure: String?,
)

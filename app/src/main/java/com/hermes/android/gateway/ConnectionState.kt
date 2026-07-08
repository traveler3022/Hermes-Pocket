package com.hermes.android.gateway

/**
 * Connection state of the [GatewayClient].
 *
 * State machine:
 * ```
 * Disconnected ──connect()──► Connecting ──┬──► Connected ──disconnect()──► Disconnected
 *                                          │                  │
 *                                          │                  └──(network drop)──► Reconnecting
 *                                          │                                          │
 *                                          └──(connect failed)──► Reconnecting ───┘
 *                                                                                      │
 *                                                                                      └──(max retries)──► Disconnected
 * ```
 *
 * UI observes [GatewayClient.connectionState] and renders accordingly.
 *
 * Reference: Phase 1.5 Rule 1 (Strict Layer Dependency) — this is a Domain
 * type, no infrastructure imports.
 */
sealed class ConnectionState {

    /** Not connected, not trying. */
    object Disconnected : ConnectionState()

    /** Initial connection attempt in progress. */
    object Connecting : ConnectionState()

    /** WebSocket is open; `gateway.ready` event received. */
    data class Connected(val sessionId: String?) : ConnectionState()

    /** Connection lost; exponential backoff reconnection in progress. */
    data class Reconnecting(
        val attempt: Int,
        val nextAttemptInMs: Long,
        val lastError: String?,
    ) : ConnectionState()

    /** Permanently failed — max retries exceeded or user cancelled. */
    data class Failed(val reason: String) : ConnectionState()
}

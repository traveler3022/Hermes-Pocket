package com.hermes.android.gateway

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Client for the Hermes tui_gateway JSON-RPC server.
 *
 * ## Purpose
 * Provides a single, implementation-agnostic contract for the Android
 * ViewModel layer to talk to the Hermes backend over WebSocket.
 *
 * ## Wire protocol
 * - WebSocket URL: `ws://127.0.0.1:9119/api/ws?token=<session_token>` (default)
 * - Framing: newline-delimited JSON-RPC 2.0
 * - Server emits `gateway.ready` event on connect
 * - Client sends RPC requests; server responds with same `id`
 * - Server emits events (message.delta, tool.start, etc.) as the agent runs
 *
 * ## Implementations
 * - [com.hermes.android.gateway.OkHttpGatewayClient] — production (OkHttp WebSocket)
 * - (test) `FakeGatewayClient` — for unit tests
 *
 * ## Phase 1.5 compliance
 * - UI depends only on ViewModel
 * - ViewModel depends only on this interface (no OkHttp imports)
 * - Only `di/GatewayModule.kt` knows about the concrete implementation
 * - Swap test: replacing `OkHttpGatewayClient` with a fake must not affect
 *   any other file
 *
 * Reference: `tui_gateway/ws.py` (wire protocol), `ui-tui/src/gatewayClient.ts` (TS reference)
 */
interface GatewayClient {

    /**
     * Current connection state, observable for UI updates.
     * Updated by the implementation as connect/reconnect/disconnect progresses.
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Hot stream of events received from the gateway.
     * Subscribers receive every event while subscribed; late subscribers
     * do NOT receive past events (use a replay buffer in the UI if needed).
     */
    val events: SharedFlow<GatewayEvent>

    /**
     * Connect to the gateway at [url].
     *
     * Suspends until the connection is established and `gateway.ready`
     * event is received, or until [connectTimeoutMs] elapses.
     *
     * Idempotent — calling while already connected returns immediately.
     *
     * @param url WebSocket URL (default: `ws://127.0.0.1:9119/api/ws?token=<session_token>`)
     * @param connectTimeoutMs max time to wait for `gateway.ready`
     * @return the connection state after connect attempt
     */
    suspend fun connect(
        url: String = DEFAULT_URL,
        connectTimeoutMs: Long = 15_000,
    ): ConnectionState

    /**
     * Disconnect from the gateway. Cancels any pending reconnection attempts.
     */
    suspend fun disconnect()

    /**
     * Send an RPC request and await the response.
     *
     * @param method RPC method name (see [GatewayMethods])
     * @param params request parameters
     * @param timeoutMs max time to wait for response
     * @return the response result on success
     * @throws GatewayException on error response or timeout
     */
    suspend fun request(
        method: String,
        params: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
        timeoutMs: Long = 120_000,
    ): kotlinx.serialization.json.JsonElement

    /**
     * Send an RPC request without waiting for a response (fire-and-forget).
     * Useful for events where the server doesn't reply (e.g. terminal.resize).
     */
    suspend fun notify(
        method: String,
        params: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    )

    companion object {
        /**
         * Default WebSocket URL — Hermes dashboard server (running in Termux).
         *
         * Per gateway-bind-audit.md (2026-06-27):
         * - Host: 127.0.0.1 (shared loopback between Android apps and Termux)
         * - Port: 9119 (Hermes' dashboard default, see
         *   hermes_cli/subcommands/dashboard.py:26)
         * - Path: /api/ws (FastAPI WebSocket endpoint, web_server.py:11655)
         * - The ?token=<value> query param is appended by TermuxBridge's
         *   getWebSocketUrl(); callers should generally use that method
         *   rather than this constant directly.
         */
        const val DEFAULT_URL = "ws://127.0.0.1:9119/api/ws"
    }
}

/**
 * Thrown when an RPC request fails (error response, timeout, or not connected).
 */
class GatewayException(message: String, cause: Throwable? = null) : Exception(message, cause)

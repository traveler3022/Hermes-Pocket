package com.hermes.android.gateway

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Server → Client JSON-RPC response.
 *
 * Exactly one of [result] / [error] is non-null.
 *
 * Reference: `tui_gateway/server.py:956` (`_ok()`), `tui_gateway/server.py:960` (`_err()`).
 */
@Serializable
data class GatewayResponse(
    val jsonrpc: String = "2.0",
    val id: Long,
    val result: JsonElement? = null,
    val error: GatewayError? = null,
)

@Serializable
data class GatewayError(
    val code: Int,
    val message: String,
)

/**
 * Standard JSON-RPC error codes.
 *
 * Reference: JSON-RPC 2.0 spec + `tui_gateway/server.py` usage.
 */
object GatewayErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
}

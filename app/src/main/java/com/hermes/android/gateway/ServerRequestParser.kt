package com.hermes.android.gateway

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Server→client request frames (`tui_gateway/server_requests.py`, contracts in
 * `tui_gateway/contracts/server_requests.py`) as events. The agent asks the
 * user something with `{"id": "srq-…", "method", "params": {session_id, …}}`
 * and blocks until a response frame with that id comes back — there is no
 * `*.request` event or `*.respond` method any more.
 */
internal object ServerRequestParser {

    /** Null when this app has no card for [method] — the caller declines it. */
    fun parse(id: String, method: String, params: JsonObject): GatewayEvent? {
        val sid = params.text("session_id")?.takeIf { it.isNotBlank() }
        return when (method) {
            "clarify" -> GatewayEvent.ClarifyRequest(
                sessionId = sid,
                requestId = id,
                question = params.text("question").orEmpty(),
                choices = params["choices"].strings(),
                multiSelect = params.flag("multi_select"),
                questions = (params["questions"] as? JsonArray).orEmpty().mapNotNull { entry ->
                    val q = entry as? JsonObject ?: return@mapNotNull null
                    GatewayEvent.ClarifyQuestion(
                        qid = q.text("qid") ?: return@mapNotNull null,
                        question = q.text("question").orEmpty(),
                        choices = q["choices"].strings(),
                        multiSelect = q.flag("multi_select"),
                    )
                },
            )
            "approval" -> {
                // The server lists what it accepts: a smart-denied command only
                // once/deny, one that may not be stored permanently no "always".
                // The sheet offered deny/once/always whatever it said, and never
                // "session" (allow for the rest of this chat).
                val choices = params["choices"].strings() ?: when {
                    params.flag("smart_denied") -> listOf("once", "deny")
                    params.text("allow_permanent") == "false" -> listOf("once", "session", "deny")
                    else -> APPROVAL_CHOICES
                }
                GatewayEvent.ApprovalRequest(
                    sessionId = sid,
                    command = params.text("command").orEmpty(),
                    description = params.text("description").orEmpty(),
                    patternKeys = params["pattern_keys"].strings().orEmpty(),
                    allowPermanent = "always" in choices,
                    requestId = params.text("request_id").orEmpty(),
                    serverRequestId = id,
                    choices = choices,
                )
            }
            "sudo" -> GatewayEvent.SudoRequest(sid, id)
            "secret" -> GatewayEvent.SecretRequest(
                sid,
                id,
                params.text("env_var").orEmpty(),
                params.text("prompt").orEmpty(),
            )
            else -> null
        }
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

    private fun JsonObject.flag(key: String): Boolean = text(key) == "true"

    private fun JsonElement?.strings(): List<String>? =
        (this as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.takeIf { s -> s.isNotBlank() } }
            ?.takeIf { it.isNotEmpty() }
}

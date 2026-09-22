package com.hermes.android.gateway

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Helper functions for parsing gateway event payloads.
 * Extracted from OkHttpGatewayClient for better separation of concerns.
 */
internal object GatewayEventHelpers {

    /**
     * Whether a `session.active_list` row's `status` means a turn is in flight.
     * The gateway reports `working`, `waiting` (blocked on an approval or a
     * question), `starting` or `idle` — never `streaming`, which is what this
     * used to test for, so every busy session read as finished. `streaming`
     * and `running` stay accepted for any build that does send them.
     */
    fun isBusySessionStatus(status: String): Boolean = status in BUSY_SESSION_STATUSES

    private val BUSY_SESSION_STATUSES = setOf("working", "waiting", "streaming", "running")

    /**
     * Whether a `session.info` payload says the session is not running. The
     * gateway sends one right after it clears `running` at the end of a turn,
     * which is later than message.complete, so it is the first frame that
     * proves a turn is over to a client that missed or outran the completion.
     */
    /**
     * Side-job failures (title generation, compression summary) that do not touch the
     * reply. The server keeps these out of its own chat surfaces (`_TELEGRAM_NOISY_STATUS_RE`
     * in gateway/run.py); this is the auxiliary part of that list.
     */
    private val AUXILIARY_NOISE = Regex(
        "auxiliary\\s+.+\\s+failed" +
            "|compression\\s+summary\\s+failed" +
            "|fallback\\s+context\\s+marker" +
            "|configured\\s+compression\\s+model\\s+.+\\s+failed" +
            "|no\\s+auxiliary\\s+llm\\s+provider\\s+configured",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun isAuxiliaryNoise(text: String): Boolean = AUXILIARY_NOISE.containsMatchIn(text)

    fun isSettledSessionInfo(info: Map<String, JsonElement>): Boolean =
        (info["running"] as? JsonPrimitive)?.content == "false"

    fun parseSkinMap(element: JsonElement): Map<String, String> {
        return try {
            element.jsonObject.toMap().mapValues { it.value.jsonPrimitive.content }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Mirrors `parseTodos` in `ui-tui/src/app/turnController.ts`: drop items
     * without a known status or with empty id/content instead of failing the
     * whole event.
     */
    fun parseTodos(element: JsonElement): List<GatewayEvent.TodoItem>? {
        val array = element as? JsonArray ?: return null
        val validStatuses = setOf("pending", "in_progress", "completed", "cancelled")
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val status = obj["status"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (status !in validStatuses) return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.content?.trim().orEmpty()
            val content = obj["content"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (id.isEmpty() || content.isEmpty()) return@mapNotNull null
            GatewayEvent.TodoItem(id = id, content = content, status = status)
        }
    }

    fun parseStringList(element: JsonElement): List<String>? {
        return try {
            val array = when (element) {
                is JsonArray -> element
                is JsonObject -> element["choices"] as? JsonArray
                    ?: element["pattern_keys"] as? JsonArray
                else -> null
            }
            array?.map { it.jsonPrimitive.content }
        } catch (e: Exception) {
            null
        }
    }
}

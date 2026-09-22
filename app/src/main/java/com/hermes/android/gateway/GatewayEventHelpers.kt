package com.hermes.android.gateway

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

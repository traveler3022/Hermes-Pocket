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

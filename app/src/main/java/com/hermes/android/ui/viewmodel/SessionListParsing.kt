package com.hermes.android.ui.viewmodel

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/**
 * `session.list` → the rows the Sessions screen shows.
 *
 * Pulled out of the ViewModel so the row-shaping rules are testable on the
 * JVM. Two of them are not cosmetic:
 *
 * - **A row without an id is dropped.** The list is a LazyColumn keyed by
 *   session id, and Compose treats a repeated key as fatal — two id-less rows
 *   both defaulting to `""` took the whole screen down. An id-less row is
 *   also unusable: open, rename and delete all address a session by id.
 * - **A blank title is "Untitled".** `?:` only replaces a missing field; the
 *   server sends `""` for a session it has not titled yet, which rendered as
 *   an empty row.
 *
 * [now] is injected so tests are not at the mercy of the clock.
 */
internal fun parseSessionListPayload(
    result: JsonElement,
    now: Long = System.currentTimeMillis(),
): List<SessionSummary> {
    return try {
        val obj = result as? JsonObject ?: return emptyList()
        val arr = obj["sessions"] as? JsonArray ?: return emptyList()
        arr.mapNotNull { item ->
            val s = item as? JsonObject ?: return@mapNotNull null
            val id = s.text("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SessionSummary(
                id = id,
                title = s.text("title")?.takeIf { it.isNotBlank() } ?: "Untitled",
                // Fix S9F01: field is "preview" not "last_message"
                lastMessagePreview = s.text("preview"),
                // Fix S9F01: field is "started_at" not "updated_at"
                updatedAt = (s["started_at"] ?: s["updated_at"])
                    ?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() }
                    ?.let(::normalizeEpochMillis) ?: now,
                messageCount = s.text("message_count")?.toIntOrNull() ?: 0,
            )
        }
    } catch (e: Exception) {
        Timber.w(e, "[Sessions] Parse error")
        emptyList()
    }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content

package com.hermes.android.ui.viewmodel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Unsent composer text, one per chat. Before this the app kept a single draft,
 * so text typed in one chat showed up in whichever chat was opened next.
 *
 * Keyed by the chat's stored id when it has one, else its live id; a null key
 * is the new chat whose session doesn't exist yet. A blank text removes the
 * entry. [read] and [write] hold the whole map as one JSON string, so the
 * drafts survive the app being closed. The oldest entries go past [MAX].
 */
internal class ChatDrafts(
    read: () -> String?,
    private val write: (String) -> Unit,
) {
    private val drafts = LinkedHashMap<String, String>().apply { putAll(decode(read())) }

    fun get(key: String?): String = drafts[key.orEmpty()].orEmpty()

    fun put(key: String?, text: String) {
        val k = key.orEmpty()
        val had = drafts.remove(k)
        if (text.isNotBlank()) drafts[k] = text
        while (drafts.size > MAX) drafts.remove(drafts.keys.first())
        if (had != drafts[k]) write(encode(drafts))
    }

    companion object {
        const val MAX = 50

        fun encode(map: Map<String, String>): String =
            JsonObject(map.mapValues { JsonPrimitive(it.value) }).toString()

        fun decode(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                Json.parseToJsonElement(raw).jsonObject.mapNotNull { (k, v) ->
                    runCatching { v.jsonPrimitive.contentOrNull }.getOrNull()?.let { k to it }
                }.toMap()
            }.getOrDefault(emptyMap())
        }
    }
}

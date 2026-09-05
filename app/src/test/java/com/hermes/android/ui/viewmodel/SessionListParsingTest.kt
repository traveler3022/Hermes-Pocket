package com.hermes.android.ui.viewmodel

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionListParsingTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `rows without an id are dropped`() {
        val payload = buildJsonObject {
            put("sessions", buildJsonArray {
                add(buildJsonObject { put("id", "s1"); put("title", "one") })
                add(buildJsonObject { put("title", "no id at all") })
                add(buildJsonObject { put("id", "  "); put("title", "blank id") })
            })
        }

        val rows = parseSessionListPayload(payload, now)

        // Two id-less rows would both key on "" and take the LazyColumn down.
        assertEquals(listOf("s1"), rows.map { it.id })
    }

    @Test
    fun `a blank title falls back to Untitled`() {
        val payload = buildJsonObject {
            put("sessions", buildJsonArray {
                add(buildJsonObject { put("id", "s1"); put("title", "") })
                add(buildJsonObject { put("id", "s2") })
            })
        }

        val rows = parseSessionListPayload(payload, now)

        assertEquals(listOf("Untitled", "Untitled"), rows.map { it.title })
    }

    @Test
    fun `epoch seconds are normalized to millis and the preview is carried`() {
        val payload = buildJsonObject {
            put("sessions", buildJsonArray {
                add(buildJsonObject {
                    put("id", "s1")
                    put("title", "chat")
                    put("preview", "hello there")
                    put("started_at", 1_700_000_000)
                    put("message_count", 7)
                })
            })
        }

        val row = parseSessionListPayload(payload, now).single()

        assertEquals(1_700_000_000_000L, row.updatedAt)
        assertEquals("hello there", row.lastMessagePreview)
        assertEquals(7, row.messageCount)
    }

    @Test
    fun `a payload with no sessions array is empty, not a crash`() {
        assertTrue(parseSessionListPayload(JsonObject(emptyMap()), now).isEmpty())
        assertTrue(parseSessionListPayload(buildJsonObject { put("sessions", "nope") }, now).isEmpty())
    }
}

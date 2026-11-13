package com.hermes.android.data

import com.hermes.android.domain.topics.ChatTopic
import com.hermes.android.domain.topics.movedPast
import com.hermes.android.domain.topics.newTopicId
import com.hermes.android.domain.topics.ordered
import com.hermes.android.domain.topics.pointedAt
import com.hermes.android.domain.topics.renamed
import com.hermes.android.domain.topics.withTopic
import com.hermes.android.domain.topics.withoutTopic
import com.hermes.android.ui.screen.AgeUnit
import com.hermes.android.ui.screen.ageOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicsTest {

    private val a = ChatTopic("a", "Standup", "s1")
    private val b = ChatTopic("b", "Trip", "s2", cwd = "/home/u/trip")
    private val c = ChatTopic("c", "Build", "s3")

    @Test
    fun `meta round-trips and keeps the block's other keys`() {
        val block = buildJsonObject { put("other", "kept") }
        val written = TopicMeta.write(listOf(a, b), block)
        assertEquals(JsonPrimitive("kept"), written["other"])
        assertEquals(listOf(a, b), TopicMeta.parse(written))
    }

    @Test
    fun `entries missing a field or repeating an id are skipped`() {
        val block = buildJsonObject {
            put(
                "topics",
                buildJsonArray {
                    add(buildJsonObject { put("id", "a"); put("name", "Standup"); put("sessionId", "s1") })
                    add(buildJsonObject { put("id", "x"); put("name", "No chat") })
                    add(buildJsonObject { put("id", "a"); put("name", "Again"); put("sessionId", "s9") })
                    add(buildJsonObject { put("id", " "); put("name", "Blank id"); put("sessionId", "s8") })
                },
            )
        }
        assertEquals(listOf(a), TopicMeta.parse(block))
        assertEquals(emptyList<ChatTopic>(), TopicMeta.parse(null))
        assertEquals(emptyList<ChatTopic>(), TopicMeta.parse(JsonObject(emptyMap())))
    }

    @Test
    fun `a chat is a topic once`() {
        assertEquals(listOf(a), listOf(a).withTopic(ChatTopic("z", "Same chat", "s1")))
        assertEquals(listOf(a, b), listOf(a).withTopic(b))
    }

    @Test
    fun `rename, point, remove`() {
        assertEquals("Daily", listOf(a).renamed("a", "  Daily ").single().name)
        assertEquals(listOf(a), listOf(a).renamed("a", "   "))
        assertEquals("s7", listOf(a, b).pointedAt("b", "s7")[1].sessionId)
        assertEquals(listOf(b), listOf(a, b).withoutTopic("a"))
    }

    @Test
    fun `moving past a neighbour swaps the two`() {
        assertEquals(listOf(b, a, c), listOf(a, b, c).movedPast("b", "a"))
        assertEquals(listOf(a, c, b), listOf(a, b, c).movedPast("b", "c"))
        assertEquals(listOf(a, b, c), listOf(a, b, c).movedPast("nope", "a"))
    }

    @Test
    fun `a dragged order keeps topics it leaves out in place`() {
        assertEquals(listOf(c, b, a), listOf(a, b, c).ordered(listOf("c", "b", "a")))
        // b was hidden from the drag: it stays second.
        assertEquals(listOf(c, b, a), listOf(a, b, c).ordered(listOf("c", "a")))
    }

    @Test
    fun `new ids are short and plain`() {
        val id = newTopicId()
        assertEquals(12, id.length)
        assertTrue(id.all { it.isLowerCase() || it.isDigit() })
    }

    @Test
    fun `ages bucket as the rows show them`() {
        val now = 1_000_000_000_000L
        assertEquals(0L to AgeUnit.Now, ageOf(now - 30_000, now))
        assertEquals(5L to AgeUnit.Minute, ageOf(now - 5 * 60_000, now))
        assertEquals(3L to AgeUnit.Hour, ageOf(now - 3 * 3_600_000, now))
        assertEquals(2L to AgeUnit.Day, ageOf(now - 2 * 86_400_000L, now))
        assertEquals(null, ageOf(0, now))
    }
}

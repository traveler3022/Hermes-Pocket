package com.hermes.android.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * tool.complete carries structured results for tools like browser_exec and
 * web_search. Reading those as strings threw, and the event was dropped.
 */
class JsonAsTextTest {

    @Test
    fun `strings and numbers read as themselves`() {
        assertEquals("ok", JsonPrimitive("ok").asText())
        assertEquals("1.5", JsonPrimitive(1.5).asText())
    }

    @Test
    fun `objects and arrays read as their json`() {
        val obj = Json.parseToJsonElement("""{"output":"hi","exit_code":0}""")
        assertEquals("""{"output":"hi","exit_code":0}""", obj.asText())
        assertEquals("[1,2]", Json.parseToJsonElement("[1,2]").asText())
    }

    @Test
    fun `null and absent read as null`() {
        assertNull(JsonNull.asText())
        assertNull(null.asText())
    }
}

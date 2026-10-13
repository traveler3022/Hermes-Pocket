package com.hermes.android.data

import com.hermes.android.domain.cron.CronBlueprints.FieldType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CronBlueprintsTest {

    // Trimmed from GET /api/cron/blueprints on Hermes 0.21.7.
    private val answer = Json.parseToJsonElement(
        """
        {"blueprints": [
          {"key": "important-mail", "title": "Important-mail monitor", "description": "Check your inbox",
           "category": "email", "tags": ["email"], "scheduleHuman": "every 30 minutes",
           "fields": [
             {"name": "interval_min", "type": "enum", "label": "How often?", "default": "30",
              "options": ["15", "30", "60"], "optional": false, "strict": true, "help": "minutes between checks"},
             {"name": "deliver", "type": "enum", "label": "Where to deliver?", "default": "origin",
              "options": ["origin", "local"], "optional": false, "strict": false, "help": ""},
             {"name": "time", "type": "time", "label": "What time?", "default": null, "options": []},
             {"name": "recurrence", "type": "weekdays", "label": "Repeat on", "default": "everyday",
              "options": ["everyday", "weekdays", "weekends"]},
             {"name": "odd", "type": "slider", "label": "", "default": 5}
           ]},
          {"title": "no key, dropped"},
          {"key": "bare"}
        ]}
        """,
    )

    @Test
    fun `parses rows and fields`() {
        val list = parseBlueprints(answer)
        assertEquals(listOf("important-mail", "bare"), list.map { it.key })
        val mail = list[0]
        assertEquals("every 30 minutes", mail.scheduleHuman)
        assertEquals(listOf(FieldType.ENUM, FieldType.ENUM, FieldType.TIME, FieldType.WEEKDAYS, FieldType.TEXT), mail.fields.map { it.type })
        assertEquals(listOf("15", "30", "60"), mail.fields[0].options)
        assertTrue(mail.fields[0].strict)
        assertFalse(mail.fields[1].strict)
        // A null default is blank, a number is its text, a missing label is the name.
        assertEquals("", mail.fields[2].default)
        assertEquals("5", mail.fields[4].default)
        assertEquals("odd", mail.fields[4].label)
    }

    @Test
    fun `a row without title or category gets fallbacks`() {
        val bare = parseBlueprints(answer).last()
        assertEquals("bare", bare.title)
        assertEquals("general", bare.category)
        assertTrue(bare.fields.isEmpty())
    }

    @Test
    fun `anything else parses to an empty list`() {
        assertTrue(parseBlueprints(Json.parseToJsonElement("""{"detail": "Not Found"}""")).isEmpty())
        assertTrue(parseBlueprints(Json.parseToJsonElement("[]")).isEmpty())
    }
}

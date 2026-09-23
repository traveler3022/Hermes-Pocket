package com.hermes.android.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class SlashCommandDescriptionsTest {

    @Test
    fun `english stays hermes' own text`() {
        val text = "Set a standing goal Hermes works on across turns until achieved"
        assertEquals(text, SlashCommandDescriptions.describe("/goal", text, persian = false))
    }

    @Test
    fun `persian keeps hermes' usage hint`() {
        val described = SlashCommandDescriptions.describe(
            "/plan", "Write a markdown plan (usage: /plan [request])", persian = true,
        )
        assert(described.endsWith("(استفاده: /plan [request])")) { described }
        assert(!described.contains("Write a markdown")) { described }
    }

    @Test
    fun `a command the app has no translation for shows hermes' text`() {
        val text = "A command added in a later Hermes"
        assertEquals(text, SlashCommandDescriptions.describe("/brand-new", text, persian = true))
    }

    @Test
    fun `lookup ignores case`() {
        assert(SlashCommandDescriptions.describe("/GOAL", "x", persian = true) != "x")
    }
}

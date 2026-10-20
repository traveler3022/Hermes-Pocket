package com.hermes.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** A theme saved before the two families still opens on the closest one. */
class ColorThemeMigrationTest {

    @Test
    fun `current keys round-trip`() {
        ColorTheme.entries.forEach { assertEquals(it, ColorTheme.fromKey(it.key)) }
    }

    @Test
    fun `retired palettes move to the closest family`() {
        listOf("hermes", "blue_eye", "claude", "aether").forEach { assertEquals(it, ColorTheme.LAJVARD, ColorTheme.fromKey(it)) }
        listOf("midnight", "indigo_pro", "carbon").forEach { assertEquals(it, ColorTheme.CARBON, ColorTheme.fromKey(it)) }
    }

    @Test
    fun `an unknown key falls back to Carbon`() {
        assertEquals(ColorTheme.CARBON, ColorTheme.fromKey("no-such-theme"))
    }
}

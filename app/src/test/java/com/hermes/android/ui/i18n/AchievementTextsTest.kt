package com.hermes.android.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementTextsTest {

    @Test
    fun englishIsLeftAsHermesWroteIt() {
        assertEquals("Let Him Cook", AchievementTexts.name("let_him_cook", "Let Him Cook", persian = false))
        assertEquals("Requirement: x.", AchievementTexts.criteria("Requirement: x.", persian = false))
    }

    @Test
    fun anUnknownAchievementKeepsHermesText() {
        assertEquals("New One", AchievementTexts.name("brand_new", "New One", persian = true))
        assertEquals("Does a thing.", AchievementTexts.description("brand_new", "Does a thing.", persian = true))
    }

    @Test
    fun aTierLadderKeepsHermesNumbers() {
        val fa = AchievementTexts.criteria(
            "Requirement: tool calls in one session. Tier ladder: Copper 200, Silver 500, Olympian 8000.",
            persian = true,
        )
        assertEquals("شرط: فراخوانی ابزار در یک گفتگو. پلکان سطح‌ها: مس 200، نقره 500، المپیایی 8000.", fa)
    }

    @Test
    fun aMultiConditionLineIsTranslatedPartByPart() {
        val fa = AchievementTexts.criteria(
            "Requirement: git workflow commands ≥ 50; tool calls in one session ≥ 500.",
            persian = true,
        )
        assertEquals("شرط: دستورهای git ≥ 50؛ فراخوانی ابزار در یک گفتگو ≥ 500.", fa)
    }

    @Test
    fun aMetricWeDontKnowLeavesTheWholeLineInEnglish() {
        val line = "Requirement: some new metric. Tier ladder: Copper 1, Silver 2."
        assertEquals(line, AchievementTexts.criteria(line, persian = true))
    }

    @Test
    fun theSecretLineIsTranslated() {
        assertTrue(AchievementTexts.criteria("Secret: exact requirement hidden until x", persian = true).startsWith("مخفی"))
    }
}

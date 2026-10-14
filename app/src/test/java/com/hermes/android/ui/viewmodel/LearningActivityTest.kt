package com.hermes.android.ui.viewmodel

import com.hermes.android.domain.memory.LearningNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class LearningActivityTest {

    private fun node(id: String, date: LocalDate?) = LearningNode(
        id = id, label = id, kind = LearningNode.Kind.SKILL, category = "x",
        timestamp = date?.atStartOfDay()?.toEpochSecond(ZoneOffset.UTC), useCount = 0,
        state = "active", createdBy = "agent", pinned = false, memorySource = null,
    )

    @Test
    fun `months between first and last, empty ones included`() {
        val activity = monthlyActivity(
            listOf(
                node("a", LocalDate.of(2026, 7, 3)),
                node("b", LocalDate.of(2026, 9, 20)),
                node("c", LocalDate.of(2026, 9, 21)),
                node("d", null),
            ),
            zone = ZoneOffset.UTC,
        )
        assertEquals(
            listOf(YearMonth.of(2026, 7) to 1, YearMonth.of(2026, 8) to 0, YearMonth.of(2026, 9) to 2),
            activity,
        )
    }

    @Test
    fun `capped to the newest months`() {
        val activity = monthlyActivity(
            listOf(node("a", LocalDate.of(2024, 1, 1)), node("b", LocalDate.of(2026, 9, 1))),
            zone = ZoneOffset.UTC,
            months = 3,
        )
        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9)), activity.map { it.first })
    }

    @Test
    fun `nothing dated`() {
        assertTrue(monthlyActivity(listOf(node("a", null)), zone = ZoneOffset.UTC).isEmpty())
    }
}

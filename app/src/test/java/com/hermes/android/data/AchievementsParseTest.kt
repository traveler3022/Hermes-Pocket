package com.hermes.android.data

import com.hermes.android.domain.achievements.Achievement
import com.hermes.android.domain.achievements.Tier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsParseTest {

    @Test
    fun `board from GET achievements`() {
        // Trimmed from the hermes-achievements plugin 0.4.0 on Hermes 0.21.7.
        val board = parseAchievementBoard(
            Json.parseToJsonElement(
                """
                {"achievements": [
                   {"id": "let_him_cook", "name": "Let Him Cook", "description": "A long tool chain.",
                    "category": "Agent Autonomy", "kind": "best_session", "icon": "flame",
                    "unlocked": true, "discovered": true, "state": "unlocked", "tier": "Silver",
                    "progress": 640, "next_tier": "Gold", "next_threshold": 1200, "progress_pct": 53,
                    "unlocked_at": 1792231540, "criteria": "Requirement: tool calls in one session."},
                   {"id": "full_send", "name": "Full Send", "description": "Everything at once.",
                    "category": "Agent Autonomy", "kind": "multi_condition", "unlocked": false,
                    "discovered": true, "state": "discovered", "tier": null, "progress": 0,
                    "next_tier": null, "next_threshold": 100, "progress_pct": 0, "criteria": ""},
                   {"id": "port_3000_taken", "name": "???", "category": "Debugging Chaos",
                    "unlocked": false, "discovered": false, "state": "secret", "progress": 0},
                   {"name": "no id, skipped"}
                 ],
                 "unlocked_count": 1, "total_count": 60, "error": null,
                 "scan_meta": {"mode": "pending", "status": {"state": "running"}}}
                """.trimIndent(),
            ),
        )
        assertEquals(3, board.items.size)
        assertEquals(1, board.unlocked)
        assertEquals(60, board.total)
        assertTrue(board.scanning)
        assertNull(board.error)

        val cook = board.items[0]
        assertEquals(Achievement.State.UNLOCKED, cook.state)
        assertEquals(Tier.SILVER, cook.tier)
        assertEquals(Tier.GOLD, cook.nextTier)
        assertEquals(640L, cook.progress)
        assertEquals(1200L, cook.nextThreshold)
        assertEquals(53, cook.percent)
        assertEquals(1792231540L, cook.unlockedAt)

        assertEquals(Achievement.State.DISCOVERED, board.items[1].state)
        assertNull(board.items[1].tier)
        assertEquals(Achievement.State.SECRET, board.items[2].state)
    }

    @Test
    fun `a finished scan with an error, and an empty answer`() {
        val board = parseAchievementBoard(
            Json.parseToJsonElement(
                """{"achievements": [], "error": "Could not open SessionDB", "scan_meta": {"status": {"state": "idle"}}}""",
            ),
        )
        assertFalse(board.scanning)
        assertEquals("Could not open SessionDB", board.error)
        assertEquals(0, board.total)
        assertTrue(parseAchievementBoard(Json.parseToJsonElement("null")).items.isEmpty())
    }

    @Test
    fun tierNamesMapToTheLadderInOrder() {
        assertEquals(Tier.OLYMPIAN, Tier.fromHermes("Olympian"))
        assertEquals(Tier.GOLD, Tier.fromHermes("gold"))
        assertNull(Tier.fromHermes("Mythic"))
        assertNull(Tier.fromHermes(null))
        assertTrue(Tier.COPPER < Tier.SILVER && Tier.DIAMOND < Tier.OLYMPIAN)
    }
}

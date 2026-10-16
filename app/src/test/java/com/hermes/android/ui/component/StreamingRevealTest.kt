package com.hermes.android.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingRevealTest {

    @Test
    fun `each arrival becomes its own fading piece`() {
        val track = RevealTrack()
        track.update(length = 10, now = 1_000, fading = true)
        track.update(length = 25, now = 1_080, fading = true)
        assertEquals(
            listOf(RevealBatch(0, 10, 1_000), RevealBatch(10, 25, 1_080)),
            track.batches,
        )
    }

    @Test
    fun `finished pieces are dropped`() {
        val track = RevealTrack()
        track.update(length = 10, now = 1_000, fading = true)
        track.update(length = 20, now = 1_000 + REVEAL_FADE_MS, fading = true)
        assertEquals(listOf(RevealBatch(10, 20, 1_000 + REVEAL_FADE_MS)), track.batches)
    }

    @Test
    fun `text that was there before streaming does not fade`() {
        val track = RevealTrack()
        track.update(length = 40, now = 1_000, fading = false)
        track.update(length = 50, now = 1_100, fading = true)
        assertEquals(listOf(RevealBatch(40, 50, 1_100)), track.batches)
    }

    @Test
    fun `shorter text clips pieces and adds none`() {
        val track = RevealTrack()
        track.update(length = 30, now = 1_000, fading = true)
        track.update(length = 12, now = 1_050, fading = true)
        assertEquals(listOf(RevealBatch(0, 12, 1_000)), track.batches)
    }

    @Test
    fun `alpha eases from zero to one over the fade`() {
        val batch = RevealBatch(0, 1, startedAt = 0)
        assertEquals(0f, batch.alpha(0), 0f)
        assertEquals(0.5f, batch.alpha(REVEAL_FADE_MS / 2), 0.001f)
        assertEquals(1f, batch.alpha(REVEAL_FADE_MS), 0f)
        assertTrue(batch.alpha(REVEAL_FADE_MS / 4) < 0.25f)
    }
}

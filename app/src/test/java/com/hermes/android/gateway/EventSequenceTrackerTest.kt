package com.hermes.android.gateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSequenceTrackerTest {

    private val tracker = EventSequenceTracker()

    @Test
    fun `contiguous frames are not a gap`() {
        assertFalse(tracker.isGap("s", 1))
        assertFalse(tracker.isGap("s", 2))
        assertFalse(tracker.isGap("s", 3))
    }

    @Test
    fun `a skipped frame is a gap`() {
        tracker.isGap("s", 4)

        assertTrue(tracker.isGap("s", 6))
    }

    @Test
    fun `the first frame on a socket is never a gap`() {
        assertFalse(tracker.isGap("s", 97))
    }

    @Test
    fun `duplicates and older frames do not move the watermark`() {
        tracker.isGap("s", 5)

        assertFalse(tracker.isGap("s", 5))
        assertFalse(tracker.isGap("s", 3))
        assertFalse(tracker.isGap("s", 6))
    }

    @Test
    fun `sessions are tracked independently`() {
        tracker.isGap("a", 1)
        tracker.isGap("b", 10)

        assertFalse(tracker.isGap("a", 2))
        assertFalse(tracker.isGap("b", 11))
    }

    @Test
    fun `reset starts tracking a fresh socket`() {
        tracker.isGap("s", 1)
        tracker.reset()

        assertFalse(tracker.isGap("s", 50))
    }
}

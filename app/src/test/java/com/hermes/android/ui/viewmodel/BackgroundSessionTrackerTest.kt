package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSessionTrackerTest {

    @Test
    fun `a chat the user is not looking at still reports as running`() {
        val tracker = BackgroundSessionTracker()
        tracker.onTurnStart("A")
        tracker.onDelta("A", "salam ")
        tracker.onDelta("A", "donya")

        val a = tracker.snapshot().getValue("A")
        assertTrue(a.isRunning)
        assertEquals("salam donya", a.preview)
        assertEquals(0, a.unreadReplies)
    }

    @Test
    fun `a reply that lands while away is unread and stacks`() {
        val tracker = BackgroundSessionTracker()
        tracker.onTurnStart("A")
        tracker.onTurnEnd("A", "first", isActive = false)
        assertEquals(1, tracker.snapshot().getValue("A").unreadReplies)
        assertFalse(tracker.snapshot().getValue("A").isRunning)

        tracker.onTurnStart("A")
        tracker.onTurnEnd("A", "second", isActive = false)
        assertEquals(2, tracker.snapshot().getValue("A").unreadReplies)
    }

    @Test
    fun `a reply in the chat on screen is never unread`() {
        val tracker = BackgroundSessionTracker()
        tracker.onTurnStart("A")
        tracker.onTurnEnd("A", "hi", isActive = true)
        assertEquals(0, tracker.snapshot().getValue("A").unreadReplies)
    }

    @Test
    fun `opening a chat clears its badge`() {
        val tracker = BackgroundSessionTracker()
        tracker.onTurnStart("A")
        tracker.onTurnEnd("A", "hi", isActive = false)
        tracker.markRead("A")
        assertEquals(0, tracker.snapshot().getValue("A").unreadReplies)
    }

    @Test
    fun `two concurrent chats do not leak into each other`() {
        val tracker = BackgroundSessionTracker()
        tracker.onTurnStart("A")
        tracker.onTurnStart("B")
        tracker.onDelta("A", "aaa")
        tracker.onDelta("B", "bbb")
        tracker.onTurnEnd("B", "bbb", isActive = false)

        assertEquals("aaa", tracker.snapshot().getValue("A").preview)
        assertTrue(tracker.snapshot().getValue("A").isRunning)
        assertEquals("bbb", tracker.snapshot().getValue("B").preview)
        assertFalse(tracker.snapshot().getValue("B").isRunning)
    }

    @Test
    fun `preview stays a bounded single line tail`() {
        val tracker = BackgroundSessionTracker()
        tracker.onTurnStart("A")
        tracker.onDelta("A", "x".repeat(5000) + "\n\n  end")

        val preview = tracker.snapshot().getValue("A").preview
        assertTrue(preview.length <= 120)
        assertFalse(preview.contains("\n"))
        assertTrue(preview.endsWith("end"))
    }

    @Test
    fun `a new turn clears the previous preview`() {
        val tracker = BackgroundSessionTracker()
        tracker.onTurnStart("A")
        tracker.onDelta("A", "old answer")
        tracker.onTurnEnd("A", "old answer", isActive = false)
        tracker.onTurnStart("A")

        assertEquals("", tracker.snapshot().getValue("A").preview)
        assertEquals(1, tracker.snapshot().getValue("A").unreadReplies)
    }

    @Test
    fun `blank ids and empty deltas are ignored`() {
        val tracker = BackgroundSessionTracker()
        tracker.onTurnStart("")
        tracker.onDelta("", "z")
        tracker.onTurnEnd("", "z", isActive = false)
        tracker.onTurnStart("A")
        tracker.onDelta("A", "")

        assertNull(tracker.snapshot()[""])
        assertEquals("", tracker.snapshot().getValue("A").preview)
    }

    @Test
    fun `forget drops a deleted session so its badge cannot outlive it`() {
        val tracker = BackgroundSessionTracker()
        tracker.onTurnStart("A")
        tracker.onTurnEnd("A", "hi", isActive = false)
        tracker.forget("A")
        assertNull(tracker.snapshot()["A"])
    }
}

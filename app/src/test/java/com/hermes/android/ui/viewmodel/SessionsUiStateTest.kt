package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionsUiStateTest {

    @Test
    fun `SessionSortOrder has expected values`() {
        val orders = SessionSortOrder.values()
        assertEquals(3, orders.size)
        assertTrue(orders.contains(SessionSortOrder.NEWEST_FIRST))
        assertTrue(orders.contains(SessionSortOrder.OLDEST_FIRST))
        assertTrue(orders.contains(SessionSortOrder.NAME_AZ))
    }

    @Test
    fun `SessionsUiState default empty`() {
        val state = SessionsUiState()
        assertTrue(state.sessions.isEmpty())
        assertFalse(state.isLoadingSessions)
        assertNull(state.errorMessage)
        assertNull(state.showRenameDialog)
        assertEquals(SessionSortOrder.NEWEST_FIRST, state.sortOrder)
    }

    @Test
    fun `SessionsUiState with sessions`() {
        val session = SessionSummary(id = "s1", title = "Test", lastMessagePreview = "hello", updatedAt = 1000L, messageCount = 5)
        val state = SessionsUiState(sessions = listOf(session))
        assertEquals(1, state.sessions.size)
        assertEquals("Test", state.sessions[0].title)
        assertEquals("hello", state.sessions[0].lastMessagePreview)
    }

    @Test
    fun `SessionRenameDialog fields`() {
        val dialog = SessionRenameDialog(sessionId = "s1", currentTitle = "Old Name")
        assertEquals("s1", dialog.sessionId)
        assertEquals("Old Name", dialog.currentTitle)
    }

    @Test
    fun `SessionUsage fields`() {
        val usage = SessionUsage(calls = 10, input = 5000, output = 2000, total = 7000)
        assertEquals(10, usage.calls)
        assertEquals(5000, usage.input)
        assertEquals(2000, usage.output)
        assertEquals(7000, usage.total)
    }

    @Test
    fun `AgentProcess fields`() {
        val agent = AgentProcess(sessionId = "s1", command = "run", status = "running", uptimeSeconds = 42L)
        assertEquals("s1", agent.sessionId)
        assertEquals("running", agent.status)
    }

    @Test
    fun `SessionsUiState with error`() {
        val state = SessionsUiState(errorMessage = "Network error")
        assertEquals("Network error", state.errorMessage)
    }

    @Test
    fun `SessionsUiState with rename dialog`() {
        val dialog = SessionRenameDialog(sessionId = "s1", currentTitle = "Old")
        val state = SessionsUiState(showRenameDialog = dialog)
        assertNotNull(state.showRenameDialog)
        assertEquals("s1", state.showRenameDialog!!.sessionId)
    }
}

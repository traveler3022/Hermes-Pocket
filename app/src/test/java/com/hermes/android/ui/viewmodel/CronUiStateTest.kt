package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CronUiStateTest {

    @Test
    fun `CronUiState default has empty job list`() {
        val state = CronUiState()
        assertTrue(state.jobs.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `CronUiState with error`() {
        val state = CronUiState(errorMessage = "Failed to load")
        assertEquals("Failed to load", state.errorMessage)
    }

    @Test
    fun `CronUiState loading state`() {
        val state = CronUiState(isLoading = true)
        assertTrue(state.isLoading)
    }

    @Test
    fun `CronUiState with jobs`() {
        val job = CronJob(id = "job1", name = "Daily summary", schedule = "0 9 * * *", promptPreview = "Summarize", enabled = true, lastRunAt = null, nextRunAt = null, lastStatus = null, state = "idle")
        val state = CronUiState(jobs = listOf(job))
        assertEquals(1, state.jobs.size)
        assertEquals("Daily summary", state.jobs[0].name)
        assertTrue(state.jobs[0].enabled)
    }

    @Test
    fun `CronJob disabled state`() {
        val job = CronJob(id = "job2", name = "Disabled", schedule = "0 0 * * 0", promptPreview = "test", enabled = false, lastRunAt = "2024-01-01", nextRunAt = null, lastStatus = "success", state = "idle")
        assertFalse(job.enabled)
        assertEquals("2024-01-01", job.lastRunAt)
    }

    @Test
    fun `CronUiState showCreateDialog`() {
        val state = CronUiState(showCreateDialog = true)
        assertTrue(state.showCreateDialog)
    }
}

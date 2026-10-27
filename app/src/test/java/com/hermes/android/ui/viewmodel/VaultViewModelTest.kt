package com.hermes.android.ui.viewmodel

import com.hermes.android.domain.vault.CredentialVault
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Monkey crash: opening the vault with the gateway disconnected threw out of the
    // async calls and killed the app instead of showing the error. runTest fails the test
    // on such an uncaught exception.
    @Test
    fun `load failure while disconnected shows the error instead of crashing`() = runTest {
        val vault = mockk<CredentialVault>()
        coEvery { vault.sources() } throws IllegalStateException("Not connected")
        coEvery { vault.items() } returns emptyList()

        val state = VaultViewModel(vault).uiState.value

        assertFalse(state.loading)
        assertEquals("Not connected", state.loadError)
    }
}

package com.hermes.android.ui.viewmodel

import com.hermes.android.data.DefaultModelResult
import com.hermes.android.data.KeyCheck
import com.hermes.android.data.ProviderSetupRepository
import com.hermes.android.data.SetupProvider
import com.hermes.android.data.SetupState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val openRouter = SetupProvider("openrouter", "OpenRouter", "OPENROUTER_API_KEY", "https://openrouter.ai/keys")
    private val custom = SetupProvider("custom", "Custom", "OPENAI_BASE_URL", "", needsBaseUrl = true)
    private val repository = mockk<ProviderSetupRepository>(relaxed = true)
    private val setupState = mockk<SetupState>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.providers } returns listOf(openRouter, custom)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModelAtKeyStep(provider: SetupProvider = openRouter) =
        SetupViewModel(repository, setupState).apply { pickProvider(provider) }

    @Test
    fun `a rejected key shows the error and is not saved`() {
        coEvery { repository.checkKey(openRouter, "bad", "") } returns
            KeyCheck(ok = false, reachable = true, message = "That API key was rejected.")
        val vm = viewModelAtKeyStep()
        vm.setApiKey("bad")

        vm.submitKey()

        assertEquals(SetupStep.ApiKey, vm.state.value.step)
        assertEquals("That API key was rejected.", vm.state.value.error)
        coVerify(exactly = 0) { repository.saveKey(any(), any()) }
    }

    @Test
    fun `an unverifiable key is still saved and the model list loads`() {
        coEvery { repository.checkKey(openRouter, "sk-or", "") } returns KeyCheck(ok = false, reachable = false, message = "")
        coEvery { repository.models(openRouter) } returns listOf("anthropic/claude-sonnet-4", "openai/gpt-4o")
        val vm = viewModelAtKeyStep()
        vm.setApiKey("  sk-or ")

        vm.submitKey()

        coVerify { repository.saveKey(openRouter, "sk-or") }
        assertEquals(SetupStep.Model, vm.state.value.step)
        assertEquals(listOf("anthropic/claude-sonnet-4", "openai/gpt-4o"), vm.state.value.models)
    }

    @Test
    fun `an empty key never reaches the gateway`() {
        val vm = viewModelAtKeyStep()

        vm.submitKey()

        assertNotNull(vm.state.value.error)
        coVerify(exactly = 0) { repository.checkKey(any(), any(), any()) }
    }

    @Test
    fun `a custom endpoint uses the models its validation returned`() {
        coEvery { repository.checkKey(custom, "", "http://10.0.0.2:8080/v1") } returns
            KeyCheck(ok = true, reachable = true, message = "", models = listOf("llama-3"))
        val vm = viewModelAtKeyStep(custom)
        vm.setBaseUrl("http://10.0.0.2:8080/v1")

        vm.submitKey()

        assertEquals(listOf("llama-3"), vm.state.value.models)
        coVerify(exactly = 0) { repository.models(any()) }
    }

    @Test
    fun `picking a model saves it and completes setup`() {
        coEvery { repository.setDefaultModel(openRouter, "openai/gpt-4o", any(), any(), false) } returns DefaultModelResult.Applied
        val vm = viewModelAtKeyStep()

        vm.pickModel("openai/gpt-4o")

        assertTrue(vm.state.value.finished)
        verify { setupState.markComplete() }
    }

    @Test
    fun `an expensive model waits for confirmation before completing`() {
        coEvery { repository.setDefaultModel(openRouter, "big-model", any(), any(), false) } returns
            DefaultModelResult.NeedsConfirm("This model costs a lot.")
        coEvery { repository.setDefaultModel(openRouter, "big-model", any(), any(), true) } returns DefaultModelResult.Applied
        val vm = viewModelAtKeyStep()

        vm.pickModel("big-model")
        assertEquals("This model costs a lot.", vm.state.value.confirmMessage)
        assertFalse(vm.state.value.finished)

        vm.confirmExpensiveModel()
        assertNull(vm.state.value.confirmMessage)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `a gateway failure surfaces as an error instead of crashing`() {
        coEvery { repository.checkKey(any(), any(), any()) } throws java.io.IOException("Hermes answered HTTP 500: boom")
        val vm = viewModelAtKeyStep()
        vm.setApiKey("sk")

        vm.submitKey()

        assertEquals("Hermes answered HTTP 500: boom", vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }
}

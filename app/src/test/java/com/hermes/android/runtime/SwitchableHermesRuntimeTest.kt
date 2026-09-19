package com.hermes.android.runtime

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.linux.ProotLinuxRuntime
import com.hermes.android.runtime.termux.TermuxBridge
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class SwitchableHermesRuntimeTest {

    private val selected = MutableStateFlow(RuntimeType.PROOT_LINUX)
    private val selection = mockk<RuntimeSelection> {
        every { this@mockk.selected } returns this@SwitchableHermesRuntimeTest.selected
        every { select(any()) } answers { this@SwitchableHermesRuntimeTest.selected.value = firstArg() }
    }
    private val termux = runtimeMock<TermuxBridge>(RuntimeType.TERMUX, "ws://127.0.0.1:9119/api/ws?token=t")
    private val linux = runtimeMock<ProotLinuxRuntime>(RuntimeType.PROOT_LINUX, "ws://127.0.0.1:9120/api/ws?token=l")
    private val gatewayClient = mockk<GatewayClient>(relaxed = true)

    private inline fun <reified T : HermesRuntime> runtimeMock(type: RuntimeType, url: String): T = mockk(relaxed = true) {
        every { this@mockk.type } returns type
        every { state } returns MutableStateFlow(RuntimeState.NotDetected)
        every { installProgress } returns MutableStateFlow(null)
        every { getWebSocketUrl() } returns url
    }

    @Test
    fun `calls go to the selected runtime`() {
        val router = SwitchableHermesRuntime(selection, termux, linux, gatewayClient)

        assertEquals(RuntimeType.PROOT_LINUX, router.type)
        assertEquals("ws://127.0.0.1:9120/api/ws?token=l", router.getWebSocketUrl())

        router.select(RuntimeType.TERMUX)

        assertEquals(RuntimeType.TERMUX, router.type)
        assertEquals("ws://127.0.0.1:9119/api/ws?token=t", router.getWebSocketUrl())
    }

    @Test
    fun `switching away from the built-in runtime drops the socket and stops its gateway`() {
        coEvery { linux.stopGateway() } returns StopResult.Success
        val router = SwitchableHermesRuntime(selection, termux, linux, gatewayClient)

        router.select(RuntimeType.TERMUX)

        coVerify(timeout = 2_000) { gatewayClient.disconnect() }
        coVerify(timeout = 2_000) { linux.stopGateway() }
    }

    @Test
    fun `selecting the current runtime is a no-op`() {
        val router = SwitchableHermesRuntime(selection, termux, linux, gatewayClient)

        router.select(RuntimeType.PROOT_LINUX)

        coVerify(exactly = 0) { gatewayClient.disconnect() }
    }
}

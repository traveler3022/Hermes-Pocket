package com.hermes.android.runtime

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.linux.ProotLinuxRuntime
import com.hermes.android.runtime.remote.RemoteRuntime
import com.hermes.android.runtime.termux.TermuxBridge
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private val remote = runtimeMock<RemoteRuntime>(RuntimeType.REMOTE, "wss://example.com/api/ws")
    private val gatewayClient = mockk<GatewayClient>(relaxed = true)

    private inline fun <reified T : HermesRuntime> runtimeMock(type: RuntimeType, url: String): T = mockk(relaxed = true) {
        every { this@mockk.type } returns type
        every { state } returns MutableStateFlow(RuntimeState.NotDetected)
        every { installProgress } returns MutableStateFlow(null)
        every { getWebSocketUrl() } returns url
    }

    @Test
    fun `calls go to the selected runtime`() {
        val router = SwitchableHermesRuntime(selection, termux, linux, remote, gatewayClient)

        assertEquals(RuntimeType.PROOT_LINUX, router.type)
        assertEquals("ws://127.0.0.1:9120/api/ws?token=l", router.getWebSocketUrl())

        router.select(RuntimeType.TERMUX)

        assertEquals(RuntimeType.TERMUX, router.type)
        assertEquals("ws://127.0.0.1:9119/api/ws?token=t", router.getWebSocketUrl())
    }

    @Test
    fun `the server choice goes to the remote runtime`() {
        val router = SwitchableHermesRuntime(selection, termux, linux, remote, gatewayClient)

        router.select(RuntimeType.REMOTE)

        assertEquals(RuntimeType.REMOTE, router.type)
        assertEquals("wss://example.com/api/ws", router.getWebSocketUrl())
    }

    @Test
    fun `switching away from the built-in runtime drops the socket and stops its gateway`() {
        coEvery { linux.stopGateway() } returns StopResult.Success
        val router = SwitchableHermesRuntime(selection, termux, linux, remote, gatewayClient)

        router.select(RuntimeType.TERMUX)

        coVerify(timeout = 2_000) { gatewayClient.disconnect() }
        coVerify(timeout = 2_000) { linux.stopGateway() }
    }

    @Test
    fun `switching away from Termux stops its dashboard and forgets its address`() {
        coEvery { termux.stopGateway() } returns StopResult.Success
        selected.value = RuntimeType.TERMUX
        val router = SwitchableHermesRuntime(selection, termux, linux, remote, gatewayClient)

        router.select(RuntimeType.PROOT_LINUX)

        coVerify(timeout = 2_000) { gatewayClient.forgetEndpoint() }
        coVerify(timeout = 2_000) { termux.stopGateway() }
    }

    @Test
    fun `selecting the current runtime keeps the connection but records the choice`() {
        val router = SwitchableHermesRuntime(selection, termux, linux, remote, gatewayClient)

        router.select(RuntimeType.PROOT_LINUX)

        coVerify(exactly = 0) { gatewayClient.disconnect() }
        // A fresh install's default is not a choice until the user picks it.
        verify { selection.select(RuntimeType.PROOT_LINUX) }
    }
}

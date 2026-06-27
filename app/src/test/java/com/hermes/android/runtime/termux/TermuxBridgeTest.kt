package com.hermes.android.runtime.termux

import android.content.Context
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.InstallResult
import com.hermes.android.runtime.ProgressEmitter
import com.hermes.android.runtime.RuntimeInfo
import com.hermes.android.runtime.RuntimeState
import com.hermes.android.runtime.RuntimeType
import com.hermes.android.runtime.GatewayHandle
import com.hermes.android.runtime.StopResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TermuxBridge] — pure JVM (no Robolectric).
 *
 * What these tests verify:
 * - `install()` calls `executor.executeBackgroundScript()` instead of relying
 *   on the user to copy/paste a command.
 * - `startGateway()` calls `executor.executeBackgroundScript()` with a
 *   `hermes dashboard` script — NOT a TODO stub.
 * - `stopGateway()` calls `executor.executeBackgroundScript()` with a
 *   `hermes dashboard --stop` script — NOT a TODO stub.
 * - `isHealthy()` checks `GatewayClient.connect()` state — NOT just local state.
 * - `getInstallInstructions()` returns null on the automated path (no copy/paste).
 *
 * Per gateway-bind-audit.md (2026-06-27): the dashboard server (not the
 * gateway service) is what exposes /api/ws on 127.0.0.1:9119.
 */
class TermuxBridgeTest {

    private lateinit var context: Context
    private lateinit var detector: TermuxDetector
    private lateinit var installer: TermuxInstaller
    private lateinit var executor: TermuxCommandExecutor
    private lateinit var gatewayClient: GatewayClient
    private lateinit var progressFlow: MutableStateFlow<com.hermes.android.runtime.InstallProgress?>
    private lateinit var completionFlow: MutableStateFlow<TermuxInstallProgressReceiver.InstallCompletion>
    private lateinit var bridge: TermuxBridge

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        detector = mockk(relaxed = true)
        installer = mockk(relaxed = true)
        executor = mockk(relaxed = true)
        gatewayClient = mockk(relaxed = true)
        progressFlow = MutableStateFlow(null)
        completionFlow = MutableStateFlow(TermuxInstallProgressReceiver.InstallCompletion.Pending)

        bridge = TermuxBridge(
            context = context,
            detector = detector,
            installer = installer,
            executor = executor,
            gatewayClient = gatewayClient,
            progressFlow = progressFlow,
            completionFlow = completionFlow,
        )
    }

    @Test
    fun `install dispatches script via executor not via copy-paste`() = runTest {
        // Given: Termux is detected and executor accepts the script
        val runtimeInfo = RuntimeInfo(type = RuntimeType.TERMUX, version = "1.0.0")
        bridgeTest_setState(RuntimeState.Detected(runtimeInfo))
        every { installer.generateInstallScript() } returns "echo install"
        every { executor.executeBackgroundScript(any(), any()) } returns
            TermuxCommandExecutor.Result.Accepted

        // Simulate the install completing asynchronously after install() resets
        // any stale completion value to Pending.
        launch {
            kotlinx.coroutines.delay(1_000)
            completionFlow.value = TermuxInstallProgressReceiver.InstallCompletion.Completed
        }

        // When: install is called
        val result = bridge.install(ProgressEmitter { })

        // Then: executor was called (NOT user copy/paste)
        val scriptSlot = slot<String>()
        coVerify { executor.executeBackgroundScript(capture(scriptSlot), any()) }
        assertTrue("Script must be passed to executor", scriptSlot.captured.isNotEmpty())
        assertTrue("Install must succeed", result is InstallResult.Success)
    }

    @Test
    fun `install returns Failure when executor reports TermuxMissing`() = runTest {
        val runtimeInfo = RuntimeInfo(type = RuntimeType.TERMUX, version = "1.0.0")
        bridgeTest_setState(RuntimeState.Detected(runtimeInfo))
        every { installer.generateInstallScript() } returns "echo install"
        every { executor.executeBackgroundScript(any(), any()) } returns
            TermuxCommandExecutor.Result.TermuxMissing("Termux is not installed. Cannot send RUN_COMMAND intent.")

        val result = bridge.install(ProgressEmitter { })

        assertTrue("Result must be Failure", result is InstallResult.Failure)
        assertTrue(
            "Failure reason must mention Termux missing",
            (result as InstallResult.Failure).reason.contains("Termux"),
        )
    }

    @Test
    fun `install returns Failure with allow-external-apps guidance when policy blocks`() = runTest {
        val runtimeInfo = RuntimeInfo(type = RuntimeType.TERMUX, version = "1.0.0")
        bridgeTest_setState(RuntimeState.Detected(runtimeInfo))
        every { installer.generateInstallScript() } returns "echo install"
        every { executor.executeBackgroundScript(any(), any()) } returns
            TermuxCommandExecutor.Result.AllowExternalAppsDisabled("policy violated")
        every { executor.buildAllowExternalAppsInstructions() } returns "Setup instructions here"

        val result = bridge.install(ProgressEmitter { })

        assertTrue("Result must be Failure", result is InstallResult.Failure)
        val reason = (result as InstallResult.Failure).reason
        assertTrue("Reason must mention allow-external-apps", reason.contains("allow-external-apps"))
        assertTrue("Reason must include setup instructions", reason.contains("Setup instructions here"))
    }

    @Test
    fun `startGateway dispatches hermes dashboard via executor`() = runTest {
        // Given: runtime is Installed
        val runtimeInfo = RuntimeInfo(type = RuntimeType.TERMUX, version = "1.0.0", hermesVersion = "0.17.0")
        bridgeTest_setState(RuntimeState.Installed(runtimeInfo))
        every { executor.executeBackgroundScript(any(), any()) } returns
            TermuxCommandExecutor.Result.Accepted
        coEvery { gatewayClient.connect(any(), any()) } returns ConnectionState.Connecting

        // When: startGateway is called
        val handle = bridge.startGateway()

        // Then: executor was called with a script that contains "hermes dashboard"
        // (NOT "hermes gateway start" — that fails on Termux per gateway.py:6201)
        val scriptSlot = slot<String>()
        coVerify { executor.executeBackgroundScript(capture(scriptSlot), any()) }
        assertTrue(
            "Script must invoke 'hermes dashboard' (not 'gateway start')",
            scriptSlot.captured.contains("hermes dashboard"),
        )
        assertTrue(
            "Script must NOT use 'gateway start' (fails on Termux)",
            !scriptSlot.captured.contains("gateway start"),
        )
        // Per gateway-bind-audit.md: must set HERMES_DASHBOARD_SESSION_TOKEN
        assertTrue(
            "Script must set HERMES_DASHBOARD_SESSION_TOKEN env var",
            scriptSlot.captured.contains("HERMES_DASHBOARD_SESSION_TOKEN"),
        )
        assertTrue(
            "Script must prefer official Termux install layout (~/.hermes/hermes-agent/venv)",
            scriptSlot.captured.contains(".hermes/hermes-agent/venv/bin/hermes"),
        )
        assertTrue(
            "Script must create a placeholder assets dir for dashboard StaticFiles",
            scriptSlot.captured.contains("web_dist_placeholder/assets"),
        )
        // Must bind to 127.0.0.1:9119 (default dashboard port)
        assertTrue(
            "Script must use --host 127.0.0.1",
            scriptSlot.captured.contains("--host 127.0.0.1"),
        )
        assertTrue(
            "Script must use --port 9119",
            scriptSlot.captured.contains("--port 9119"),
        )
        assertTrue("Handle must have WS URL", handle.webSocketUrl.startsWith("ws://"))
    }

    @Test
    fun `startGateway throws when executor returns Failure`() = runTest {
        val runtimeInfo = RuntimeInfo(type = RuntimeType.TERMUX, version = "1.0.0", hermesVersion = "0.17.0")
        bridgeTest_setState(RuntimeState.Installed(runtimeInfo))
        every { executor.executeBackgroundScript(any(), any()) } returns
            TermuxCommandExecutor.Result.Failure("explosion")

        var threw = false
        try {
            bridge.startGateway()
        } catch (e: IllegalStateException) {
            threw = true
            assertTrue("Exception message must mention the failure", e.message!!.contains("explosion"))
        }
        assertTrue("startGateway must throw on executor failure", threw)
    }

    @Test
    fun `stopGateway dispatches hermes dashboard --stop via executor`() = runTest {
        val runtimeInfo = RuntimeInfo(type = RuntimeType.TERMUX, version = "1.0.0", hermesVersion = "0.17.0")
        val handle = GatewayHandle(
            pid = null,
            startedAt = System.currentTimeMillis(),
            webSocketUrl = "ws://127.0.0.1:9119/api/ws?token=test",
        )
        bridgeTest_setState(RuntimeState.Running(runtimeInfo, handle))
        every { executor.executeBackgroundScript(any(), any()) } returns
            TermuxCommandExecutor.Result.Accepted
        coEvery { gatewayClient.disconnect() } returns Unit

        val result = bridge.stopGateway()

        val scriptSlot = slot<String>()
        coVerify { executor.executeBackgroundScript(capture(scriptSlot), any()) }
        assertTrue(
            "Stop script must prefer official Termux install layout (~/.hermes/hermes-agent/venv)",
            scriptSlot.captured.contains(".hermes/hermes-agent/venv/bin/hermes"),
        )
        assertTrue(
            "Script must invoke 'hermes dashboard --stop' (not 'gateway stop')",
            scriptSlot.captured.contains("dashboard --stop"),
        )
        assertTrue(
            "Script must NOT use 'gateway stop' (irrelevant for dashboard)",
            !scriptSlot.captured.contains("gateway stop"),
        )
        assertTrue("stopGateway must succeed", result is StopResult.Success)
    }

    @Test
    fun `stopGateway returns Failure when not running`() = runTest {
        bridgeTest_setState(RuntimeState.NotDetected)
        val result = bridge.stopGateway()
        assertTrue(result is StopResult.Failure)
    }

    @Test
    fun `isHealthy returns false when not in Running state`() = runTest {
        bridgeTest_setState(RuntimeState.Installed(
            RuntimeInfo(type = RuntimeType.TERMUX, version = "1.0.0", hermesVersion = "0.17.0")
        ))
        val healthy = bridge.isHealthy()
        assertTrue("isHealthy must return false when not Running", !healthy)
    }

    @Test
    fun `isHealthy returns true when GatewayClient reports Connected`() = runTest {
        val runtimeInfo = RuntimeInfo(type = RuntimeType.TERMUX, version = "1.0.0", hermesVersion = "0.17.0")
        val handle = GatewayHandle(
            pid = null,
            startedAt = System.currentTimeMillis(),
            webSocketUrl = "ws://127.0.0.1:9119/api/ws?token=test",
        )
        bridgeTest_setState(RuntimeState.Running(runtimeInfo, handle))
        coEvery { gatewayClient.connect(any(), any()) } returns ConnectionState.Connected(sessionId = null)

        val healthy = bridge.isHealthy()
        assertTrue("isHealthy must return true when WS is connected", healthy)
    }

    @Test
    fun `isHealthy returns false when GatewayClient reports Disconnected`() = runTest {
        val runtimeInfo = RuntimeInfo(type = RuntimeType.TERMUX, version = "1.0.0", hermesVersion = "0.17.0")
        val handle = GatewayHandle(
            pid = null,
            startedAt = System.currentTimeMillis(),
            webSocketUrl = "ws://127.0.0.1:9119/api/ws?token=test",
        )
        bridgeTest_setState(RuntimeState.Running(runtimeInfo, handle))
        coEvery { gatewayClient.connect(any(), any()) } returns ConnectionState.Disconnected

        val healthy = bridge.isHealthy()
        assertTrue("isHealthy must return false when WS is Disconnected", !healthy)
    }

    @Test
    fun `getInstallInstructions returns null on automated path`() {
        // On the automated path, no copy/paste instructions are needed —
        // install() dispatches via executor.
        val instructions = bridge.getInstallInstructions()
        assertTrue("getInstallInstructions must return null on automated path", instructions == null)
    }

    @Test
    fun `getFallbackInstallInstructions returns copy-paste instructions for legacy path`() {
        every { installer.generateInstallCommand() } returns "echo legacy"
        val fallback = bridge.getFallbackInstallInstructions()
        assertTrue("Fallback must have non-null command", fallback.command != null)
        assertTrue("Fallback command must contain 'echo legacy'", fallback.command!!.contains("echo legacy"))
        assertTrue("Fallback title must mention fallback", fallback.title.contains("fallback", ignoreCase = true))
    }

    // ── Helper ──────────────────────────────────────────────────────────

    /**
     * Set the bridge's internal state. We use reflection because the state
     * is private and only changes via detect()/install()/startGateway().
     */
    private fun bridgeTest_setState(state: RuntimeState) {
        val field = TermuxBridge::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(bridge) as MutableStateFlow<RuntimeState>
        stateFlow.value = state
    }
}

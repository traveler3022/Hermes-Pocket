package com.hermes.android.runtime.embedded

import com.hermes.android.runtime.DetectionResult
import com.hermes.android.runtime.GatewayHandle
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.InstallInstructions
import com.hermes.android.runtime.InstallProgress
import com.hermes.android.runtime.InstallResult
import com.hermes.android.runtime.ProgressEmitter
import com.hermes.android.runtime.RuntimeInfo
import com.hermes.android.runtime.RuntimeState
import com.hermes.android.runtime.RuntimeType
import com.hermes.android.runtime.StopResult
import com.hermes.android.runtime.VerifyResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * STUB IMPLEMENTATION — for Compile-time Swap Test only.
 *
 * Purpose: verify that swapping TermuxBridge for a different HermesRuntime
 * implementation does NOT cause compilation errors in any other file
 * (UI, ViewModel, Domain, DI — except the DI binding itself).
 *
 * This class deliberately returns stub data. It is NOT a real implementation.
 * It will be replaced with a real EmbeddedPythonRuntime in Step 12.
 *
 * Reference:
 * - User request: Compile-time Swap Test (2026-06-26)
 * - ADR-009: Production builds must NOT require Termux interaction
 * - Phase 1.5 Rule 5: Runtime Swap Test
 */
@Singleton
class EmbeddedPythonRuntime @Inject constructor() : HermesRuntime {

    override val type: RuntimeType = RuntimeType.EMBEDDED_PYTHON

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.NotDetected)
    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private val _installProgress = MutableStateFlow<InstallProgress?>(null)
    override val installProgress: StateFlow<InstallProgress?> = _installProgress.asStateFlow()

    override suspend fun detect(): DetectionResult {
        // Stub: always returns Available
        val info = RuntimeInfo(
            type = RuntimeType.EMBEDDED_PYTHON,
            version = "stub-0.1",
            pythonVersion = "3.13.1",
        )
        _state.value = RuntimeState.Detected(info)
        return DetectionResult.Available(info)
    }

    override suspend fun install(progressEmitter: ProgressEmitter): InstallResult {
        return try {
            // Stub: succeeds immediately
            val info = RuntimeInfo(
                type = RuntimeType.EMBEDDED_PYTHON,
                version = "stub-0.1",
                pythonVersion = "3.13.1",
                hermesVersion = "stub-installed",
            )
            _state.value = RuntimeState.Installed(info)
            InstallResult.Success(info)
        } catch (e: Exception) {
            InstallResult.Failure(reason = e.message ?: "Unknown install error")
        }
    }

    override suspend fun verify(): VerifyResult {
        return VerifyResult.Success(hermesVersion = "stub", doctorOk = true)
    }

    override suspend fun startGateway(): GatewayHandle {
        return GatewayHandle(
            startedAt = System.currentTimeMillis(),
            webSocketUrl = getWebSocketUrl(),
        )
    }

    override suspend fun ensureGatewayReady(): Boolean {
        // Stub: the embedded runtime hosts the gateway in-process, so it is
        // ready whenever the runtime is running. No token re-sync needed.
        return _state.value is RuntimeState.Running
    }

    override suspend fun stopGateway(): StopResult {
        return StopResult.Success
    }

    override suspend fun fetchLogs() {
        // Embedded runtime logs are managed in-app
    }

    override suspend fun runDoctor(): String {
        return "Embedded Python runtime doctor: stub OK"
    }

    override suspend fun isHealthy(): Boolean {
        return _state.value is RuntimeState.Running
    }

    override fun getWebSocketUrl(): String = "ws://127.0.0.1:9119/api/ws"

    override fun launchHostApp(): Boolean {
        // Embedded runtime has no host app to launch
        return false
    }

    override fun getInstallInstructions(): InstallInstructions? {
        // Embedded runtime needs no user-side setup
        return null
    }
}

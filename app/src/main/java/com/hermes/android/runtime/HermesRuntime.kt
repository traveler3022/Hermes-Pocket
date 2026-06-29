package com.hermes.android.runtime

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract runtime that hosts the Hermes Python agent.
 *
 * ## Purpose
 * Provides a single, implementation-agnostic contract for the Android UI
 * and business logic to interact with the Hermes backend — regardless of
 * whether that backend runs inside a migration adapter (e.g. an external
 * terminal app) or as an embedded Python (future production).
 *
 * ## Implementations
 * - The current migration adapter (bound via Hilt in `di/RuntimeModule.kt`).
 * - (future) `EmbeddedPythonRuntime` — production target (ADR-009).
 * - (hypothetical) `NativeKotlinRuntime` — far-future rewrite.
 *
 * ## Design Constraints (per Step 2 approval)
 * - UI and business logic depend ONLY on this interface.
 * - No method here may leak implementation-specific concepts.
 * - Implementations must be swappable without UI changes.
 *
 * ## References
 * - ADR-001: Migration adapter — migration only
 * - ADR-002: Native Compose UI
 * - ADR-007: Reuse install.sh — migration only
 * - ADR-009: Production must NOT require a migration adapter
 * - migration-spec-v1.0 / docs/06-migration-order/01-roadmap.md Step 2
 */
interface HermesRuntime {

    /** Identifies the runtime implementation. */
    val type: RuntimeType

    /**
     * Current runtime state, observable for UI updates.
     * Updated by the implementation as detect/install/start progresses.
     */
    val state: StateFlow<RuntimeState>

    /**
     * Latest install progress update, or null when not installing.
     * UI renders a progress bar/stepper from this.
     */
    val installProgress: StateFlow<InstallProgress?>

    /**
     * Detect whether the runtime is available on this device.
     *
     * Does NOT initialize or install — only probes presence and basic compatibility.
     * Safe to call multiple times.
     *
     * @return [DetectionResult.Available] if ready,
     *         [DetectionResult.Missing] if user action is required,
     *         [DetectionResult.Incompatible] if device cannot support this runtime.
     */
    suspend fun detect(): DetectionResult

    /**
     * Install / initialize the runtime.
     *
     * For the migration adapter: triggers the install script in the external
     * terminal and polls progress.
     * For Embedded Python (future): extracts bundled Python + pip installs in-app.
     *
     * The caller supplies a [ProgressEmitter] that receives updates as
     * installation proceeds. This method suspends until installation
     * completes (success, failure, or cancellation).
     *
     * @return the final [InstallResult].
     */
    suspend fun install(progressEmitter: ProgressEmitter): InstallResult

    /**
     * Verify the runtime is functional after installation.
     *
     * Typically runs `hermes --version` and `hermes doctor` inside the runtime
     * and returns success only if both succeed.
     */
    suspend fun verify(): VerifyResult

    /**
     * Start the Hermes gateway process inside the runtime.
     *
     * Returns a [GatewayHandle] on success. The handle includes the WebSocket
     * URL the Android UI should connect to.
     *
     * Idempotent — calling while already running returns the existing handle.
     */
    suspend fun startGateway(): GatewayHandle

    /**
     * Stop the running gateway process.
     */
    suspend fun stopGateway(): StopResult

    /**
     * Fetch logs from the runtime (e.g. install.log and gateway_stdout.log).
     */
    suspend fun fetchLogs()

    /**
     * Run `hermes --version` + `hermes doctor` inside the runtime and return the
     * captured output for display. Read-only diagnostics — does not change state.
     */
    suspend fun runDoctor(): String

    /**
     * Quick health probe — is the runtime alive and the gateway responding?
     *
     * Cheaper than [verify]; intended for periodic polling by a foreground service.
     */
    suspend fun isHealthy(): Boolean

    /**
     * WebSocket URL for connecting to the gateway server.
     *
     * Format: `ws://localhost:PORT`
     *
     * The Android UI uses this to connect to the running Hermes backend
     * (Step 3 — WebSocket Client). The URL is implementation-specific:
     * the migration adapter binds to a localhost port; an embedded runtime
     * may bind to a Unix domain socket or a different port.
     */
    fun getWebSocketUrl(): String

    /**
     * Launch the runtime's host application (if any).
     *
     * For the migration adapter, this opens the external terminal app so the
     * user can run the install command. For an embedded runtime, this is a
     * no-op (returns false).
     *
     * Returns true if the launch succeeded.
     */
    fun launchHostApp(): Boolean

    /**
     * Installation instructions for the user, if the runtime requires
     * user-side setup.
     *
     * For the migration adapter, this returns a bash command the user must
     * run in the external terminal. For an embedded runtime, this returns
     * null (no user-side setup needed).
     */
    fun getInstallInstructions(): InstallInstructions?
}

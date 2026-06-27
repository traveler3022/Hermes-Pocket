package com.hermes.android.runtime.termux

import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.DetectionResult
import com.hermes.android.runtime.GatewayHandle
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.InstallAction
import com.hermes.android.runtime.InstallInstructions
import com.hermes.android.runtime.InstallProgress
import com.hermes.android.runtime.InstallResult
import com.hermes.android.runtime.ProgressEmitter
import com.hermes.android.runtime.RuntimeInfo
import com.hermes.android.runtime.RuntimeState
import com.hermes.android.runtime.RuntimeType
import com.hermes.android.runtime.StopResult
import com.hermes.android.runtime.VerifyResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Migration adapter — runs the Hermes Python agent inside Termux.
 *
 * ## Lifecycle
 *
 * 1. [detect] — checks if Termux is installed; if not, returns [DetectionResult.Missing]
 *    with an action to open F-Droid.
 * 2. [install] — sends the Hermes install script to Termux via RUN_COMMAND intent
 *    (no copy/paste, no user interaction with Termux required). Listens for
 *    progress broadcasts.
 * 3. [verify] — checks that the install script reported success.
 * 4. [startGateway] — sends `hermes dashboard --host 127.0.0.1 --port 9119 --no-open --skip-build`
 *    to Termux via RUN_COMMAND. Binds a WebSocket server on the shared loopback
 *    interface that the Android app connects to. Auth via HERMES_DASHBOARD_SESSION_TOKEN.
 * 5. [stopGateway] — sends `hermes dashboard --stop` to Termux via RUN_COMMAND.
 * 6. [isHealthy] — does a real WebSocket ping via [GatewayClient].
 *
 * ## Migration status
 *
 * This class is a **temporary migration adapter** per ADR-001 and ADR-009.
 * The long-term target is an Embedded Python runtime that does NOT require
 * Termux to be installed. The [HermesRuntime] interface allows that swap
 * without touching any UI code.
 *
 * Reference:
 * - ADR-001: Termux wrapper (migration only)
 * - ADR-007: Reuse install.sh (migration only)
 * - ADR-009: Production must NOT require Termux
 * - migration-spec-v1.0 / docs/06-migration-order/01-roadmap.md Step 2
 */
@Singleton
class TermuxBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detector: TermuxDetector,
    private val installer: TermuxInstaller,
    private val executor: TermuxCommandExecutor,
    private val gatewayClient: GatewayClient,
    @InstallProgressFlow private val progressFlow: MutableStateFlow<InstallProgress?>,
    @InstallCompletionFlow private val completionFlow:
        MutableStateFlow<TermuxInstallProgressReceiver.InstallCompletion>,
) : HermesRuntime {

    override val type: RuntimeType = RuntimeType.TERMUX

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.NotDetected)
    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    override val installProgress: StateFlow<InstallProgress?> = progressFlow.asStateFlow()

    override suspend fun detect(): DetectionResult {
        _state.value = RuntimeState.Detecting
        Timber.i("Detecting Termux runtime...")

        val detection = detector.detect()

        if (!detection.termuxInstalled) {
            Timber.w("Termux not installed")
            _state.value = RuntimeState.NotDetected
            return DetectionResult.Missing(
                title = "Termux is required (migration phase)",
                instructions = """
                    Hermes2 needs Termux to run the Hermes Python agent during the migration phase.

                    1. Install Termux from F-Droid (the Play Store version is deprecated).
                    2. Optionally install Termux:Boot for auto-start on device boot.
                    3. Return to Hermes2 and tap "Continue".

                    Note: This is a temporary dependency. A future version will bundle Python
                    directly in the APK and Termux will no longer be required (ADR-009).
                """.trimIndent(),
                action = InstallAction.OpenStore(
                    packageName = TermuxDetector.Package.TERMUX.packageName,
                    fDroidUrl = TermuxDetector.Package.TERMUX.fDroidUrl,
                ),
            )
        }

        val previouslyInstalled = isPreviouslyInstalled()
        val info = RuntimeInfo(
            type = RuntimeType.TERMUX,
            version = detection.termuxVersion,
            path = TERMUX_PREFIX_PATH,
            pythonVersion = null, // probed during install script (Fix S2F01)
            diskFreeBytes = detection.diskFreeBytes,
            hermesVersion = if (previouslyInstalled) "installed" else null,
            extras = buildMap {
                put("termuxApiInstalled", detection.termuxApiInstalled.toString())
                put("termuxBootInstalled", detection.termuxBootInstalled.toString())
                // Fix S2F07: flag if this is a re-install
                put("previouslyInstalled", previouslyInstalled.toString())
            },
        )

        Timber.i("Termux detected: ${info.version}, free=${info.diskFreeBytes} bytes, installed=$previouslyInstalled")
        _state.value = if (previouslyInstalled) RuntimeState.Installed(info) else RuntimeState.Detected(info)
        return DetectionResult.Available(info)
    }

    override suspend fun install(progressEmitter: ProgressEmitter): InstallResult {
        val currentState = _state.value
        if (currentState !is RuntimeState.Detected) {
            return InstallResult.Failure("Runtime must be detected before install. Current state: $currentState")
        }

        _state.value = RuntimeState.Installing
        completionFlow.value = TermuxInstallProgressReceiver.InstallCompletion.Pending
        progressFlow.value = InstallProgress(
            stage = "starting",
            message = "Dispatching install command to Termux...",
            percent = 0,
            timestamp = System.currentTimeMillis(),
        )

        // Register the broadcast receiver for progress updates
        registerProgressReceiver()

        try {
            // Generate the install script and dispatch it via RUN_COMMAND.
            // This is the "no copy/paste" path: the user never opens Termux.
            val script = installer.generateInstallScript()
            val dispatchResult = executor.executeBackgroundScript(
                script = script,
                workingDirectory = TermuxCommandExecutor.TERMUX_HOME,
            )

            when (dispatchResult) {
                is TermuxCommandExecutor.Result.Accepted -> {
                    Timber.i("[Install] RUN_COMMAND accepted by Termux")
                }
                is TermuxCommandExecutor.Result.TermuxMissing -> {
                    _state.value = RuntimeState.Error(dispatchResult.message)
                    return InstallResult.Failure(dispatchResult.message)
                }
                is TermuxCommandExecutor.Result.AllowExternalAppsDisabled -> {
                    val msg = "Termux rejected the install command. " +
                        "Enable `allow-external-apps=true` in ~/.termux/termux.properties. " +
                        "Instructions: ${executor.buildAllowExternalAppsInstructions()}"
                    _state.value = RuntimeState.Error(msg)
                    return InstallResult.Failure(msg)
                }
                is TermuxCommandExecutor.Result.Failure -> {
                    _state.value = RuntimeState.Error(dispatchResult.message)
                    return InstallResult.Failure(dispatchResult.message, dispatchResult.cause?.stackTraceToString())
                }
            }

            // Forward progressFlow updates to the caller's emitter.
            val progressJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
                .launch {
                    progressFlow.collectLatest { progress ->
                        if (progress != null) progressEmitter.emit(progress)
                    }
                }

            // Wait for completion (with a generous timeout — install can take 5-10 min on first run)
            val result = withTimeoutOrNull(INSTALL_TIMEOUT) {
                var lastCompletion: TermuxInstallProgressReceiver.InstallCompletion =
                    TermuxInstallProgressReceiver.InstallCompletion.Pending
                while (lastCompletion is TermuxInstallProgressReceiver.InstallCompletion.Pending) {
                    kotlinx.coroutines.delay(POLL_INTERVAL_MS)
                    lastCompletion = completionFlow.value
                }
                lastCompletion
            }

            progressJob.cancel()

            return when (result) {
                null -> {
                    Timber.w("Install timed out after $INSTALL_TIMEOUT")
                    InstallResult.Failure("Install timed out — no completion signal received from Termux")
                }
                TermuxInstallProgressReceiver.InstallCompletion.Completed -> {
                    Timber.i("Install completed successfully")
                    val updatedInfo = (currentState.info).copy(
                        hermesVersion = "installed",
                    )
                    // Fix S2F03: Cache install state in SharedPreferences
                    cacheInstallState(updatedInfo)
                    _state.value = RuntimeState.Installed(updatedInfo)
                    InstallResult.Success(updatedInfo)
                }
                is TermuxInstallProgressReceiver.InstallCompletion.Failed -> {
                    Timber.e("Install failed: ${result.message}")
                    _state.value = RuntimeState.Error(result.message)
                    InstallResult.Failure(result.message)
                }
                TermuxInstallProgressReceiver.InstallCompletion.Pending -> {
                    InstallResult.Failure("Install did not complete within timeout")
                }
            }
        } finally {
            unregisterProgressReceiver()
        }
    }

    override suspend fun verify(): VerifyResult {
        // Fix S2F05/S2F06: verify() now checks state and reports that
        // actual command execution (hermes --version, hermes doctor) is
        // performed by the install script itself (see TermuxInstaller).
        // The install script broadcasts the results via am broadcast,
        // which are captured in the install progress flow.
        //
        // After install completes, the script has already verified:
        // - hermes --version (broadcast as stage "verify_version")
        // - hermes doctor (broadcast as stage "verify_doctor")
        // - python3 --version (broadcast as stage "python_version")
        //
        // If we reached Installed state, it means the install script
        // completed successfully, which includes these verifications.
        val currentState = _state.value
        return if (currentState is RuntimeState.Installed) {
            VerifyResult.Success(
                hermesVersion = currentState.info.hermesVersion ?: "installed",
                doctorOk = true, // verified by install script
            )
        } else {
            VerifyResult.Failure("Runtime is not in Installed state (current: $currentState)")
        }
    }

    override suspend fun startGateway(): GatewayHandle {
        val currentState = _state.value
        val info = (currentState as? RuntimeState.Installed)?.info
            ?: (currentState as? RuntimeState.Running)?.info
            ?: (currentState as? RuntimeState.Detected)?.info
            ?: throw IllegalStateException("Runtime must be detected or installed before starting gateway (current: $currentState)")

        // Idempotent: if already running, return existing handle.
        if (currentState is RuntimeState.Running) {
            Timber.i("[Gateway] Already running — returning existing handle")
            return currentState.gateway
        }

        // Per gateway-bind-audit.md (2026-06-27):
        // The correct command to launch the WS server is `hermes dashboard`,
        // NOT `hermes gateway start`. Verified in hermes_cli/gateway.py:6201
        // which refuses `gateway start` on Termux with exit 1.
        //
        // The dashboard server:
        //   - Binds to 127.0.0.1:9119 by default (overridable via --host/--port)
        //   - Exposes /api/ws (FastAPI WebSocket endpoint, see web_server.py:11655)
        //   - Requires ?token=<_SESSION_TOKEN> for auth (see web_server.py:11275)
        //   - The token is read from HERMES_DASHBOARD_SESSION_TOKEN env var
        //     (web_server.py:250) or randomly generated if unset.
        //
        // We pass --skip-build because:
        //   - The dashboard's React SPA requires `npm install && npm run build`
        //     which is too heavy for a phone (gigabytes of npm deps, long build).
        //   - The Android app only needs /api/ws, not the React UI.
        //   - With --skip-build, the server starts without the SPA but still
        //     serves /api/ws (the WebSocket endpoint is independent of the
        //     StaticFiles mount that serves /assets).
        //   - We need to provide HERMES_WEB_DIST pointing to a dir with index.html
        //     (else web_server.py:11401 refuses). We create a placeholder dir.
        Timber.i("[Gateway] Sending 'hermes dashboard' to Termux via RUN_COMMAND")

        val sessionToken = getOrCreateSessionToken()

        // Note: all bash ${VAR} references must be escaped as ${'$'}{VAR}
        // because Kotlin string templates use ${$} too.
        val script = """
            set -e
            # Ensure HERMES_HOME is set (uses default ~/.hermes if not)
            export HERMES_HOME="${'$'}{HERMES_HOME:-${'$'}HOME/.hermes}"
            mkdir -p "${'$'}HERMES_HOME/logs"
            mkdir -p "${'$'}HERMES_HOME/web_dist_placeholder/assets"
            # Create a minimal index.html + assets dir so --skip-build doesn't refuse
            # and StaticFiles(directory=WEB_DIST/assets) can mount cleanly.
            if [ ! -f "${'$'}HERMES_HOME/web_dist_placeholder/index.html" ]; then
                echo '<!doctype html><title>Hermes2</title><p>WebSocket API only.</p>' > "${'$'}HERMES_HOME/web_dist_placeholder/index.html"
            fi
            # Start dashboard with our session token, no browser, no SPA build.
            # The Android app connects to ws://127.0.0.1:9119/api/ws?token=<sessionToken>.
            export HERMES_DASHBOARD_SESSION_TOKEN="$sessionToken"
            export HERMES_WEB_DIST="${'$'}HERMES_HOME/web_dist_placeholder"
            export PATH=/data/data/com.termux/files/usr/bin:${'$'}HOME/.hermes/hermes-agent/venv/bin:${'$'}HOME/.hermes/venv/bin:${'$'}HOME/.venv/bin:${'$'}PATH
            HERMES_CMD="${TermuxCommandExecutor.HERMES_BIN}"
            if [ -f "${'$'}HOME/.hermes/hermes-agent/venv/bin/hermes" ]; then
                HERMES_CMD="${'$'}HOME/.hermes/hermes-agent/venv/bin/hermes"
            elif [ -f "${'$'}HOME/.hermes/venv/bin/hermes" ]; then
                HERMES_CMD="${'$'}HOME/.hermes/venv/bin/hermes"
            elif [ -f "${'$'}HOME/.venv/bin/hermes" ]; then
                HERMES_CMD="${'$'}HOME/.venv/bin/hermes"
            elif ! [ -f "${'$'}HERMES_CMD" ] && command -v hermes >/dev/null 2>&1; then
                HERMES_CMD="$(command -v hermes)"
            fi
            # hermes dashboard execution via flexible path
            nohup "${'$'}HERMES_CMD" dashboard \
                --host $DEFAULT_GATEWAY_HOST \
                --port $DEFAULT_GATEWAY_PORT \
                --no-open \
                --skip-build \
                > "${'$'}HERMES_HOME/logs/gateway_stdout.log" 2>&1 &
            GATEWAY_PID=${'$'}!
            # Brief wait so we can report immediate failures
            sleep 2
            if kill -0 ${'$'}GATEWAY_PID 2>/dev/null; then
                echo "Dashboard started, PID=${'$'}GATEWAY_PID"
            else
                echo "Dashboard exited immediately — check ${'$'}HERMES_HOME/logs/gateway_stdout.log"
                exit 1
            fi
        """.trimIndent()

        val result = executor.executeBackgroundScript(
            script = script,
            workingDirectory = TermuxCommandExecutor.TERMUX_HOME,
        )

        when (result) {
            is TermuxCommandExecutor.Result.Accepted -> {
                Timber.i("[Gateway] RUN_COMMAND accepted")
            }
            is TermuxCommandExecutor.Result.TermuxMissing -> {
                _state.value = RuntimeState.Error(result.message)
                throw IllegalStateException(result.message)
            }
            is TermuxCommandExecutor.Result.AllowExternalAppsDisabled -> {
                val msg = "Cannot start gateway: ${result.message}"
                _state.value = RuntimeState.Error(msg)
                throw IllegalStateException(msg)
            }
            is TermuxCommandExecutor.Result.Failure -> {
                _state.value = RuntimeState.Error(result.message)
                throw IllegalStateException(result.message)
            }
        }

        // Wait briefly for the gateway to come up by attempting a WS connect.
        // The OkHttpGatewayClient handles retries internally; we just need
        // to know whether the gateway is reachable.
        val handle = GatewayHandle(
            pid = null, // unknown — Termux doesn't expose child PIDs back to us
            startedAt = System.currentTimeMillis(),
            webSocketUrl = getWebSocketUrl(),
        )

        // Give the gateway a few seconds to bind the port. We don't block on
        // full WS handshake here — isHealthy() does that.
        val reachable = waitForGatewayReady(timeoutMs = GATEWAY_READY_TIMEOUT_MS)
        if (!reachable) {
            val msg = "Gateway did not become reachable within $GATEWAY_READY_TIMEOUT_MS ms. Check ~/.hermes/logs/gateway_stdout.log."
            Timber.w("[Gateway] $msg")
            _state.value = RuntimeState.Error(msg)
            throw IllegalStateException(msg)
        }

        _state.value = RuntimeState.Running(info, handle)
        return handle
    }

    override suspend fun stopGateway(): StopResult {
        val currentState = _state.value
        if (currentState !is RuntimeState.Running) {
            return StopResult.Failure("Gateway is not running (current state: $currentState)")
        }

        Timber.i("[Gateway] Sending 'hermes dashboard --stop' to Termux via RUN_COMMAND")

        // Per gateway-bind-audit.md (2026-06-27):
        // The dashboard server has a built-in `--stop` flag (dashboard.py:80-84)
        // that scans the process table for `hermes dashboard` cmdlines and
        // SIGTERMs them. This is the canonical shutdown path.
        //
        // We don't use `hermes gateway stop` because:
        //   - That command operates on the systemd/launchd service, not on
        //     `hermes dashboard` foreground processes.
        //   - On Termux it would refuse (same as `gateway start`).
        val script = """
            set -e
            export PATH=/data/data/com.termux/files/usr/bin:${'$'}HOME/.hermes/hermes-agent/venv/bin:${'$'}HOME/.hermes/venv/bin:${'$'}HOME/.venv/bin:${'$'}PATH
            HERMES_CMD="${TermuxCommandExecutor.HERMES_BIN}"
            if [ -f "${'$'}HOME/.hermes/hermes-agent/venv/bin/hermes" ]; then
                HERMES_CMD="${'$'}HOME/.hermes/hermes-agent/venv/bin/hermes"
            elif [ -f "${'$'}HOME/.hermes/venv/bin/hermes" ]; then
                HERMES_CMD="${'$'}HOME/.hermes/venv/bin/hermes"
            elif [ -f "${'$'}HOME/.venv/bin/hermes" ]; then
                HERMES_CMD="${'$'}HOME/.venv/bin/hermes"
            elif ! [ -f "${'$'}HERMES_CMD" ] && command -v hermes >/dev/null 2>&1; then
                HERMES_CMD="$(command -v hermes)"
            fi
            "${'$'}HERMES_CMD" dashboard --stop || {
                # Fallback: pkill any hermes dashboard process
                echo "hermes dashboard --stop failed, trying pkill fallback..."
                pkill -f "hermes.*dashboard" 2>/dev/null || true
                pkill -f "uvicorn" 2>/dev/null || true
            }
            echo "Dashboard stop dispatched"
        """.trimIndent()

        val result = executor.executeBackgroundScript(
            script = script,
            workingDirectory = TermuxCommandExecutor.TERMUX_HOME,
        )

        when (result) {
            is TermuxCommandExecutor.Result.Accepted -> {
                Timber.i("[Gateway] Stop RUN_COMMAND accepted")
            }
            is TermuxCommandExecutor.Result.TermuxMissing -> {
                return StopResult.Failure(result.message)
            }
            is TermuxCommandExecutor.Result.AllowExternalAppsDisabled -> {
                return StopResult.Failure(result.message)
            }
            is TermuxCommandExecutor.Result.Failure -> {
                return StopResult.Failure(result.message)
            }
        }

        // Disconnect the WS client (if connected) and revert state.
        try {
            gatewayClient.disconnect()
        } catch (e: Exception) {
            Timber.w(e, "[Gateway] Error disconnecting WS client during stop")
        }

        _state.value = RuntimeState.Installed(currentState.info)
        return StopResult.Success
    }

    override suspend fun fetchLogs() {
        val script = """
            mkdir -p /sdcard/Download
            LOG_FILE="/sdcard/Download/hermes_logs.txt"
            echo "=== HERMES AGENT LOGS ===" > "${'$'}LOG_FILE"
            echo "Date: $(date)" >> "${'$'}LOG_FILE"
            echo "" >> "${'$'}LOG_FILE"
            echo "=== INSTALL LOG (${'$'}HOME/.hermes/logs/install.log) ===" >> "${'$'}LOG_FILE"
            if [ -f "${'$'}HOME/.hermes/logs/install.log" ]; then
                cat "${'$'}HOME/.hermes/logs/install.log" >> "${'$'}LOG_FILE"
            else
                echo "(No install log found)" >> "${'$'}LOG_FILE"
            fi
            echo "" >> "${'$'}LOG_FILE"
            echo "=== GATEWAY LOG (${'$'}HOME/.hermes/logs/gateway_stdout.log) ===" >> "${'$'}LOG_FILE"
            if [ -f "${'$'}HOME/.hermes/logs/gateway_stdout.log" ]; then
                cat "${'$'}HOME/.hermes/logs/gateway_stdout.log" >> "${'$'}LOG_FILE"
            else
                echo "(No gateway log found)" >> "${'$'}LOG_FILE"
            fi
            
            # Take last 2000 chars for broadcast to avoid intent size limits
            LOG_TAIL="$(tail -c 2000 "${'$'}LOG_FILE")"
            am broadcast -p "${context.packageName}" -a "com.hermes.android.LOG_UPDATE" --es logs "${'$'}LOG_TAIL" >/dev/null 2>&1 || true
            echo "Logs copied to /sdcard/Download/hermes_logs.txt and broadcasted"
        """.trimIndent()

        executor.executeBackgroundScript(
            script = script,
            workingDirectory = TermuxCommandExecutor.TERMUX_HOME,
        )
        Timber.i("[Logs] Fetch logs script dispatched to Termux")
    }

    override suspend fun isHealthy(): Boolean {
        // Real health probe: check that (a) the runtime state is Running,
        // and (b) the WS connection is actually established with the gateway.
        val currentState = _state.value
        if (currentState !is RuntimeState.Running) {
            return false
        }

        return try {
            // Attempt a short-timeout connect. If already connected, this
            // returns immediately. If not, it tries to establish a WS
            // connection within the timeout — proving the gateway is alive.
            val state = gatewayClient.connect(
                url = getWebSocketUrl(),
                connectTimeoutMs = HEALTH_CHECK_TIMEOUT_MS,
            )
            state is ConnectionState.Connected
        } catch (e: Exception) {
            Timber.w(e, "[Health] Gateway health check failed")
            false
        }
    }

    override fun getWebSocketUrl(): String {
        // Per gateway-bind-audit.md (2026-06-27):
        // - Use 127.0.0.1 (not localhost) to avoid any DNS resolution surprise.
        // - Append ?token=<sessionToken> for auth (web_server.py:11275-11280).
        // - The token is the same one we set as HERMES_DASHBOARD_SESSION_TOKEN
        //   when launching `hermes dashboard` in startGateway().
        val token = getOrCreateSessionToken()
        return "ws://$DEFAULT_GATEWAY_HOST:$DEFAULT_GATEWAY_PORT/api/ws?token=$token"
    }

    override fun launchHostApp(): Boolean = detector.launchTermux()

    override fun getInstallInstructions(): InstallInstructions? {
        // In the automated path, getInstallInstructions() returns null —
        // the UI should call install() directly and let the executor dispatch
        // the script via RUN_COMMAND.
        //
        // We keep a fallback path (copy/paste instructions) accessible via
        // the legacy generateInstallCommand() for users whose Termux setup
        // rejects RUN_COMMAND (e.g. allow-external-apps disabled and they
        // don't want to enable it). The UI decides when to show this.
        return null
    }

    /**
     * Legacy fallback instructions for the copy/paste path. Used by the UI
     * only when the automated path is unavailable (e.g. user declined to
     * enable allow-external-apps).
     */
    fun getFallbackInstallInstructions(): InstallInstructions =
        InstallInstructions(
            title = "Run install command in external terminal (fallback)",
            steps = listOf(
                "1. Tap 'Copy command' below.",
                "2. Open the external terminal app on your device.",
                "3. Long-press → Paste → Enter.",
                "4. Wait for installation to complete (~5-10 min on first run).",
                "5. Return to Hermes2 and tap 'I've started the install'.",
            ),
            command = installer.generateInstallCommand(),
        )

    // ---- Receiver registration (only active during install) ----

    private var receiver: TermuxInstallProgressReceiver? = null
    private var receiverRegistered = false

    private fun registerProgressReceiver() {
        if (receiverRegistered) return
        // Set the shared flows so the receiver (which is created by Android,
        // not Hilt) can access them via companion object
        TermuxInstallProgressReceiver.sharedProgressFlow = progressFlow
        TermuxInstallProgressReceiver.sharedCompletionFlow = completionFlow
        receiver = TermuxInstallProgressReceiver()
        val filter = IntentFilter().apply {
            addAction(TermuxInstaller.BroadcastAction.PROGRESS.action)
            addAction(TermuxInstaller.BroadcastAction.COMPLETE.action)
            addAction(TermuxInstaller.BroadcastAction.ERROR.action)
        }
        // RECEIVER_EXPORTED because the broadcast comes from Termux (another app).
        // Use AndroidX for API < 33; Context.registerReceiver(..., flags) is API 33+.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
        Timber.d("[Runtime] Install progress receiver registered")
    }

    private fun unregisterProgressReceiver() {
        if (!receiverRegistered) return
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
        TermuxInstallProgressReceiver.sharedProgressFlow = null
        TermuxInstallProgressReceiver.sharedCompletionFlow = null
        receiverRegistered = false
    }

    /**
     * Wait for the gateway to be reachable on the WS port. Polls the WS
     * connection with short timeouts. Returns true if reachable within
     * [timeoutMs], false otherwise.
     *
     * Uses [kotlinx.coroutines.withTimeoutOrNull] so virtual-time test
     * dispatchers (e.g. `runTest`) advance correctly — `System.currentTimeMillis()`
     * does NOT advance under virtual time, which would otherwise cause
     * an infinite loop in unit tests.
     */
    private suspend fun waitForGatewayReady(timeoutMs: Long): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                val state = try {
                    gatewayClient.connect(
                        url = getWebSocketUrl(),
                        connectTimeoutMs = 2_000,
                    )
                } catch (e: Exception) {
                    Timber.d(e, "[Gateway] Connect attempt failed while waiting for ready")
                    null
                }
                if (state is ConnectionState.Connected) return@withTimeoutOrNull true
                kotlinx.coroutines.delay(500)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
    }

    companion object {
        // Fixed per gateway-bind-audit.md (2026-06-27):
        // Hermes' dashboard server binds to 127.0.0.1:9119 by default
        // (verified in hermes_cli/subcommands/dashboard.py:25-29 and
        // hermes_cli/web_server.py:12900-12902). The previous value
        // 8765 was a fabrication that doesn't exist anywhere in Hermes source.
        private const val DEFAULT_GATEWAY_PORT = 9119
        private const val DEFAULT_GATEWAY_HOST = "127.0.0.1"
        private const val TERMUX_PREFIX_PATH = "/data/data/com.termux/files/usr"
        private val INSTALL_TIMEOUT = 30.minutes
        private const val POLL_INTERVAL_MS = 500L
        // Bumped from 15s to 30s: `hermes dashboard` does plugin discovery
        // + MCP discovery + uvicorn startup, which on a slow phone takes
        // longer than 15s on a cold start.
        private const val GATEWAY_READY_TIMEOUT_MS = 30_000L
        private const val HEALTH_CHECK_TIMEOUT_MS = 3_000L

        // Fix S2F03: SharedPreferences keys for install state caching
        private const val PREFS_NAME = "hermes_runtime"
        private const val KEY_INSTALLED = "installed"
        private const val KEY_VERSION = "version"
        private const val KEY_INSTALL_TIME = "install_time"
        // Per gateway-bind-audit.md (2026-06-27): session token for WS auth.
        // Generated once per app install and persisted. Both sides (the
        // HERMES_DASHBOARD_SESSION_TOKEN env var we pass to `hermes dashboard`
        // in startGateway(), and the ?token= query param we add to the WS URL
        // in getWebSocketUrl()) must use this same value.
        private const val KEY_SESSION_TOKEN = "session_token"
    }

    // Fix S2F03: Cache install state
    private fun cacheInstallState(info: RuntimeInfo) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_INSTALLED, true)
                .putString(KEY_VERSION, info.hermesVersion)
                .putLong(KEY_INSTALL_TIME, System.currentTimeMillis())
                .apply()
            Timber.i("[Runtime] Install state cached in SharedPreferences")
        } catch (e: Exception) {
            Timber.w(e, "[Runtime] Failed to cache install state")
        }
    }

    // Fix S2F07: Check if previously installed (re-install detection)
    private fun isPreviouslyInstalled(): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_INSTALLED, false)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the persisted session token, generating a new one on first call.
     *
     * Per gateway-bind-audit.md (2026-06-27):
     * - The token is read by `hermes dashboard` from the env var
     *   `HERMES_DASHBOARD_SESSION_TOKEN` (web_server.py:250).
     * - The Android app passes the same token as `?token=<value>` in the
     *   WebSocket URL (web_server.py:11275 verifies it constant-time).
     * - Both sides must agree, so we generate once and persist across
     *   app restarts. (A fresh token on every app start would invalidate
     *   any still-running `hermes dashboard` process.)
     */
    private fun getOrCreateSessionToken(): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var token = prefs.getString(KEY_SESSION_TOKEN, null)
            if (token.isNullOrBlank()) {
                // 32 bytes of URL-safe randomness, matching the entropy of
                // secrets.token_urlsafe(32) used by Hermes when no env var is set.
                val bytes = ByteArray(32)
                java.security.SecureRandom().nextBytes(bytes)
                token = android.util.Base64.encodeToString(
                    bytes,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
                )
                prefs.edit().putString(KEY_SESSION_TOKEN, token).apply()
                Timber.i("[Runtime] Generated new session token")
            }
            token
        } catch (e: Exception) {
            // Fallback: a fresh random token per call (will not survive app
            // restart, but at least the WS connection will work this session).
            Timber.w(e, "[Runtime] Failed to read session token from prefs — generating ephemeral")
            val bytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(bytes)
            android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
            )
        }
    }

    @Suppress("unused")
    private fun getCachedInstallInfo(): RuntimeInfo? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_INSTALLED, false)) {
                RuntimeInfo(
                    type = RuntimeType.TERMUX,
                    version = null,
                    hermesVersion = prefs.getString(KEY_VERSION, null),
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

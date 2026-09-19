package com.hermes.android.runtime.linux

import android.content.Context
import android.content.Intent
import android.os.StatFs
import com.hermes.android.gateway.StdioGatewayHub
import com.hermes.android.runtime.DetectionResult
import com.hermes.android.runtime.GatewayHandle
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.InstallInstructions
import com.hermes.android.runtime.InstallProgress
import com.hermes.android.runtime.InstallResult
import com.hermes.android.runtime.PrerequisiteResult
import com.hermes.android.runtime.ProgressEmitter
import com.hermes.android.runtime.RuntimeInfo
import com.hermes.android.runtime.RuntimeState
import com.hermes.android.runtime.RuntimeType
import com.hermes.android.runtime.StopResult
import com.hermes.android.runtime.VerifyResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hermes inside the bundled Alpine rootfs run by proot — no Termux or other host app needed.
 * Mirrors Aether's bundled-Linux design: like Aether's `node bridge.mjs`, the gateway is a
 * child process (`python -m tui_gateway.entry`) spoken to over stdin/stdout via
 * [StdioGatewayHub] — no web server, port, token, or dial-and-retry.
 */
@Singleton
class ProotLinuxRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val environment: ProotEnvironment,
    private val rootfsInstaller: RootfsInstaller,
    private val stdioHub: StdioGatewayHub,
    private val desktop: LinuxDesktop,
) : HermesRuntime {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        stdioHub.fileRoot = environment.rootfsDir
        // The gateway client reconnecting while no process runs (e.g. it exited) restarts it —
        // but not after a deliberate stop, which leaves the state Installed.
        stdioHub.restartHandler = handler@{
            val state = _state.value
            if (state !is RuntimeState.Error && state !is RuntimeState.Running) return@handler
            scope.launch {
                runCatching { startGateway() }.onFailure { Timber.w(it, "[Linux] Gateway restart failed") }
            }
        }
    }

    override val type: RuntimeType = RuntimeType.PROOT_LINUX

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.NotDetected)
    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private val _installProgress = MutableStateFlow<InstallProgress?>(null)
    override val installProgress: StateFlow<InstallProgress?> = _installProgress.asStateFlow()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val installMutex = Mutex()
    private val gatewayMutex = Mutex()

    @Volatile
    private var gatewayProcess: Process? = null

    override suspend fun detect(): DetectionResult {
        if (_state.value is RuntimeState.Running && gatewayProcess?.isAlive == true) {
            return DetectionResult.Available((_state.value as RuntimeState.Running).info)
        }
        if (_state.value is RuntimeState.Installing) return DetectionResult.Available(currentInfo())
        _state.value = RuntimeState.Detecting
        if (!environment.isSupportedDevice) {
            val reason = "The built-in Linux runtime needs a 64-bit ARM or x86_64 device."
            _state.value = RuntimeState.Error(reason)
            return DetectionResult.Incompatible(reason)
        }
        val info = currentInfo()
        _state.value = if (isHermesInstalled()) RuntimeState.Installed(info) else RuntimeState.Detected(info)
        return DetectionResult.Available(info)
    }

    override suspend fun checkInstallPrerequisites(): PrerequisiteResult {
        if (!environment.isSupportedDevice) {
            return PrerequisiteResult.Blocked(
                title = "Device not supported",
                instructions = "The built-in Linux runtime needs a 64-bit ARM or x86_64 device. Use the Termux runtime instead.",
            )
        }
        val free = freeBytes()
        if (free < MIN_FREE_BYTES) {
            return PrerequisiteResult.Blocked(
                title = "Not enough storage",
                instructions = "Installing Alpine + Hermes needs about ${MIN_FREE_BYTES / 1_000_000_000} GB free; " +
                    "only ${free / 1_000_000} MB is available.",
            )
        }
        return PrerequisiteResult.Ready
    }

    override suspend fun install(progressEmitter: ProgressEmitter): InstallResult = installMutex.withLock {
        _state.value = RuntimeState.Installing
        val installLog = StringBuilder()
        fun report(stage: String, message: String, percent: Int?) {
            val progress = InstallProgress(stage, message, percent, System.currentTimeMillis())
            _installProgress.value = progress
            progressEmitter.emit(progress)
        }

        return try {
            if (!environment.isRootfsInstalled) {
                rootfsInstaller.install { message -> report("rootfs", message, 5) }
            }

            report("packages", "Installing Python and git (apk)…", 10)
            runStage("packages", PACKAGES_SCRIPT, 10, 25, installLog, ::report, verify = PACKAGES_VERIFY)

            report("node", "Installing Node.js and npm (apk)…", 26)
            runStage("node", NODE_SCRIPT, 26, 30, installLog, ::report, verify = NODE_VERIFY)

            report("hermes", "Downloading Hermes Agent and its Python packages…", 31)
            runStage("hermes", HERMES_INSTALL_SCRIPT, 31, 95, installLog, ::report)

            report("verify", "Checking hermes --version…", 96)
            val version = readHermesVersion()
                ?: throw InstallFailure("Hermes command not found after install.")
            val info = currentInfo().copy(hermesVersion = version)
            prefs.edit().putString(KEY_VERSION, version).apply()
            report("complete", "Installation complete! $version", 100)
            _state.value = RuntimeState.Installed(info)
            InstallResult.Success(info)
        } catch (e: CancellationException) {
            _state.value = RuntimeState.Detected(currentInfo())
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[Linux] Install failed")
            val reason = (e as? InstallFailure)?.message ?: "Install failed: ${e.message}"
            _state.value = RuntimeState.Error("$reason — tap Retry to resume.")
            InstallResult.Failure(reason, installLog.toString().takeLast(8_000))
        } finally {
            _installProgress.value = null
        }
    }

    private suspend fun runStage(
        stage: String,
        script: String,
        startPercent: Int,
        endPercent: Int,
        log: StringBuilder,
        report: (String, String, Int?) -> Unit,
        verify: String? = null,
    ) {
        var lines = 0
        val stageLog = StringBuilder()
        val result = environment.run(script) { line ->
            stageLog.appendLine(line)
            val clean = line.replace(AnsiEscape, "").trim()
            if (clean.isNotEmpty()) {
                lines++
                // Line counts are the only progress signal apk/uv give us; creep towards endPercent.
                val span = endPercent - startPercent
                report(stage, clean.take(160), startPercent + span * lines / (lines + 60))
            }
        }
        log.append(stageLog)
        environment.guestFile("/root/.hermes/logs").mkdirs()
        environment.guestFile("/root/.hermes/logs/app-install.log").appendText(stageLog.toString())
        // apk exits 1 with "failed to write database: Permission denied" on Android: it
        // commits its db via O_TMPFILE + linkat(), and SELinux forbids hard links in app
        // storage. The packages are unpacked anyway, so — like Aether's
        // installPackageProfile — accept the stage when the installed tools actually run.
        if (!result.ok && verify != null && environment.run(verify).ok) {
            log.appendLine("[$stage] exit ${result.exitCode}, but '$verify' succeeded — continuing")
            return
        }
        if (!result.ok) {
            throw InstallFailure("Stage '$stage' failed (exit ${result.exitCode}): ${result.output.lines().lastOrNull { it.isNotBlank() }.orEmpty()}")
        }
    }

    override suspend fun verify(): VerifyResult {
        val version = readHermesVersion() ?: return VerifyResult.Failure("hermes --version failed")
        val doctor = environment.run("hermes doctor")
        return VerifyResult.Success(version, doctor.ok)
    }

    override suspend fun startGateway(): GatewayHandle = gatewayMutex.withLock {
        val current = _state.value
        if (current is RuntimeState.Running && gatewayProcess?.isAlive == true) return current.gateway
        if (!isHermesInstalled()) throw IllegalStateException("Install Hermes in the built-in Linux runtime first.")

        stopProcess()
        val logFile = environment.guestFile(GATEWAY_LOG)
        logFile.parentFile?.mkdirs()
        val process = withContext(Dispatchers.IO) {
            environment.processBuilder(command = GATEWAY_SCRIPT, mergeStderr = false)
                .redirectError(ProcessBuilder.Redirect.to(logFile))
                .start()
        }
        gatewayProcess = process
        stdioHub.attach(process) { exitCode ->
            if (gatewayProcess === process) {
                gatewayProcess = null
                if (_state.value is RuntimeState.Running) {
                    _state.value = RuntimeState.Error("Hermes gateway exited ($exitCode)")
                }
            }
        }

        val handle = GatewayHandle(pid = null, startedAt = System.currentTimeMillis(), webSocketUrl = getWebSocketUrl())
        if (!stdioHub.awaitReady(GATEWAY_READY_TIMEOUT_MS)) {
            val tail = runCatching { logFile.readLines().takeLast(15).joinToString("\n") }.getOrDefault("")
            stopProcess()
            val message = "Gateway did not start. Last log lines:\n$tail"
            _state.value = RuntimeState.Error(message)
            throw IllegalStateException(message)
        }
        _state.value = RuntimeState.Running(currentInfo(), handle)
        // The agent's browser is this Alpine's Chromium; bring it up alongside Hermes.
        scope.launch { runCatching { desktop.onHermesStarted() } }
        handle
    }

    override suspend fun stopGateway(): StopResult = gatewayMutex.withLock {
        return try {
            stopProcess()
            // Nothing is left to browse with Hermes down.
            runCatching { desktop.stop() }
            _state.value = if (isHermesInstalled()) RuntimeState.Installed(currentInfo()) else RuntimeState.Detected(currentInfo())
            StopResult.Success
        } catch (e: Exception) {
            StopResult.Failure(e.message ?: "Failed to stop gateway")
        }
    }

    private suspend fun stopProcess() = withContext(Dispatchers.IO) {
        val process = gatewayProcess ?: return@withContext
        gatewayProcess = null
        stdioHub.detach()
        process.destroy()
        if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    override suspend fun fetchLogs() {
        val logs = withContext(Dispatchers.IO) {
            listOf(GATEWAY_LOG, "/root/.hermes/logs/app-install.log").joinToString("\n\n") { path ->
                val file = environment.guestFile(path)
                val body = if (file.isFile) file.readLines().takeLast(200).joinToString("\n") else "(missing)"
                "== $path ==\n$body"
            }
        }
        context.sendBroadcast(
            Intent("com.hermes.android.LOG_UPDATE").setPackage(context.packageName).putExtra("logs", logs),
        )
    }

    override suspend fun runDoctor(): String {
        val result = environment.run("hermes --version; hermes doctor")
        return result.output.replace(AnsiEscape, "")
    }

    override suspend fun isHealthy(): Boolean =
        _state.value is RuntimeState.Running && gatewayProcess?.isAlive == true && stdioHub.isReady

    override fun getWebSocketUrl(): String = StdioGatewayHub.URL

    override fun hostFileForGuestPath(guestPath: String): File? =
        environment.guestFile(guestPath.replaceFirst(Regex("^~(?=/|$)"), "/root"))

    override fun launchHostApp(): Boolean = false

    override fun getInstallInstructions(): InstallInstructions? = null

    private suspend fun readHermesVersion(): String? {
        if (!environment.isRootfsInstalled) return null
        val result = environment.run("hermes --version")
        return result.output.replace(AnsiEscape, "").lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?.takeIf { result.ok }
    }

    private fun isHermesInstalled(): Boolean =
        environment.isRootfsInstalled && prefs.getString(KEY_VERSION, null) != null

    private fun currentInfo() = RuntimeInfo(
        type = RuntimeType.PROOT_LINUX,
        version = "Alpine 3.23 (proot)",
        path = environment.rootfsDir.absolutePath,
        diskFreeBytes = freeBytes(),
        hermesVersion = prefs.getString(KEY_VERSION, null),
    )

    private fun freeBytes(): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)

    private class InstallFailure(message: String) : Exception(message)

    companion object {
        private const val PREFS_NAME = "hermes_linux_runtime"
        private const val KEY_VERSION = "hermes_version"
        private const val GATEWAY_READY_TIMEOUT_MS = 90_000L
        private const val GATEWAY_LOG = "/root/.hermes/logs/gateway_stderr.log"
        private const val MIN_FREE_BYTES = 1_000_000_000L
        private val AnsiEscape = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

        private val PACKAGES_SCRIPT = """
            set -e
            apk add --no-cache --no-chown python3 git curl ca-certificates bash procps-ng libstdc++ libgcc
        """.trimIndent()
        private const val PACKAGES_VERIFY = "python3 --version && git --version && curl --version && bash --version"

        private val NODE_SCRIPT = """
            set -e
            apk add --no-cache --no-chown nodejs npm
        """.trimIndent()
        private const val NODE_VERIFY = "node --version && npm --version"

        // install.sh has no Alpine support, so this mirrors its steps: clone, locked uv sync
        // (every compiled dep ships musllinux aarch64 wheels), config templates, skills.
        private val HERMES_INSTALL_SCRIPT = """
            set -e
            export HERMES_HOME=/root/.hermes
            REPO="${'$'}HERMES_HOME/hermes-agent"
            mkdir -p "${'$'}HERMES_HOME"/bin "${'$'}HERMES_HOME"/logs
            if [ ! -x "${'$'}HERMES_HOME/bin/uv" ]; then
                curl -LsSf --retry 3 https://astral.sh/uv/install.sh | env UV_INSTALL_DIR="${'$'}HERMES_HOME/bin" UV_NO_MODIFY_PATH=1 sh
            fi
            UV="${'$'}HERMES_HOME/bin/uv"
            if [ -d "${'$'}REPO/.git" ]; then
                git -C "${'$'}REPO" pull --ff-only || echo "git pull failed — keeping existing checkout"
            else
                rm -rf "${'$'}REPO"
                git clone --quiet --depth 1 --branch main https://github.com/NousResearch/hermes-agent.git "${'$'}REPO"
            fi
            cd "${'$'}REPO"
            export UV_PYTHON=/usr/bin/python3 UV_PROJECT_ENVIRONMENT="${'$'}REPO/venv" UV_LINK_MODE=copy
            if ! "${'$'}UV" sync --locked --no-dev --extra web; then
                echo "uv.lock sync failed — falling back to resolving from PyPI"
                [ -x venv/bin/python ] || "${'$'}UV" venv venv
                "${'$'}UV" pip install --python venv/bin/python -e '.[web]'
            fi
            ln -sf "${'$'}REPO/venv/bin/hermes" /usr/local/bin/hermes

            mkdir -p "${'$'}HERMES_HOME"/cron "${'$'}HERMES_HOME"/sessions "${'$'}HERMES_HOME"/pairing "${'$'}HERMES_HOME"/hooks \
                "${'$'}HERMES_HOME"/image_cache "${'$'}HERMES_HOME"/audio_cache "${'$'}HERMES_HOME"/memories "${'$'}HERMES_HOME"/skills
            [ -f "${'$'}HERMES_HOME/.env" ] || cp .env.example "${'$'}HERMES_HOME/.env" 2>/dev/null || touch "${'$'}HERMES_HOME/.env"
            chmod 600 "${'$'}HERMES_HOME/.env"
            [ -f "${'$'}HERMES_HOME/config.yaml" ] || cp cli-config.yaml.example "${'$'}HERMES_HOME/config.yaml" 2>/dev/null || true
            venv/bin/python tools/skills_sync.py || cp -r skills/* "${'$'}HERMES_HOME/skills/" 2>/dev/null || true
            echo "git" > .install_method
            "${'$'}UV" cache clean || true
        """.trimIndent()

        // Same launch as Hermes' TUI (ui-tui/src/gatewayClient.ts startSpawnedGateway):
        // `python -m tui_gateway.entry` from the source root, JSON-RPC lines on stdio.
        private val GATEWAY_SCRIPT = """
            export HERMES_HOME=/root/.hermes
            REPO="${'$'}HERMES_HOME/hermes-agent"
            # A gateway orphaned by a previous app process would still be running. Stop it by
            # PID: a pkill -f pattern also matches this script's own command line and kills it.
            PIDFILE="${'$'}HERMES_HOME/gateway.pid"
            if [ -f "${'$'}PIDFILE" ]; then kill "${'$'}(cat "${'$'}PIDFILE")" 2>/dev/null && sleep 1; rm -f "${'$'}PIDFILE"; fi
            echo ${'$'}${'$'} > "${'$'}PIDFILE"
            export PYTHONPATH="${'$'}REPO" HERMES_PYTHON_SRC_ROOT="${'$'}REPO" PYTHONUNBUFFERED=1
            # Tools the agent runs (xdotool, scrot, GUI apps) land on the app's VNC desktop.
            export DISPLAY=:99
            exec "${'$'}REPO/venv/bin/python" -u -m tui_gateway.entry
        """.trimIndent()
    }
}

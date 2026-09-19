package com.hermes.android.runtime.linux

import android.content.Context
import android.content.Intent
import android.os.StatFs
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hermes inside the bundled Alpine rootfs run by proot — no Termux or other host app needed.
 * Mirrors Aether's bundled-Linux design; the app talks to `hermes dashboard` over
 * the same WebSocket API the Termux runtime uses.
 */
@Singleton
class ProotLinuxRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val environment: ProotEnvironment,
    private val rootfsInstaller: RootfsInstaller,
    private val gatewayClient: GatewayClient,
) : HermesRuntime {

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
            runStage("packages", PACKAGES_SCRIPT, 10, 30, installLog, ::report)

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
        gatewayClient.disconnect()
        val logFile = environment.guestFile(GATEWAY_LOG)
        logFile.parentFile?.mkdirs()
        val process = withContext(Dispatchers.IO) {
            environment.processBuilder(
                command = GATEWAY_SCRIPT,
                extraEnv = mapOf("HERMES_DASHBOARD_SESSION_TOKEN" to sessionToken()),
            ).redirectOutput(ProcessBuilder.Redirect.to(logFile)).start()
        }
        process.outputStream.close()
        gatewayProcess = process

        val handle = GatewayHandle(pid = null, startedAt = System.currentTimeMillis(), webSocketUrl = getWebSocketUrl())
        if (!waitForGatewayReady(process)) {
            val tail = runCatching { logFile.readLines().takeLast(15).joinToString("\n") }.getOrDefault("")
            stopProcess()
            val message = "Gateway did not start. Last log lines:\n$tail"
            _state.value = RuntimeState.Error(message)
            throw IllegalStateException(message)
        }
        _state.value = RuntimeState.Running(currentInfo(), handle)
        handle
    }

    override suspend fun stopGateway(): StopResult = gatewayMutex.withLock {
        return try {
            stopProcess()
            _state.value = if (isHermesInstalled()) RuntimeState.Installed(currentInfo()) else RuntimeState.Detected(currentInfo())
            StopResult.Success
        } catch (e: Exception) {
            StopResult.Failure(e.message ?: "Failed to stop gateway")
        }
    }

    private suspend fun stopProcess() = withContext(Dispatchers.IO) {
        val process = gatewayProcess ?: return@withContext
        gatewayProcess = null
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

    override suspend fun isHealthy(): Boolean {
        if (_state.value !is RuntimeState.Running || gatewayProcess?.isAlive != true) return false
        return try {
            gatewayClient.connect(url = getWebSocketUrl(), connectTimeoutMs = 5_000) is ConnectionState.Connected
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override fun getWebSocketUrl(): String = "ws://$GATEWAY_HOST:$GATEWAY_PORT/api/ws?token=${sessionToken()}"

    override fun launchHostApp(): Boolean = false

    override fun getInstallInstructions(): InstallInstructions? = null

    private suspend fun waitForGatewayReady(process: Process): Boolean =
        withTimeoutOrNull(GATEWAY_READY_TIMEOUT_MS) {
            while (process.isAlive) {
                val connected = try {
                    gatewayClient.connect(url = getWebSocketUrl(), connectTimeoutMs = 2_000) is ConnectionState.Connected
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                if (connected) return@withTimeoutOrNull true
                delay(1_000)
            }
            false
        } ?: false

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

    private fun sessionToken(): String {
        prefs.getString(KEY_SESSION_TOKEN, null)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        prefs.edit().putString(KEY_SESSION_TOKEN, token).apply()
        return token
    }

    private class InstallFailure(message: String) : Exception(message)

    companion object {
        private const val PREFS_NAME = "hermes_linux_runtime"
        private const val KEY_VERSION = "hermes_version"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val GATEWAY_HOST = "127.0.0.1"

        // Distinct from the Termux runtime's 9119 so both can coexist without cross-talk.
        private const val GATEWAY_PORT = 9120
        private const val GATEWAY_READY_TIMEOUT_MS = 90_000L
        private const val GATEWAY_LOG = "/root/.hermes/logs/gateway_stdout.log"
        private const val MIN_FREE_BYTES = 1_000_000_000L
        private val AnsiEscape = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

        private val PACKAGES_SCRIPT = """
            set -e
            apk add --no-cache --no-chown python3 git curl ca-certificates bash procps-ng libstdc++ libgcc
        """.trimIndent()

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
                git clone --depth 1 --branch main https://github.com/NousResearch/hermes-agent.git "${'$'}REPO"
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

        private val GATEWAY_SCRIPT = """
            export HERMES_HOME=/root/.hermes
            mkdir -p "${'$'}HERMES_HOME/web_dist_placeholder/assets"
            [ -f "${'$'}HERMES_HOME/web_dist_placeholder/index.html" ] || \
                echo '<!doctype html><title>Hermes2</title><p>WebSocket API only.</p>' > "${'$'}HERMES_HOME/web_dist_placeholder/index.html"
            export HERMES_WEB_DIST="${'$'}HERMES_HOME/web_dist_placeholder"
            # A gateway orphaned by a previous app process would hold the port. The [h] keeps
            # pkill from matching this script's own command line.
            pkill -f "[h]ermes dashboard --host $GATEWAY_HOST --port $GATEWAY_PORT" 2>/dev/null && sleep 1
            exec hermes dashboard --host $GATEWAY_HOST --port $GATEWAY_PORT --no-open --skip-build
        """.trimIndent()
    }
}

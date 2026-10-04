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
import kotlinx.coroutines.async
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
 * Hermes inside the bundled Linux rootfs run by proot — no Termux or other host app needed.
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
        // The gateway client reconnecting while no process runs starts one — after an exit,
        // and on a cold start too. It used to act only in Error/Running, so right after the
        // app launched (state NotDetected) the chat dialled into nothing for a minute or
        // more, until the service happened to start Hermes. A deliberate stop still wins,
        // and a start already under way is left to finish rather than queued behind.
        stdioHub.restartHandler = handler@{
            if (stoppedDeliberately || updating) return@handler
            val state = _state.value
            if (state is RuntimeState.Installing || state is RuntimeState.Detecting) return@handler
            if (gatewayMutex.isLocked) {
                Timber.i("[Linux] Gateway start already in progress — not starting another")
                return@handler
            }
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

    /** Set by [stopGateway]; a reconnecting client must not bring Hermes back after that. */
    @Volatile
    private var stoppedDeliberately = false

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
                instructions = "Installing Hermes needs about ${MIN_FREE_BYTES / 1_000_000_000} GB free; " +
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
            runStage("packages", PACKAGES_SCRIPT, 10, 25, installLog, ::report, verify = PACKAGES_VERIFY, forApk = true)

            report("node", "Installing Node.js and npm (apk)…", 26)
            runStage("node", NODE_SCRIPT, 26, 30, installLog, ::report, verify = NODE_VERIFY, forApk = true)

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

    @Volatile
    private var updating = false

    override val canUpdateHermes: Boolean get() = true

    /**
     * Update Hermes Agent: [HERMES_UPDATE_SCRIPT] brings the checkout to GitHub's main
     * through a chain of fallbacks, then the install script's locked `uv sync` brings the
     * dependencies along. The gateway is stopped for the swap and started again after,
     * also when the update fails, so a failed update still leaves Hermes running.
     */
    override suspend fun updateHermes(progressEmitter: ProgressEmitter): InstallResult =
        // The runtime's own scope: a caller that goes away (the About screen closed)
        // must not cancel the update halfway and leave Hermes stopped.
        scope.async { updateHermesNow(progressEmitter) }.await()

    private suspend fun updateHermesNow(progressEmitter: ProgressEmitter): InstallResult {
        if (!isHermesInstalled()) return InstallResult.Failure("Hermes is not installed yet.")
        val result = installMutex.withLock {
            updating = true
            val log = StringBuilder()
            fun report(stage: String, message: String, percent: Int?) {
                progressEmitter.emit(InstallProgress(stage, message, percent, System.currentTimeMillis()))
            }
            try {
                report("stop", "Stopping Hermes…", 2)
                stopGateway()
                report("update", "Downloading the newest Hermes Agent…", 5)
                // Proot refuses to delete link2symlink entries ("Operation not permitted"), so what
                // the guest can't remove is removed from here: uv caches that past hardlinking
                // poisoned (they break pip and the update), and the .git dirs the repair set aside.
                deleteTree(environment.guestFile("/root/.cache/uv"))
                // Hermes' own package manager keeps its uv cache here and hides every UV_* setting
                // (UV_LINK_MODE=copy included) from uv, so the cache fills with .l2s link entries
                // that the next `hermes update` fails to link ("Operation not permitted").
                deleteTree(environment.guestFile("/root/.hermes/cache/uv"))
                runStage("update", HERMES_UPDATE_SCRIPT, 5, 60, log, ::report)
                environment.guestFile("/root/.hermes/hermes-agent")
                    .listFiles { f -> f.name.startsWith(".git-old-") }
                    ?.forEach(::deleteTree)
                runStage("hermes", "export HERMES_SKIP_PULL=1\n$HERMES_INSTALL_SCRIPT", 60, 90, log, ::report)
                report("verify", "Checking hermes --version…", 92)
                val version = readHermesVersion()
                    ?: throw InstallFailure("Hermes command not found after the update.")
                prefs.edit().putString(KEY_VERSION, version).apply()
                InstallResult.Success(currentInfo().copy(hermesVersion = version))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Linux] Hermes update failed")
                InstallResult.Failure((e as? InstallFailure)?.message ?: "Update failed: ${e.message}", log.toString().takeLast(8_000))
            } finally {
                updating = false
            }
        }
        progressEmitter.emit(InstallProgress("start", "Starting Hermes…", 95, System.currentTimeMillis()))
        runCatching { startGateway() }.onFailure { Timber.w(it, "[Linux] Gateway did not come back after the update") }
        return result
    }

    /** Deletes [root] without following symlinks: link2symlink's links hold absolute host paths. */
    private fun deleteTree(root: File) {
        if (!root.exists() && !java.nio.file.Files.isSymbolicLink(root.toPath())) return
        runCatching {
            java.nio.file.Files.walkFileTree(root.toPath(), object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes) =
                    java.nio.file.FileVisitResult.CONTINUE.also { java.nio.file.Files.deleteIfExists(file) }
                override fun visitFileFailed(file: java.nio.file.Path, exc: java.io.IOException) =
                    java.nio.file.FileVisitResult.CONTINUE.also { java.nio.file.Files.deleteIfExists(file) }
                override fun postVisitDirectory(dir: java.nio.file.Path, exc: java.io.IOException?) =
                    java.nio.file.FileVisitResult.CONTINUE.also { java.nio.file.Files.deleteIfExists(dir) }
            })
        }.onFailure { Timber.w(it, "[Linux] Could not delete ${root.path}") }
    }

    private suspend fun runStage(
        stage: String,
        script: String,
        startPercent: Int,
        endPercent: Int,
        log: StringBuilder,
        report: (String, String, Int?) -> Unit,
        verify: String? = null,
        forApk: Boolean = false,
    ) {
        var lines = 0
        val stageLog = StringBuilder()
        val startedAt = System.currentTimeMillis()
        val result = environment.run(script, forApk = forApk) { line ->
            stageLog.appendLine(line)
            val clean = line.replace(AnsiEscape, "").trim()
            if (clean.isNotEmpty()) {
                lines++
                // Line counts are the only progress signal apk/uv give us; creep towards endPercent.
                val span = endPercent - startPercent
                report(stage, clean.take(160), startPercent + span * lines / (lines + 60))
            }
        }
        // Which stage a slow install spent its time in, for the install log.
        stageLog.appendLine("[$stage] exit ${result.exitCode} after ${(System.currentTimeMillis() - startedAt) / 1000}s")
        log.append(stageLog)
        environment.guestFile("/root/.hermes/logs").mkdirs()
        environment.guestFile("/root/.hermes/logs/app-install.log").appendText(stageLog.toString())
        // proot's --link2symlink stops apk's db commit (O_TMPFILE + linkat) from being denied
        // by SELinux, but if apk still exits non-zero after unpacking everything — like
        // Aether's installPackageProfile — accept the stage when the installed tools run.
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

    override suspend fun startGateway(): GatewayHandle {
        // Hermes' files are being replaced: a gateway started now would load half of
        // the old version and half of the new. Wait for the update to finish.
        if (updating) installMutex.withLock { }
        return startGatewayLocked()
    }

    private suspend fun startGatewayLocked(): GatewayHandle = gatewayMutex.withLock {
        stoppedDeliberately = false
        val current = _state.value
        if (current is RuntimeState.Running && gatewayProcess?.isAlive == true) return current.gateway
        if (!isHermesInstalled()) throw IllegalStateException("Install Hermes in the built-in Linux runtime first.")

        // A start that was cancelled between the process coming up and the state being
        // set leaves a live, ready gateway that nothing claims. stopProcess() below would
        // kill a working Hermes and boot another one from scratch — tens of seconds, to
        // arrive exactly where we already are. Adopt it instead.
        val orphan = gatewayProcess
        if (orphan?.isAlive == true && stdioHub.isReady) {
            val adopted = GatewayHandle(
                pid = null,
                startedAt = System.currentTimeMillis(),
                webSocketUrl = getWebSocketUrl(),
            )
            _state.value = RuntimeState.Running(currentInfo(), adopted)
            Timber.i("[Runtime] Adopted an already-running gateway instead of restarting it")
            return adopted
        }
        // Alive but not ready: a boot whose start was cancelled (its screen or service
        // went away) is still coming up. Killing it would throw that work away and begin
        // again from nothing, so give it the time a boot gets, and adopt it if it arrives.
        if (orphan?.isAlive == true && stdioHub.awaitReady(GATEWAY_READY_TIMEOUT_MS)) {
            val adopted = GatewayHandle(
                pid = null,
                startedAt = System.currentTimeMillis(),
                webSocketUrl = getWebSocketUrl(),
            )
            _state.value = RuntimeState.Running(currentInfo(), adopted)
            Timber.i("[Runtime] Waited for a gateway that was still booting and adopted it")
            return adopted
        }

        stopProcess()
        // The gateway reads gateway.env (the browser's secret address) once, at start.
        desktop.beforeHermesStarts()
        val logFile = environment.guestFile(GATEWAY_LOG)
        logFile.parentFile?.mkdirs()
        val process = withContext(Dispatchers.IO) {
            environment.processBuilder(command = GATEWAY_SCRIPT, mergeStderr = false)
                .redirectError(ProcessBuilder.Redirect.to(logFile))
                .start()
        }
        gatewayProcess = process
        stdioHub.attach(process) { exitCode ->
            // Why Hermes itself went down is in its own stderr, not in the app: put the
            // end of it in the journal next to the exit, where a report will include it.
            val tail = runCatching { logFile.readLines().takeLast(EXIT_LOG_LINES).joinToString("\n") }.getOrDefault("")
            Timber.w("[Linux] Gateway exited ($exitCode); last lines of its log:\n$tail")
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
        handle
    }

    override suspend fun stopGateway(): StopResult = gatewayMutex.withLock {
        stoppedDeliberately = true
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
        version = "Built-in Linux",
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
        private const val EXIT_LOG_LINES = 40
        private const val GATEWAY_LOG = "/root/.hermes/logs/gateway_stderr.log"
        private const val MIN_FREE_BYTES = 1_000_000_000L
        private val AnsiEscape = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

        // A dropped connection used to fail the whole install on its first error. Network steps
        // now try again a few times, and a Retry skips everything already in place.
        private val RETRY_FN = """
            retry() {
                n=1
                until "${'$'}@"; do
                    [ ${'$'}n -ge 5 ] && return 1
                    echo "Network step failed (try ${'$'}n of 5) — trying again in ${'$'}((n * 3))s…"
                    sleep ${'$'}((n * 3))
                    n=${'$'}((n + 1))
                done
            }
            # /etc/apk/cache keeps every downloaded package, so a retry only fetches what is missing.
            apk_add() {
                apk info -e "${'$'}@" >/dev/null 2>&1 && { echo "Already installed: ${'$'}*"; return 0; }
                mkdir -p /etc/apk/cache
                retry apk add --no-chown "${'$'}@"
                rm -f /etc/apk/cache/*.apk
            }
        """.trimIndent()

        private val PACKAGES_SCRIPT = """
            set -e
            $RETRY_FN
            apk_add python3 git curl ca-certificates bash procps-ng libstdc++ libgcc libatomic
        """.trimIndent()
        private const val PACKAGES_VERIFY = "python3 --version && git --version && curl --version && bash --version"

        private val NODE_SCRIPT = """
            set -e
            $RETRY_FN
            apk_add nodejs npm
        """.trimIndent()
        private const val NODE_VERIFY = "node --version && npm --version"

        // Brings the checkout to GitHub's main, stopping at the first way that gets there: Hermes'
        // own `hermes update`, a plain pull, then (after saying what is broken) fresh git data
        // fetched beside the old one. Nothing is moved or reinstalled; the venv and untracked
        // files stay. The install script then syncs the dependencies.
        private val HERMES_UPDATE_SCRIPT = """
            export HERMES_HOME=/root/.hermes
            REPO="${'$'}HERMES_HOME/hermes-agent"
            REMOTE=https://github.com/NousResearch/hermes-agent.git
            BRANCH=main
            # busybox has timeout; if a rootfs ever lacks it, run without a limit rather than fail every step.
            t() { if command -v timeout >/dev/null 2>&1; then timeout "${'$'}@"; else shift; "${'$'}@"; fi; }
            # proot's --link2symlink turns git's link()+unlink() object writes into symlinks holding
            # absolute paths, so a moved or copied .git loses every object. Rename mode writes real files.
            git config --global core.createObject rename
            [ ! -d "${'$'}REPO/.git" ] || git -C "${'$'}REPO" config core.createObject rename 2>/dev/null
            # Success is judged against GitHub itself, never an exit code: HEAD must equal the remote tip.
            TARGET=${'$'}(t 60 git ls-remote "${'$'}REMOTE" "refs/heads/${'$'}BRANCH" 2>/dev/null | cut -f1)
            if [ -z "${'$'}TARGET" ]; then
                echo "Could not reach GitHub — check the internet connection; nothing was changed."
                exit 1
            fi
            echo "Newest Hermes Agent: ${'$'}TARGET"
            # main moves every few minutes, so a HEAD past TARGET (or at a fresher tip) counts too.
            at_target() {
                head=${'$'}(git -C "${'$'}REPO" rev-parse HEAD 2>/dev/null) || return 1
                [ "${'$'}head" = "${'$'}TARGET" ] && return 0
                git -C "${'$'}REPO" merge-base --is-ancestor "${'$'}TARGET" "${'$'}head" 2>/dev/null && return 0
                [ "${'$'}head" = "${'$'}(t 60 git ls-remote "${'$'}REMOTE" "refs/heads/${'$'}BRANCH" 2>/dev/null | cut -f1)" ]
            }
            # hermes update may provision nodejs.org's Node in ~/.hermes/node; one that cannot run (a
            # missing library, or left from the old Alpine rootfs) makes Hermes ignore the system Node.
            drop_broken_node() {
                if [ -e "${'$'}HERMES_HOME/node/bin/node" ] && ! "${'$'}HERMES_HOME/node/bin/node" --version >/dev/null 2>&1; then
                    echo "removing Hermes-managed Node that cannot run here; the system Node stays in use"
                    rm -rf "${'$'}HERMES_HOME/node"
                fi
            }
            healthy() { git -C "${'$'}REPO" fsck --connectivity-only >/dev/null 2>&1; }
            # uv hardlinks by default, and under link2symlink that has left pip in the venv half-installed.
            fix_pip() {
                "${'$'}REPO/venv/bin/python" -m pip --version >/dev/null 2>&1 && return 0
                echo "pip in the venv is broken — reinstalling it"
                UV_LINK_MODE=copy "${'$'}HERMES_HOME/bin/uv" pip install -q --python "${'$'}REPO/venv/bin/python" --reinstall pip || echo "pip reinstall failed"
            }
            done_with() { drop_broken_node; fix_pip; echo "== Updated by: ${'$'}1"; exit 0; }
            at_target && healthy && done_with "nothing to do, already the newest"

            echo "== [1/3] hermes update"
            if [ -x "${'$'}REPO/venv/bin/hermes" ]; then
                (cd "${'$'}REPO" && export UV_LINK_MODE=copy && t 900 "${'$'}REPO/venv/bin/hermes" update --yes --no-gateway-restart </dev/null) || echo "hermes update failed (exit ${'$'}?)"
                drop_broken_node
                at_target && healthy && done_with "hermes update"
            else
                echo "hermes command missing — skipping"
            fi

            echo "== [2/3] git pull from GitHub"
            # A step killed by its timeout can leave git children and index.lock behind; clear both.
            pkill -x git 2>/dev/null && sleep 1
            find "${'$'}REPO/.git" -maxdepth 2 -name '*.lock' -delete 2>/dev/null
            t 600 git -C "${'$'}REPO" pull --ff-only "${'$'}REMOTE" "${'$'}BRANCH" </dev/null || echo "git pull failed (exit ${'$'}?)"
            at_target && healthy && done_with "git pull"

            echo "== [3/3] diagnose and repair the git data"
            if [ ! -d "${'$'}REPO/.git" ]; then
                echo "diagnosis: ${'$'}REPO/.git is missing"
            else
                dangling=${'$'}(find "${'$'}REPO/.git" -type l ! -exec test -e {} \; -print 2>/dev/null | wc -l)
                [ "${'$'}dangling" -eq 0 ] || echo "diagnosis: ${'$'}dangling git file(s) are links to data that is gone (proot link2symlink; the .git was moved or its .l2s files deleted)"
                if healthy; then
                    echo "diagnosis: git data is intact; the pull itself failed:"
                    git -C "${'$'}REPO" status 2>&1 | head -5
                else
                    echo "diagnosis: git data is broken (${'$'}(git -C "${'$'}REPO" fsck --connectivity-only 2>&1 | grep -c missing) missing object(s)):"
                    git -C "${'$'}REPO" fsck --connectivity-only 2>&1 | head -3
                fi
            fi
            # Fetch into a second git dir next to the old one, in real files (rename mode), and only swap
            # once it holds the new version. Code files are then brought to it in place; venv and every
            # untracked file stay where they are. The old .git is renamed, not deleted: proot refuses to
            # rm its link2symlink entries ("Operation not permitted"); the app deletes .git-old-* from outside.
            NEW="${'$'}REPO/.git-new"
            rm -rf "${'$'}NEW" 2>/dev/null || mv "${'$'}NEW" "${'$'}REPO/.git-old-new-${'$'}(date +%s)"
            if git --git-dir="${'$'}NEW" init -q -b "${'$'}BRANCH" &&
                git --git-dir="${'$'}NEW" config core.createObject rename &&
                t 600 git --git-dir="${'$'}NEW" fetch -q --depth 1 "${'$'}REMOTE" "${'$'}BRANCH" </dev/null &&
                [ "${'$'}(git --git-dir="${'$'}NEW" rev-parse FETCH_HEAD)" != "" ]; then
                TARGET=${'$'}(git --git-dir="${'$'}NEW" rev-parse FETCH_HEAD)
                { [ ! -e "${'$'}REPO/.git" ] || mv "${'$'}REPO/.git" "${'$'}REPO/.git-old-${'$'}(date +%s)"; } && mv "${'$'}NEW" "${'$'}REPO/.git" &&
                    git -C "${'$'}REPO" config core.bare false &&
                    git -C "${'$'}REPO" remote add origin "${'$'}REMOTE" 2>/dev/null
                git -C "${'$'}REPO" update-ref "refs/remotes/origin/${'$'}BRANCH" "${'$'}TARGET"
                git -C "${'$'}REPO" reset -q --hard "${'$'}TARGET" && git -C "${'$'}REPO" branch -q -u "origin/${'$'}BRANCH" 2>/dev/null
                at_target && healthy && done_with "git repair"
                echo "repair fetched ${'$'}TARGET but could not check it out"
            else
                echo "could not download fresh git data from GitHub"
                rm -rf "${'$'}NEW"
            fi
            drop_broken_node
            echo "Hermes Agent was not updated — the steps and diagnosis above say why (also in /root/.hermes/logs/app-install.log)"
            exit 1
        """.trimIndent()

        // Mirrors install.sh's steps without its package manager (which fetches its own Python,
        // Node and ffmpeg, over 1 GB): clone, locked uv sync, config templates, skills.
        private val HERMES_INSTALL_SCRIPT = """
            set -e
            $RETRY_FN
            # uv's own retries and read timeout, raised for slow connections that drop now and then.
            export UV_HTTP_RETRIES=10 UV_HTTP_TIMEOUT=120
            export HERMES_HOME=/root/.hermes
            REPO="${'$'}HERMES_HOME/hermes-agent"
            mkdir -p "${'$'}HERMES_HOME"/bin "${'$'}HERMES_HOME"/logs
            if [ ! -x "${'$'}HERMES_HOME/bin/uv" ]; then
                # Not `curl | sh`: the pipe reports sh's success even when the download failed.
                get_uv() {
                    curl -LsSf --retry 3 -o /tmp/uv-install.sh https://astral.sh/uv/install.sh &&
                        env UV_INSTALL_DIR="${'$'}HERMES_HOME/bin" UV_NO_MODIFY_PATH=1 sh /tmp/uv-install.sh &&
                        [ -x "${'$'}HERMES_HOME/bin/uv" ]
                }
                retry get_uv
            fi
            UV="${'$'}HERMES_HOME/bin/uv"
            # Real files for git objects, not link2symlink's absolute-path symlinks (see HERMES_UPDATE_SCRIPT).
            git config --global core.createObject rename
            if [ -d "${'$'}REPO/.git" ]; then
                # An update has already brought the checkout to GitHub's main (HERMES_UPDATE_SCRIPT).
                if [ "${'$'}{HERMES_SKIP_PULL:-}" != 1 ]; then
                    git -C "${'$'}REPO" pull --ff-only || echo "git pull failed — keeping existing checkout"
                fi
            else
                clone() { rm -rf "${'$'}REPO"; git clone --quiet --depth 1 --branch main https://github.com/NousResearch/hermes-agent.git "${'$'}REPO"; }
                retry clone
            fi
            cd "${'$'}REPO"
            # uv.lock only covers the Python in pm/lock.json (3.14 now) and every core dep carries a
            # python_version >= that marker, so an older Python gets Hermes without openai, rich, … .
            # The system Python is used when it is that version; otherwise uv downloads it.
            PY=${'$'}(python3 -c "import json; v = json.load(open('pm/lock.json'))['packages']['python']['version']; print('.'.join(v.split('+')[0].split('.')[:2]))" 2>/dev/null || echo 3.14)
            export UV_PYTHON_INSTALL_DIR="${'$'}HERMES_HOME/python"
            if ! "${'$'}UV" python find --system "${'$'}PY" >/dev/null 2>&1; then
                echo "Downloading Python ${'$'}PY…"
                retry "${'$'}UV" python install --no-bin "${'$'}PY"
            fi
            export UV_PYTHON="${'$'}PY" UV_PROJECT_ENVIRONMENT="${'$'}REPO/venv" UV_LINK_MODE=copy
            if ! retry "${'$'}UV" sync --locked --no-dev --extra web; then
                echo "uv.lock sync failed — falling back to resolving from PyPI"
                [ -x venv/bin/python ] || "${'$'}UV" venv venv
                retry "${'$'}UV" pip install --python venv/bin/python -e '.[web]'
            fi
            mkdir -p /usr/local/bin
            ln -sf "${'$'}REPO/venv/bin/hermes" /usr/local/bin/hermes

            mkdir -p "${'$'}HERMES_HOME"/cron "${'$'}HERMES_HOME"/sessions "${'$'}HERMES_HOME"/pairing "${'$'}HERMES_HOME"/hooks \
                "${'$'}HERMES_HOME"/image_cache "${'$'}HERMES_HOME"/audio_cache "${'$'}HERMES_HOME"/memories "${'$'}HERMES_HOME"/skills
            [ -f "${'$'}HERMES_HOME/.env" ] || cp .env.example "${'$'}HERMES_HOME/.env" 2>/dev/null || touch "${'$'}HERMES_HOME/.env"
            chmod 600 "${'$'}HERMES_HOME/.env"
            [ -f "${'$'}HERMES_HOME/config.yaml" ] || cp cli-config.yaml.example "${'$'}HERMES_HOME/config.yaml" 2>/dev/null || true
            venv/bin/python tools/skills_sync.py || cp -r skills/* "${'$'}HERMES_HOME/skills/" 2>/dev/null || true
            echo "git" > .install_method
            "${'$'}UV" cache clean || true
            # Bytecode for what the gateway actually imports (~370 files), written by one import.
            # Compiling the whole venv and source tree took about 80s more for ~4200 files it never loads.
            echo "Precompiling Hermes for faster startup…"
            PYTHONPATH="${'$'}REPO" venv/bin/python -c "import tui_gateway.server" >/dev/null 2>&1 || true
        """.trimIndent()

        // Same launch as Hermes' TUI (ui-tui/src/gatewayClient.ts startSpawnedGateway):
        // `tui_gateway.entry` from the source root, JSON-RPC lines on stdio.
        //
        // Plus the Group Chat room worker, which `hermes dashboard` starts
        // (hermes_cli/web_server.py) and the stdio entry does not: without it every
        // groups.create / groups.send answers 4123. Its idle re-check goes from 5 s to
        // 60 s. A send, a finished turn, a stop or an approval wakes it at once; the
        // timer only covers writers outside this process. Measured on the 0.21.4
        // gateway: 3 idle rooms cost 3.4% of a core at 5 s, 0.7% at 60 s, and with no
        // rooms the worker costs nothing.
        //
        // Plus Kanban when the user turned it on (LinuxKanban): a stand-in for the
        // dashboard API and the gateway dispatcher, which this runtime has neither of.
        private val GATEWAY_SCRIPT = """
            export HERMES_HOME=/root/.hermes
            REPO="${'$'}HERMES_HOME/hermes-agent"
            # A gateway orphaned by a previous app process would still be running. Stop it by
            # PID: a pkill -f pattern also matches this script's own command line and kills it.
            PIDFILE="${'$'}HERMES_HOME/gateway.pid"
            if [ -f "${'$'}PIDFILE" ]; then kill "${'$'}(cat "${'$'}PIDFILE")" 2>/dev/null && sleep 1; rm -f "${'$'}PIDFILE"; fi
            echo ${'$'}${'$'} > "${'$'}PIDFILE"
            export PYTHONPATH="${'$'}REPO" HERMES_PYTHON_SRC_ROOT="${'$'}REPO" PYTHONUNBUFFERED=1
            # Commands the gateway runs for the app (shell.exec → `python3 -`) must get
            # Hermes' own interpreter, which has PyYAML and the rest; the system
            # python3 does not ("No module named 'yaml'" on the settings screen).
            export VIRTUAL_ENV="${'$'}REPO/venv" PATH="${'$'}REPO/venv/bin:${'$'}PATH"
            # Tools the agent runs (xdotool, scrot, GUI apps) land on the app's VNC desktop.
            export DISPLAY=:99
            # Settings the app owns (BROWSER_CDP_URL, …). Env beats config.yaml, so the app
            # never has to spend seconds in `hermes config set` to change them.
            ENV_FILE="${'$'}HERMES_HOME/android/gateway.env"
            [ -f "${'$'}ENV_FILE" ] && . "${'$'}ENV_FILE"
            # Kanban, when the user turned it on (LinuxKanban).
            [ -f "${'$'}HERMES_HOME/android/kanban.enabled" ] && export HERMES_ANDROID_KANBAN=1
            exec "${'$'}REPO/venv/bin/python" -u -c '
            import logging, os, sys, threading, time
            from tui_gateway import entry, hosted_room_service, methods_groups, server
            hosted_room_service._HOSTED_ROOM_IDLE_FALLBACK_SECONDS = 60.0
            def start_rooms():
                try:
                    methods_groups.start_hosted_room_service()
                except Exception:
                    logging.getLogger("hosted-rooms").exception("Group Chat room worker did not start")
            threading.Thread(target=start_rooms, name="hosted-rooms-start", daemon=True).start()
            # Kanban without a dashboard: android.kanban hands a request to the dashboard Kanban
            # plugin router (plugins/kanban/dashboard/plugin_api.py) in this process, so the app
            # sends the same calls as to a server; a thread does what the gateway dispatcher
            # does, a dispatch every 60 s.
            def install_kanban(repo):
                log = logging.getLogger("android-kanban")
                lock, state = threading.Lock(), {}
                def client():
                    with lock:
                        if "client" not in state:
                            import importlib.util
                            from fastapi import FastAPI
                            from fastapi.testclient import TestClient
                            spec = importlib.util.spec_from_file_location(
                                "android_kanban_plugin_api", os.path.join(repo, "plugins/kanban/dashboard/plugin_api.py"))
                            module = importlib.util.module_from_spec(spec)
                            # FastAPI resolves the request bodies through sys.modules.
                            sys.modules[spec.name] = module
                            spec.loader.exec_module(module)
                            app = FastAPI()
                            app.include_router(module.router)
                            state["client"] = TestClient(app)
                        return state["client"]
                def handle(rid, params):
                    try:
                        response = client().request(
                            str(params.get("method") or "GET").upper(), str(params.get("path") or "/"),
                            json=params.get("body"))
                        return server._ok(rid, {"status": response.status_code, "body": response.json() if response.content else None})
                    except Exception as exc:
                        log.exception("kanban call failed")
                        return server._err(rid, 5900, f"kanban: {exc}")
                server._methods["android.kanban"] = handle
                server._LONG_HANDLERS = server._LONG_HANDLERS | {"android.kanban"}
                def dispatch():
                    from hermes_cli import kanban_db_dispatch
                    time.sleep(15)
                    while True:
                        try:
                            kanban_db_dispatch.reap_worker_zombies()
                            client().post("/dispatch")
                        except Exception:
                            log.exception("kanban dispatch failed")
                        time.sleep(60)
                threading.Thread(target=dispatch, name="android-kanban", daemon=True).start()
            if os.environ.get("HERMES_ANDROID_KANBAN") == "1":
                install_kanban(os.environ["HERMES_PYTHON_SRC_ROOT"])
            entry.main()
            '
        """.trimIndent()
    }
}

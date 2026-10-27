package com.hermes.android.runtime.linux

import android.os.FileObserver
import android.os.Process
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs `apk` for the Linux's own shells (the gateway, so the agent; the terminal) under proot's
 * link2symlink, which only a proot the app starts can have. Android denies every app hard links
 * (SELinux `neverallow all_untrusted_apps file_type:file link`), and apk makes them to save its
 * database (O_TMPFILE + linkat) and for packages that ship them (glibc does): run directly, it
 * puts the files in place and then fails with "failed to write database: Permission denied", so
 * apk no longer knows what is installed. link2symlink for the whole Linux would cover it, but uv
 * breaks under it: it hard-links again from link2symlink's stand-ins ("failed to hardlink …
 * Invalid argument", proot field test run 40490146593).
 *
 * So only apk comes here: /usr/local/sbin/apk (assets/linux/apk-bridge.sh, ahead of /usr/sbin on
 * the PATH) leaves a request in [QUEUE]; this runs the real apk for it the way the app's own
 * package installs run, and the wrapper prints the output and exits with apk's code.
 */
@Singleton
class GuestApkBridge @Inject constructor(
    private val environment: ProotEnvironment,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // One apk at a time: a second one would fail on apk's database lock ("Unable to lock
    // database", exit 99, field test); queued here it waits for the first instead.
    private val oneAtATime = Mutex()

    @Volatile
    private var observer: FileObserver? = null

    /** Cheap once watching; [ProotEnvironment] calls it before every launch that isn't apk's own. */
    fun ensureRunning() {
        if (observer != null || !environment.isRootfsInstalled) return
        synchronized(this) {
            if (observer != null) return
            runCatching { start() }
                .onFailure { Timber.w(it, "[Linux] apk bridge did not start; apk runs directly") }
        }
    }

    fun stop() = synchronized(this) {
        observer?.stopWatching()
        observer = null
    }

    private fun start() {
        val queue = environment.guestFile(QUEUE).apply { mkdirs() }
        check(environment.copyAssetToGuest(WRAPPER_ASSET, WRAPPER_PATH)) { "$WRAPPER_ASSET is missing from this build" }
        Os.chmod(environment.guestFile(WRAPPER_PATH).path, 0b111_101_101)
        val watcher = object : FileObserver(
            queue,
            FileObserver.CREATE or FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or
                FileObserver.DELETE_SELF or FileObserver.MOVE_SELF,
        ) {
            override fun onEvent(event: Int, path: String?) {
                if (event and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) != 0) {
                    // The Linux was reinstalled: the next launch watches the new folder.
                    unwatch(this)
                    return
                }
                path?.takeIf { it.endsWith(READY) }?.let { claim(queue, it.removeSuffix(READY)) }
            }
        }
        watcher.startWatching()
        observer = watcher
        // Requests made while nothing watched; their wrapper waits a few seconds for an answer.
        queue.list()?.filter { it.endsWith(READY) }?.forEach { claim(queue, it.removeSuffix(READY)) }
        Timber.i("[Linux] apk bridge watching ${QUEUE}")
    }

    private fun unwatch(watcher: FileObserver) = synchronized(this) {
        if (observer === watcher) {
            watcher.stopWatching()
            observer = null
        }
    }

    private fun claim(queue: File, id: String) {
        // CREATE and CLOSE_WRITE both report one marker; whoever deletes it serves the request.
        if (!File(queue, id + READY).delete()) return
        val request = File(queue, id)
        if (request.isDirectory) scope.launch { serve(request) }
    }

    private suspend fun serve(request: File) {
        val caller = runCatching { File(request, "pid").readText().trim().toInt() }.getOrNull()
        val out = File(request, "out")
        var exitCode = 1
        try {
            val args = parseArgs(File(request, "args").readBytes())
            val cwd = File(request, "cwd").readText().trim().ifEmpty { "/" }
            // Taken at once, so the wrapper doesn't give up while this waits its turn.
            File(request, "taken").writeText(Process.myPid().toString())
            Timber.i("[Linux] apk for the Linux: ${args.firstOrNull { !it.startsWith("-") } ?: "?"}")
            val command = command(cwd, args, "$QUEUE/${request.name}/apkpid")
            oneAtATime.withLock {
                if (File(request, "cancel").exists() || !isAlive(caller)) return@withLock
                FileOutputStream(out, true).use { stream ->
                    coroutineScope {
                        val watch = launch { stopWhenCallerGoes(request, caller) }
                        exitCode = environment.run(command, mapOf(DIRECT_ENV to "1"), forApk = true) { line ->
                            stream.write("$line\n".toByteArray())
                            stream.flush()
                        }.exitCode
                        watch.cancel()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Linux] apk for the Linux did not run")
            runCatching { out.appendText("apk: the Hermes app could not run apk: ${e.message}\n") }
        } finally {
            runCatching { File(request, "rc").writeText("$exitCode") }
            // A wrapper that was stopped can't clear its request; one still waiting does it itself.
            if (File(request, "cancel").exists() || !isAlive(caller)) request.deleteRecursively()
        }
    }

    /** A caller stopped (Ctrl-C, a tool's timeout) stops its apk, as it would stop a local one. */
    private suspend fun stopWhenCallerGoes(request: File, caller: Int?) {
        while (true) {
            delay(CALLER_CHECK_MS)
            if (File(request, "cancel").exists() || !isAlive(caller)) {
                runCatching { File(request, "apkpid").readText().trim().toInt() }.getOrNull()
                    ?.let { pid -> runCatching { Os.kill(pid, OsConstants.SIGINT) } }
                return
            }
        }
    }

    private fun isAlive(pid: Int?) = pid == null || File("/proc/$pid").exists()

    companion object {
        /** Set on the app's own apk runs, which already have link2symlink. */
        const val DIRECT_ENV = "HERMES_APK_DIRECT"
        const val QUEUE = "/run/hermes-apk"
        const val WRAPPER_PATH = "/usr/local/sbin/apk"
        private const val WRAPPER_ASSET = "linux/apk-bridge.sh"
        private const val READY = ".ready"
        private const val CALLER_CHECK_MS = 500L

        /** The wrapper's `printf '%s\0' "$@"`: every argument ends in a NUL, empty ones included. */
        internal fun parseArgs(bytes: ByteArray): List<String> =
            String(bytes, Charsets.UTF_8).split('\u0000').dropLast(1)

        /**
         * The guest command for one request: apk in the caller's folder, its pid left at [pidFile]
         * so a stopped caller can interrupt it. `exec apk` reaches the wrapper, which hands over to
         * the real apk because [DIRECT_ENV] is set, so the pid stays apk's.
         */
        internal fun command(cwd: String, args: List<String>, pidFile: String, program: String = "apk"): String =
            "cd ${quote(cwd)} 2>/dev/null || cd /; echo $$ > ${quote(pidFile)}; exec $program" +
                args.joinToString("") { " " + quote(it) }

        internal fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}

package com.hermes.android.runtime.linux

import android.content.Context
import android.system.Os
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs commands inside the bundled Linux rootfs via proot, the same approach Aether uses.
 * proot and its loader ship as jniLibs because nativeLibraryDir is the only app location
 * that may execve() on targetSdk >= 29.
 */
@Singleton
class ProotEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val baseDir = File(context.filesDir, "linux")
    val rootfsDir = ProotEnvironment.rootfsDir(context)
    private val hostLibDir = File(baseDir, "lib")
    private val hostTmpDir = File(baseDir, "tmp")
    private val fakeProcDir = File(baseDir, "proc")
    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val prootBinary = File(nativeDir, "libproot.so")
    private val prootLoader = File(nativeDir, "libproot-loader.so")

    /** ABI Android extracted our jniLibs for (nativeLibraryDir ends in lib/arm64 or lib/x86_64). */
    val abi: String? = when (nativeDir.name) {
        "arm64" -> "arm64-v8a"
        "x86_64" -> "x86_64"
        else -> null
    }

    val isSupportedDevice: Boolean
        get() = abi != null && prootBinary.isFile && prootLoader.isFile

    val isRootfsInstalled: Boolean
        get() = File(rootfsDir, READY_MARKER).isFile

    fun markRootfsReady() {
        File(rootfsDir, READY_MARKER).writeText(System.currentTimeMillis().toString())
        LinuxFilesProvider.notifyRootsChanged(context)
    }

    /** Host path of a file inside the guest, e.g. `/root/.hermes/logs/x.log`. */
    fun guestFile(guestPath: String): File = File(rootfsDir, guestPath.removePrefix("/"))

    /**
     * Copies the APK asset [base] (`.gz` or not: the APK build may un-gzip assets) into the
     * guest at [guestPath], always uncompressed. Returns false when the asset is missing.
     */
    fun copyAssetToGuest(base: String, guestPath: String): Boolean {
        val target = guestFile(guestPath).apply { parentFile?.mkdirs() }
        for ((name, gz) in listOf("$base.gz" to true, base to false)) {
            val input = runCatching { context.assets.open(name) }.getOrNull() ?: continue
            input.use { raw ->
                val stream = if (gz) java.util.zip.GZIPInputStream(raw.buffered(), 64 * 1024) else raw
                target.outputStream().use { stream.copyTo(it, 64 * 1024) }
            }
            return true
        }
        return false
    }

    fun processBuilder(
        command: String,
        extraEnv: Map<String, String> = emptyMap(),
        mergeStderr: Boolean = true,
    ): ProcessBuilder =
        ProcessBuilder(prootArgs(listOf("/bin/sh", "-lc", command), extraEnv)).apply {
            directory(baseDir)
            redirectErrorStream(mergeStderr)
            environment().apply {
                remove("LD_PRELOAD")
                putAll(hostEnv())
            }
        }

    /**
     * An interactive Linux shell for a terminal emulator (Aether's `createTerminalLaunchSpec`):
     * the same proot invocation as [processBuilder], but `sh -i` on the emulator's pty.
     */
    fun terminalLaunchSpec(): TerminalLaunchSpec {
        val shellEnv = mapOf(
            "HERMES_HOME" to "/root/.hermes",
            "COLORTERM" to "truecolor",
            "PS1" to "hermes:\\w# ",
        )
        val args = prootArgs(listOf("/bin/sh", "-i"), shellEnv)
        // The emulator replaces the whole environment, so the host side needs PATH too.
        val env = hostEnv() + mapOf("PATH" to "/system/bin", "HOME" to baseDir.absolutePath, "TERM" to "xterm-256color")
        return TerminalLaunchSpec(
            executable = args.first(),
            arguments = args.toTypedArray(),
            environment = env.map { (key, value) -> "$key=$value" }.toTypedArray(),
            workingDirectory = baseDir.absolutePath,
        )
    }

    private fun prootArgs(guestCommand: List<String>, extraEnv: Map<String, String>): List<String> {
        prepareHost()
        val args = mutableListOf(
            prootBinary.absolutePath,
            "--kill-on-exit",
            // apk-tools 3 commits its db with O_TMPFILE + linkat() on /proc/self/fd, which
            // Android denies in app-private storage ("failed to write database: Permission
            // denied"). proot's handler copies such an fd into a real file instead.
            "--link2symlink",
            "-0",
            "-r", rootfsDir.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/proc/self/fd:/dev/fd",
            "-b", "${File(rootfsDir, "tmp").absolutePath}:/dev/shm",
        )
        for (name in FakeProcFiles.keys) {
            if (!isReadable(File("/proc/$name"))) {
                args += listOf("-b", "${File(fakeProcDir, name).absolutePath}:/proc/$name")
            }
        }
        args += listOf("-w", "/root", "/usr/bin/env", "-i")
        // The rootfs has no zoneinfo and `env -i` drops the host's zone, so everything in it ran
        // on UTC: a cron job set for 09:00 fired at 12:30 in Tehran, and the agent read the clock
        // three and a half hours off. The C library takes a POSIX TZ string without any zone database.
        val zone = mapOf("TZ" to posixTimeZone(java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())))
        (GuestEnv + zone + extraEnv).forEach { (key, value) -> args += "$key=$value" }
        args += guestCommand
        return args
    }

    private fun hostEnv(): Map<String, String> = mapOf(
        "PROOT_TMP_DIR" to hostTmpDir.absolutePath,
        "PROOT_LOADER" to prootLoader.absolutePath,
        "LD_LIBRARY_PATH" to "${hostLibDir.absolutePath}:${nativeDir.absolutePath}",
    )

    /** Runs [command] to completion, streaming each output line to [onLine]. */
    suspend fun run(
        command: String,
        extraEnv: Map<String, String> = emptyMap(),
        onLine: (String) -> Unit = {},
    ): CommandResult = withContext(Dispatchers.IO) {
        val process = processBuilder(command, extraEnv).start()
        process.outputStream.close()
        val tail = ArrayDeque<String>()
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    currentCoroutineContext().ensureActive()
                    onLine(line)
                    tail.addLast(line)
                    if (tail.size > OUTPUT_TAIL_LINES) tail.removeFirst()
                }
            }
            CommandResult(process.waitFor(), tail.joinToString("\n"))
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun prepareHost() {
        hostTmpDir.mkdirs()
        hostLibDir.mkdirs()
        // proot is linked against libtalloc.so.2, but the APK can only ship lib*.so names.
        val talloc = File(hostLibDir, "libtalloc.so.2")
        val target = File(nativeDir, "libtalloc.so").absolutePath
        if (runCatching { Os.readlink(talloc.absolutePath) }.getOrNull() != target) {
            talloc.delete()
            Os.symlink(target, talloc.absolutePath)
        }
        fakeProcDir.mkdirs()
        FakeProcFiles.forEach { (name, content) ->
            val file = File(fakeProcDir, name)
            if (!file.isFile) file.writeText(content)
        }
    }

    private fun isReadable(file: File): Boolean =
        runCatching { file.inputStream().use { it.read() } }.isSuccess

    class TerminalLaunchSpec(
        val executable: String,
        val arguments: Array<String>,
        val environment: Array<String>,
        val workingDirectory: String,
    )

    data class CommandResult(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
    }

    companion object {
        /** The built-in Linux's rootfs on the host; every other part of the app asks here. */
        fun rootfsDir(context: Context): File = File(context.filesDir, "linux/rootfs")

        // A new name for the glibc rootfs: an Alpine one still on the phone counts as not installed,
        // so the next install replaces it (keeping /root, see RootfsInstaller.keepHome).
        const val READY_MARKER = ".hermes2-glibc-ready"
        private const val OUTPUT_TAIL_LINES = 200

        private val GuestEnv = mapOf(
            "HOME" to "/root",
            "USER" to "root",
            "LANG" to "C.UTF-8",
            "TERM" to "xterm-256color",
            "TMPDIR" to "/tmp",
            // Alpine ships a PEP 668 EXTERNALLY-MANAGED marker, so the agent's own `pip install`
            // fails even as root. This rootfs is Hermes' private sandbox, not a system to protect.
            "PIP_BREAK_SYSTEM_PACKAGES" to "1",
            "UV_BREAK_SYSTEM_PACKAGES" to "1",
            // Hermes' own package manager would, on every launch, fetch its glibc Python and uv
            // (400+ MB) and fail on musl. The app installs the dependencies itself.
            "HERMES_DISABLE_LAZY_INSTALLS" to "1",
            // The running gateway checks its checkout with `git status`, which takes about 5 s under
            // proot and holds .git/index.lock at the end; a `hermes update` landing then failed with
            // "Unable to create index.lock: File exists". Read-only git no longer takes that lock.
            "GIT_OPTIONAL_LOCKS" to "0",
            "PATH" to "/root/.local/bin:/root/.hermes/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        )

        // Android denies apps these /proc entries; tools like psutil read them.
        private val FakeProcFiles = mapOf(
            "loadavg" to "0.12 0.07 0.02 2/165 765\n",
            "uptime" to "124.08 932.80\n",
            "version" to "Linux version 6.1.0-hermes2-proot (proot@hermes2) #1 SMP PREEMPT\n",
            "stat" to """
                cpu  1957 0 2877 93280 262 342 254 87 0 0
                cpu0 1957 0 2877 93280 262 342 254 87 0 0
                intr 63361 0
                ctxt 38014093
                btime 1694292441
                processes 26442
                procs_running 1
                procs_blocked 0
                softirq 75663 0 5903 6 25375 10774 0 243 11685 0 21677
            """.trimIndent() + "\n",
            "vmstat" to """
                nr_free_pages 136451
                nr_inactive_anon 0
                nr_active_anon 0
                nr_inactive_file 0
                nr_active_file 0
                pgpgin 0
                pgpgout 0
                pswpin 0
                pswpout 0
            """.trimIndent() + "\n",
        )
    }
}

/**
 * The phone's current UTC offset as a POSIX TZ string, e.g. `<+0330>-03:30` for Tehran.
 * POSIX counts hours west of UTC, hence the flipped sign. Daylight-saving rules are not
 * carried: a process started before a clock change keeps the old offset until restarted.
 */
internal fun posixTimeZone(offsetMillis: Int): String {
    val minutes = offsetMillis / 60_000
    val abs = kotlin.math.abs(minutes)
    val hh = abs / 60
    val mm = abs % 60
    val east = minutes >= 0
    return String.format(
        java.util.Locale.ROOT,
        "<%s%02d%02d>%s%02d:%02d",
        if (east) "+" else "-", hh, mm, if (east) "-" else "+", hh, mm,
    )
}

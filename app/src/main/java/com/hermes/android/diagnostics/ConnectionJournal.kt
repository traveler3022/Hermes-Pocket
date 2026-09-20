package com.hermes.android.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An on-device record of why the gateway connection came and went.
 *
 * The app already narrates its connection life through Timber in useful detail — every
 * dial, drop reason, backoff attempt and gateway process exit code. None of it survives:
 * the debug tree writes to logcat only, logcat is gone on the next reboot, and a phone
 * in someone's hand has no adb attached at the moment the thing goes wrong. So a report
 * of "it disconnects sometimes" arrives with nothing attached, and the answer is always
 * a guess.
 *
 * This keeps those lines in a file instead, capped and rotated, and adds the part Timber
 * cannot see: what the operating system was doing at that instant. A socket that dies
 * because the phone dozed off, because wifi handed over to mobile data, or because
 * Android reclaimed the whole process leaves three very different traces here — and
 * telling them apart is the entire diagnosis. Writes go through one background thread,
 * so logging from the socket's own callbacks never costs the socket anything.
 */
@Singleton
class ConnectionJournal @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dir = File(context.filesDir, "diagnostics")
    private val current = File(dir, "connection.log")
    private val rotated = File(dir, "connection.log.1")
    private val writer = Executors.newSingleThreadExecutor {
        Thread(it, "hermes-journal").apply { isDaemon = true }
    }
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    /** Appends one already-formatted line; never throws, never blocks the caller. */
    fun note(line: String) {
        val prefix = synchronized(stamp) { stamp.format(Date()) }
        writer.execute {
            runCatching {
                if (!dir.exists()) dir.mkdirs()
                rotateIfNeeded()
                current.appendText("$prefix  $line\n")
            }
        }
    }

    /**
     * A snapshot of everything outside the app that can take a socket down, written as
     * one line so a drop and its surroundings stay together in the file.
     *
     * Read deliberately through the system services rather than cached: the point is
     * what was true at the moment of the drop, not what the app last happened to notice.
     */
    fun noteContext(reason: String) {
        val fields = runCatching { systemContext() }.getOrElse { "context=unavailable (${it.message})" }
        note("~~ $reason | $fields")
    }

    /** Marks a fresh process. A gap here with no shutdown line above it means Android killed us. */
    fun noteProcessStart() {
        note("== process start | ${deviceLine()} | ${appVersion()}")
        noteContext("startup")
    }

    /**
     * The tail of the journal, newest last, including the rotated half when the current
     * file is short. Capped at [lines] because this exists to be copied into a bug
     * report, and a report nobody can paste is a report nobody sends.
     */
    fun tail(lines: Int = 400): String {
        val body = runCatching {
            val text = buildString {
                if (rotated.exists()) append(rotated.readText())
                if (current.exists()) append(current.readText())
            }
            text.lineSequence().filter { it.isNotBlank() }.toList().takeLast(lines).joinToString("\n")
        }.getOrElse { "(journal unreadable: ${it.message})" }
        return if (body.isBlank()) "(nothing recorded yet)" else body
    }

    fun clear() {
        writer.execute { runCatching { current.delete(); rotated.delete() } }
    }

    /**
     * Keeps two generations so a drop stays readable after the chatter that follows it.
     * A single truncated file loses exactly the lines that explain the failure, because
     * the reconnect storm right after is what fills the cap.
     */
    private fun rotateIfNeeded() {
        if (current.length() < MAX_BYTES) return
        rotated.delete()
        if (!current.renameTo(rotated)) current.delete()
    }

    private fun systemContext(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            caps == null -> "none"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        val validated = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val memory = android.app.ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                .getMemoryInfo(it)
        }
        return "net=$transport validated=$validated " +
            "screen=${if (pm.isInteractive) "on" else "off"} " +
            "doze=${pm.isDeviceIdleMode} saver=${pm.isPowerSaveMode} " +
            "unrestricted=${pm.isIgnoringBatteryOptimizations(context.packageName)} " +
            "lowmem=${memory.lowMemory} " +
            "uptime=${android.os.SystemClock.elapsedRealtime() / 1000}s"
    }

    private fun appVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "app=${info.versionName}"
    }.getOrDefault("app=unknown")

    private fun deviceLine(): String =
        "device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} " +
            "android=${android.os.Build.VERSION.SDK_INT}"

    private companion object {
        /** Two of these at most on disk; large enough to hold a drop plus the retries after it. */
        private const val MAX_BYTES = 192L * 1024L
    }
}

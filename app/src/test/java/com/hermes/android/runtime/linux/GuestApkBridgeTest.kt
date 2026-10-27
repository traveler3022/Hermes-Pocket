package com.hermes.android.runtime.linux

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The request format of assets/linux/apk-bridge.sh and the command the app builds from it. */
class GuestApkBridgeTest {

    @Test
    fun `arguments come back exactly as the wrapper's printf wrote them`() {
        val bytes = "add\u0000it's\u0000a b\u0000\u0000--no-cache\u0000".toByteArray()
        assertEquals(listOf("add", "it's", "a b", "", "--no-cache"), GuestApkBridge.parseArgs(bytes))
    }

    @Test
    fun `no arguments is an empty list`() {
        assertEquals(emptyList<String>(), GuestApkBridge.parseArgs(ByteArray(0)))
    }

    @Test
    fun `quoting survives a real shell`() {
        val args = listOf("add", "it's", "a b", "", "\$HOME", "`id`", "x;y", "--no-cache")
        val dir = File(System.getProperty("java.io.tmpdir"), "apk-bridge-test").apply { mkdirs() }
        val pidFile = File(dir, "apk pid's").path
        val command = GuestApkBridge.command(dir.path, args, pidFile, program = "printf '%s|'")
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor())
        assertEquals(args.joinToString("") { "$it|" }, output)
        assertEquals(true, File(pidFile).readText().trim().toInt() > 0)
        dir.deleteRecursively()
    }

    @Test
    fun `a missing folder falls back to the root`() {
        val command = GuestApkBridge.command("/no/such/dir", emptyList(), "/dev/null", program = "pwd")
        val process = ProcessBuilder("sh", "-c", command).start()
        assertEquals("/", process.inputStream.bufferedReader().readText().trim())
    }
}

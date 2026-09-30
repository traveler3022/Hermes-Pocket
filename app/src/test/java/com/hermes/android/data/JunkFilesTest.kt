package com.hermes.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class JunkFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val now = System.currentTimeMillis()
    private val old = now - 2 * JunkFiles.RECENT_MS

    private fun file(parent: File, name: String, bytes: Int, modified: Long = old): File =
        File(parent, name).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(bytes))
            setLastModified(modified)
        }

    @Test
    fun contentsKeepTheFolderAndWhatWasUsedInTheLastHour() {
        val dir = tmp.newFolder("tmp")
        file(dir, "old/big.bin", 1000)
        val fresh = file(dir, "fresh.bin", 10, modified = now)
        val source = JunkSource.Contents(dir, keepRecent = true)

        assertEquals(1000L, JunkFiles.measure(source, now))
        assertEquals(1000L, JunkFiles.clear(source, now))
        assertTrue(dir.isDirectory)
        assertTrue(fresh.isFile)
        assertFalse(File(dir, "old").exists())
    }

    @Test
    fun withoutKeepRecentEverythingGoes() {
        val dir = tmp.newFolder("uv")
        file(dir, "wheel.whl", 300, modified = now)
        val source = JunkSource.Contents(dir, keepRecent = false)

        assertEquals(300L, JunkFiles.clear(source, now))
        assertEquals(0, dir.list()!!.size)
    }

    @Test
    fun link2symlinkDataIsNeverTouched() {
        val dir = tmp.newFolder("cache")
        val data = file(dir, ".l2s.numpy0002", 500)
        file(dir, "other.bin", 20)
        val source = JunkSource.Contents(dir, keepRecent = false)

        assertEquals(20L, JunkFiles.measure(source, now))
        JunkFiles.clear(source, now)
        assertTrue(data.isFile)
    }

    @Test
    fun linksInsideAreRemovedWithoutFollowingThem() {
        val outside = file(tmp.newFolder("venv"), "module.py", 50)
        val dir = tmp.newFolder("cache2")
        val link = File(dir, "module.py").toPath()
        Files.createSymbolicLink(link, outside.toPath())
        val source = JunkSource.Contents(dir, keepRecent = false)

        assertEquals(0L, JunkFiles.measure(source, now))
        JunkFiles.clear(source, now)
        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(outside.isFile)
    }

    @Test
    fun aSymlinkedRootIsLeftAlone() {
        val target = tmp.newFolder("real")
        val kept = file(target, "a.bin", 70)
        val root = File(tmp.root, "var-tmp").toPath()
        Files.createSymbolicLink(root, target.toPath())
        val source = JunkSource.Contents(root.toFile(), keepRecent = false)

        assertEquals(0L, JunkFiles.measure(source, now))
        assertEquals(0L, JunkFiles.clear(source, now))
        assertTrue(kept.isFile)
        assertTrue(Files.isSymbolicLink(root))
    }

    @Test
    fun xServerEntriesStay() {
        val dir = tmp.newFolder("guest-tmp")
        val socketDir = file(dir, ".X11-unix/X1", 1).parentFile!!
        val lock = file(dir, ".X1-lock", 11)
        val source = JunkSource.Contents(dir, keepRecent = true)

        assertEquals(0L, JunkFiles.measure(source, now))
        JunkFiles.clear(source, now)
        assertTrue(File(socketDir, "X1").isFile)
        assertTrue(lock.isFile)
    }

    @Test
    fun childrenOnlyTakeWhatMatches() {
        val dir = tmp.newFolder("apk-cache")
        val pkg = file(dir, "python3-3.14.0-r0.apk", 400)
        val index = file(dir, "APKINDEX.1a2b.tar.gz", 90)
        val source = JunkSource.Children(dir, keepRecent = false) { it.name.endsWith(".apk") }

        assertEquals(400L, JunkFiles.clear(source, now))
        assertFalse(pkg.exists())
        assertTrue(index.isFile)
    }

    @Test
    fun liveLogsAreEmptiedRotatedOnesDeletedStateKept() {
        val logs = tmp.newFolder("logs")
        val live = file(logs, "agent.log", 100, modified = now)
        val rotated = file(logs, "agent.log.1", 40)
        val gz = file(logs, "gateway.log.2.gz", 30)
        val receipt = file(logs, "update_receipts/latest.json", 25)
        val result = file(logs, "process-results/proc_1.json", 15)
        val source = JunkSource.Logs(logs, recursive = true)

        assertEquals(170L, JunkFiles.measure(source, now))
        assertEquals(170L, JunkFiles.clear(source, now))
        assertTrue(live.isFile)
        assertEquals(0L, live.length())
        assertFalse(rotated.exists())
        assertFalse(gz.exists())
        assertTrue(receipt.isFile)
        assertTrue(result.isFile)
    }

    @Test
    fun logsWithoutRecursionStayAtTheTop() {
        val home = tmp.newFolder("hermes-home")
        file(home, "gateway-starts.log", 60)
        val nested = file(home, "sessions/notes.log", 30)
        val source = JunkSource.Logs(home, recursive = false)

        assertEquals(60L, JunkFiles.clear(source, now))
        assertEquals(30L, nested.length())
    }

    @Test
    fun chromiumCachesAreFoundOnlyInProfiles() {
        val profile = tmp.newFolder("chrome-profile")
        file(profile, "Local State", 5)
        file(profile, "ShaderCache/data_0", 5)
        file(profile, "Default/Preferences", 5)
        file(profile, "Default/Cache/Cache_Data/f_000001", 5)
        file(profile, "Default/Cookies", 5)
        val unrelated = tmp.newFolder("config")
        file(unrelated, "someapp/Cache/x", 5)

        val found = JunkFiles.chromiumCaches(listOf(profile, unrelated))
            .map { (it as JunkSource.Contents).dir.relativeTo(tmp.root).path }
            .sorted()

        assertEquals(listOf("chrome-profile/Default/Cache", "chrome-profile/ShaderCache"), found)
    }
}

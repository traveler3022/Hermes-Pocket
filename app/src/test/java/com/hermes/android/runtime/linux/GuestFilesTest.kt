package com.hermes.android.runtime.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files

class GuestFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var rootfs: File
    private lateinit var files: GuestFiles

    @Before
    fun setUp() {
        rootfs = tmp.newFolder("rootfs")
        listOf("bin", "root", "dev", "proc", "sys").forEach { File(rootfs, it).mkdir() }
        File(rootfs, "bin/busybox").writeText("bb")
        File(rootfs, ProotEnvironment.READY_MARKER).writeText("1")
        files = GuestFiles(rootfs)
    }

    @Test
    fun `absolute symlink resolves inside the rootfs`() {
        Files.createSymbolicLink(File(rootfs, "bin/sh").toPath(), File("/bin/busybox").toPath())
        assertEquals(File(rootfs, "bin/busybox"), files.hostFile("/bin/sh"))
    }

    @Test
    fun `a link2symlink link holding the host path resolves inside the rootfs`() {
        // proot turns a hard link into symlinks to a hidden .l2s. file, by host path.
        File(rootfs, "root/.l2s.notes0001").writeText("hello")
        Files.createSymbolicLink(
            File(rootfs, "root/notes.txt").toPath(),
            File(rootfs.absolutePath, "root/.l2s.notes0001").toPath(),
        )
        assertEquals("hello", files.hostFile("/root/notes.txt").readText())
        assertEquals(listOf("notes.txt"), files.list("/root"))
    }

    @Test
    fun `a link2symlink link through the canonical host path resolves too`() {
        // Android hands out /data/user/0/… while proot writes the resolved /data/data/… path.
        val alias = File(tmp.root, "alias")
        Files.createSymbolicLink(alias.toPath(), tmp.root.toPath())
        val viaAlias = GuestFiles(File(alias, "rootfs"))
        File(rootfs, "root/.l2s.a0001").writeText("x")
        Files.createSymbolicLink(
            File(rootfs, "root/a").toPath(),
            File(rootfs.canonicalPath, "root/.l2s.a0001").toPath(),
        )
        assertEquals("x", viaAlias.hostFile("/root/a").readText())
    }

    @Test
    fun `relative symlink resolves against its own directory`() {
        Files.createSymbolicLink(File(rootfs, "root/bb").toPath(), File("../bin/busybox").toPath())
        assertEquals(File(rootfs, "bin/busybox"), files.hostFile("/root/bb"))
    }

    @Test
    fun `dot-dot in a link target cannot climb above the root`() {
        Files.createSymbolicLink(File(rootfs, "root/up").toPath(), File("../../../../bin").toPath())
        assertEquals(File(rootfs, "bin"), files.hostFile("/root/up"))
    }

    @Test(expected = FileNotFoundException::class)
    fun `symlink loops are rejected`() {
        Files.createSymbolicLink(File(rootfs, "root/a").toPath(), File("/root/b").toPath())
        Files.createSymbolicLink(File(rootfs, "root/b").toPath(), File("/root/a").toPath())
        files.hostFile("/root/a")
    }

    @Test(expected = FileNotFoundException::class)
    fun `system mounts are not reachable through a link`() {
        Files.createSymbolicLink(File(rootfs, "root/p").toPath(), File("/proc").toPath())
        files.hostFile("/root/p")
    }

    @Test
    fun `root listing hides system mounts and the ready marker`() {
        assertEquals(listOf("bin", "root"), files.list("/"))
    }

    @Test(expected = FileNotFoundException::class)
    fun `document ids with dot-dot are rejected`() {
        GuestFiles.normalize("/root/../etc")
    }

    @Test
    fun `create picks a free name`() {
        assertEquals("/root/a.txt", files.create("/root", "a.txt", directory = false))
        assertEquals("/root/a (1).txt", files.create("/root", "a.txt", directory = false))
    }

    @Test
    fun `rename keeps the entry in its folder`() {
        files.create("/root", "old", directory = true)
        assertEquals("/root/new", files.rename("/root/old", "new"))
        assertTrue(File(rootfs, "root/new").isDirectory)
    }

    @Test
    fun `deleting a symlinked directory leaves its target alone`() {
        Files.createSymbolicLink(File(rootfs, "root/binlink").toPath(), File("/bin").toPath())
        files.delete("/root/binlink")
        assertFalse(Files.exists(File(rootfs, "root/binlink").toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(File(rootfs, "bin/busybox").isFile)
    }

    @Test
    fun `delete removes a whole tree`() {
        File(rootfs, "root/d/e").mkdirs()
        File(rootfs, "root/d/e/f").writeText("x")
        files.delete("/root/d")
        assertFalse(File(rootfs, "root/d").exists())
    }
}

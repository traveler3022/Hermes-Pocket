package com.hermes.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FoundFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var rootfs: File

    private fun file(path: String, bytes: Int): File =
        File(rootfs, path).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(bytes))
        }

    private fun areas() = listOf(
        FileArea.Home to File(rootfs, "root"),
        FileArea.Temp to File(rootfs, "tmp"),
        FileArea.HermesCache to File(rootfs, "root/.hermes/cache/images"),
    )

    private fun setUp() {
        rootfs = tmp.newFolder("rootfs")
        file("root/Downloads/movie.mp4", 900)
        file("root/Downloads/notes.pdf", 300)
        file("root/song.mp3", 200)
        file("root/projects/app/node_modules/lib/index.js", 50)
        file("root/projects/app/src/main.py", 40)
        file("root/.hermes/config.yaml", 10)
        file("root/.hermes/.env", 10)
        file("root/.bashrc", 10)
        file("root/.hermes/cache/images/shot.png", 120)
        file("tmp/pip-build/wheel.whl", 500)
        file("usr/lib/libc.so", 999)
    }

    @Test
    fun onlyTheUsersFilesTempAndMediaCachesAreFound() {
        setUp()
        val scan = FoundFiles.scan(areas(), rootfs)
        val all = scan.filesByFolder.values.flatten().map { File(it.path).relativeTo(rootfs).path }.sorted()

        assertEquals(
            listOf(
                "root/.hermes/cache/images/shot.png",
                "root/Downloads/movie.mp4",
                "root/Downloads/notes.pdf",
                "root/projects/app/node_modules/lib/index.js",
                "root/projects/app/src/main.py",
                "root/song.mp3",
                "tmp/pip-build/wheel.whl",
            ),
            all,
        )
    }

    @Test
    fun filesAreSortedIntoTabsBiggestFirst() {
        setUp()
        val scan = FoundFiles.scan(areas(), rootfs)

        assertEquals(listOf("movie.mp4", "shot.png"), scan.media.map { it.name })
        assertEquals(listOf("song.mp3"), scan.music.map { it.name })
        // A dependency tree counts in its folder but stays out of the list.
        assertEquals(listOf("wheel.whl", "notes.pdf", "main.py"), scan.documents.map { it.name })
    }

    @Test
    fun foldersGroupTwoLevelsDeepAndKnowTheirGuestPath() {
        setUp()
        val scan = FoundFiles.scan(areas(), rootfs)
        val byGuest = scan.folders.associateBy { it.guestPath }

        assertEquals(1200L, byGuest.getValue("/root/Downloads").size)
        assertEquals("projects/app", byGuest.getValue("/root/projects/app").relative)
        assertEquals(90L, byGuest.getValue("/root/projects/app").size)
        assertEquals("", byGuest.getValue("/root").relative)
        assertEquals(FileArea.Temp, byGuest.getValue("/tmp/pip-build").area)
        assertEquals(scan.folders.sortedByDescending { it.size }, scan.folders)
        assertEquals("/root/Downloads", scan.media.first().guestDir)
    }

    @Test
    fun deletingPrunesEmptyFoldersButKeepsTheArea() {
        setUp()
        val scan = FoundFiles.scan(areas(), rootfs)
        val downloads = scan.folders.first { it.guestPath == "/root/Downloads" }
        val paths = scan.filesByFolder.getValue(downloads.path).map { it.path }

        assertEquals(1200L, FoundFiles.delete(paths, areas()) {})
        assertFalse(File(rootfs, "root/Downloads").exists())
        assertTrue(File(rootfs, "root").isDirectory)
    }

    @Test
    fun hiddenHomeEntriesAndPathsOutsideTheAreasAreRefused() {
        setUp()
        val config = File(rootfs, "root/.hermes/config.yaml")
        val library = File(rootfs, "usr/lib/libc.so")
        val sneaky = File(rootfs, "root/Downloads/../.hermes/.env")

        val freed = FoundFiles.delete(listOf(config.path, library.path, sneaky.path), areas()) {}

        assertEquals(0L, freed)
        assertTrue(config.isFile)
        assertTrue(library.isFile)
        assertTrue(File(rootfs, "root/.hermes/.env").isFile)
    }

    @Test
    fun theMediaCacheItselfStaysWhenItsLastFileGoes() {
        setUp()
        val shot = File(rootfs, "root/.hermes/cache/images/shot.png")

        assertEquals(120L, FoundFiles.delete(listOf(shot.path), areas()) {})
        assertTrue(File(rootfs, "root/.hermes/cache/images").isDirectory)
    }
}

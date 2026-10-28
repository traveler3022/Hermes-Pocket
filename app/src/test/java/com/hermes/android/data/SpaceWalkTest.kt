package com.hermes.android.data

import com.hermes.android.domain.storage.SpacePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SpaceWalkTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(rootfs: File, guest: String, bytes: Int): File =
        File(rootfs, guest.removePrefix("/")).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(bytes))
        }

    @Test
    fun placesFollowTheLongestRule() {
        assertNull(SpaceWalk.placeOf("/"))
        assertNull(SpaceWalk.placeOf("/root"))
        assertEquals(SpacePlace.Hermes, SpaceWalk.placeOf("/root/.hermes/installs/abc/environments/x"))
        assertEquals(SpacePlace.HermesData, SpaceWalk.placeOf("/root/.hermes/state.db"))
        assertEquals(SpacePlace.HermesMade, SpaceWalk.placeOf("/root/.hermes/cache/generated/a.png"))
        assertEquals(SpacePlace.Uploads, SpaceWalk.placeOf("/root/.hermes/uploads/x.pdf"))
        assertEquals(SpacePlace.Caches, SpaceWalk.placeOf("/root/.cache/huggingface/hub"))
        assertEquals(SpacePlace.System, SpaceWalk.placeOf("/root/.local/bin/hermes"))
        assertEquals(SpacePlace.Installed, SpaceWalk.placeOf("/root/.local/lib/python3.12"))
        assertEquals(SpacePlace.System, SpaceWalk.placeOf("/root/.config/git"))
        assertEquals(SpacePlace.YourFiles, SpaceWalk.placeOf("/root/Downloads/movie.mp4"))
        assertEquals(SpacePlace.Temp, SpaceWalk.placeOf("/var/tmp/x"))
        assertEquals(SpacePlace.System, SpaceWalk.placeOf("/usr/lib/libc.so"))
        // A name that only starts like a rule is not inside it.
        assertEquals(SpacePlace.HermesData, SpaceWalk.placeOf("/root/.hermes/tools-old"))
    }

    @Test
    fun neededPlacesAndFoldersHoldingThemAreNeverDeletable() {
        assertFalse(SpaceWalk.deletable("/"))
        assertFalse(SpaceWalk.deletable("/root"))
        assertFalse(SpaceWalk.deletable("/root/.hermes"))
        assertFalse(SpaceWalk.deletable("/root/.hermes/state.db"))
        assertFalse(SpaceWalk.deletable("/usr"))
        // ~/.local is the agent's, but Hermes' launchers live in ~/.local/bin.
        assertFalse(SpaceWalk.deletable("/root/.local"))
        assertTrue(SpaceWalk.deletable("/root/.local/lib"))
        assertTrue(SpaceWalk.deletable("/root/.cache"))
        assertTrue(SpaceWalk.deletable("/root/Downloads"))
        assertFalse(SpaceWalk.deletable("/root/Downloads/../.hermes"))
        assertFalse(SpaceWalk.deletable("/root/Downloads/"))
    }

    @Test
    fun scanSumsEachPlaceAndListsItsTopEntries() {
        val rootfs = tmp.newFolder("rootfs")
        file(rootfs, "/root/.cache/huggingface/model.bin", 5000)
        file(rootfs, "/root/.cache/pip/wheel.whl", 300)
        file(rootfs, "/root/Downloads/video.mp4", 2000)
        file(rootfs, "/root/notes.txt", 10)
        file(rootfs, "/root/.hermes/state.db", 700)
        file(rootfs, "/root/.hermes/cache/generated/a.png", 40)
        file(rootfs, "/var/tmp/junk", 90)
        file(rootfs, "/var/lib/apk/db", 60)
        file(rootfs, "/root/.local/bin/hermes", 1)
        file(rootfs, "/root/.local/lib/pkg.py", 400)

        val result = SpaceWalk.scan(rootfs.toPath())
        val o = result.overview

        assertEquals(5300L, o.sizes[SpacePlace.Caches])
        assertEquals(2010L, o.sizes[SpacePlace.YourFiles])
        assertEquals(40L, o.sizes[SpacePlace.HermesMade])
        assertEquals(700L, o.sizes[SpacePlace.HermesData])
        assertEquals(90L, o.sizes[SpacePlace.Temp])
        assertEquals(400L, o.sizes[SpacePlace.Installed])
        assertEquals(61L, o.sizes[SpacePlace.System])
        assertEquals(5300L + 2010 + 40 + 700 + 90 + 400 + 61, o.total)

        assertEquals(listOf("/root/.cache"), o.roots[SpacePlace.Caches]!!.map { it.guestPath })
        assertEquals(listOf("/root/Downloads", "/root/notes.txt"), o.roots[SpacePlace.YourFiles]!!.map { it.guestPath })
        // /var counts without its tmp folder, which is a place of its own.
        val system = o.roots[SpacePlace.System]!!.associate { it.guestPath to it.size }
        assertEquals(60L, system["/var"])
        assertEquals(1L, system["/root/.local/bin"])
        assertEquals(400L, o.roots[SpacePlace.Installed]!!.single { it.guestPath == "/root/.local" }.size)
        assertFalse(o.roots[SpacePlace.Installed]!!.single().deletable)

        val inCache = SpaceWalk.list(rootfs.toPath(), "/root/.cache", result.dirSizes)
        assertEquals(listOf("huggingface", "pip"), inCache.map { it.name })
        assertEquals(5000L, inCache.first().size)
        // Without the scan's sizes a folder is walked when opened.
        assertEquals(5000L, SpaceWalk.list(rootfs.toPath(), "/root/.cache", emptyMap()).first().size)
    }

    @Test
    fun deleteRefusesWhatHermesNeedsAndKeepsRuleFolders() {
        val rootfs = tmp.newFolder("rootfs")
        val db = file(rootfs, "/root/.hermes/state.db", 700)
        val launcher = file(rootfs, "/root/.local/bin/hermes", 1)
        file(rootfs, "/root/.local/lib/pkg.py", 400)
        file(rootfs, "/tmp/a/b.bin", 50)
        val l2s = file(rootfs, "/root/.cache/uv/.l2s.wheel0001", 30)
        file(rootfs, "/root/.cache/uv/x.whl", 20)

        assertEquals(0L, SpaceWalk.delete(rootfs.toPath(), "/root/.hermes/state.db"))
        assertTrue(db.isFile)
        assertEquals(0L, SpaceWalk.delete(rootfs.toPath(), "/root/.local"))
        assertTrue(launcher.isFile)
        assertEquals(400L, SpaceWalk.delete(rootfs.toPath(), "/root/.local/lib"))
        assertFalse(File(rootfs, "root/.local/lib").exists())

        assertEquals(50L, SpaceWalk.delete(rootfs.toPath(), "/tmp"))
        assertTrue(File(rootfs, "tmp").isDirectory)
        assertEquals(0, File(rootfs, "tmp").list()!!.size)

        assertEquals(20L, SpaceWalk.delete(rootfs.toPath(), "/root/.cache/uv"))
        assertTrue(l2s.isFile)
    }

    @Test
    fun aPickedXSocketFolderKeepsTheDisplaySocket() {
        val rootfs = tmp.newFolder("rootfs")
        val display = file(rootfs, "/tmp/.X11-unix/X99", 0)

        assertEquals(0L, SpaceWalk.delete(rootfs.toPath(), "/tmp/.X11-unix"))
        assertTrue(display.exists())
    }
}

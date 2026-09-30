package com.hermes.android.runtime.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileGateTest {
    @get:Rule val tmp = TemporaryFolder()
    private val gate = FileGate()

    private fun file(name: String, bytes: ByteArray): File = tmp.newFile(name).apply { writeBytes(bytes) }

    private fun zip(name: String, vararg entries: String): File {
        val f = tmp.newFile(name)
        ZipOutputStream(f.outputStream()).use { out ->
            entries.forEach { out.putNextEntry(ZipEntry(it)); out.write(1); out.closeEntry() }
        }
        return f
    }

    private val elf = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 2, 1, 1, 0)

    private fun windowsProgram(): ByteArray = ByteArray(0x84).also {
        it[0] = 'M'.code.toByte(); it[1] = 'Z'.code.toByte()
        it[0x3C] = 0x80.toByte() // e_lfanew = 0x80, little-endian
        it[0x80] = 'P'.code.toByte(); it[0x81] = 'E'.code.toByte()
    }

    @Test
    fun `photos, text and office documents pass`() {
        assertEquals(FileGate.Risk.SAFE, gate.riskOf(file("a.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))))
        assertEquals(FileGate.Risk.SAFE, gate.riskOf(file("notes.txt", "MZ is just text here".toByteArray())))
        assertEquals(FileGate.Risk.SAFE, gate.riskOf(zip("report.docx", "[Content_Types].xml", "word/document.xml")))
        assertEquals(FileGate.Risk.SAFE, gate.riskOf(file("empty.bin", ByteArray(0))))
    }

    @Test
    fun `programs under their own names ask first`() {
        assertEquals(FileGate.Risk.PROGRAM, gate.riskOf(zip("app.apk", "AndroidManifest.xml", "classes.dex")))
        assertEquals(FileGate.Risk.PROGRAM, gate.riskOf(zip("bundle.xapk", "base.apk", "manifest.json")))
        assertEquals(FileGate.Risk.PROGRAM, gate.riskOf(zip("stuff.zip", "inside/game.apk")))
        assertEquals(FileGate.Risk.PROGRAM, gate.riskOf(file("tool", elf)))
        assertEquals(FileGate.Risk.PROGRAM, gate.riskOf(file("setup.exe", windowsProgram())))
    }

    @Test
    fun `a program named like a photo or video is disguised`() {
        assertEquals(FileGate.Risk.DISGUISED, gate.riskOf(zip("photo.jpg", "AndroidManifest.xml")))
        assertEquals(FileGate.Risk.DISGUISED, gate.riskOf(file("movie.MP4", elf)))
        assertEquals(FileGate.Risk.DISGUISED, gate.riskOf(file("invoice.pdf", windowsProgram())))
    }

    @Test
    fun `an approval lasts ten minutes and ends when the file changes`() {
        val apk = zip("app.apk", "AndroidManifest.xml")
        assertFalse(gate.isApproved(apk))
        gate.approve(apk, now = 1_000L)
        assertTrue(gate.isApproved(apk, now = 1_000L + 9 * 60 * 1000L))
        assertFalse(gate.isApproved(apk, now = 1_000L + 11 * 60 * 1000L))

        gate.approve(apk, now = 0L)
        apk.appendBytes(byteArrayOf(1, 2, 3))
        assertFalse(gate.isApproved(apk, now = 1L))
    }

    @Test
    fun `data leaves, a program only while confirmed, a disguised one never`() {
        assertNull(gate.heldBack(file("notes.txt", "hello".toByteArray())))

        val apk = zip("app.apk", "AndroidManifest.xml")
        assertEquals(FileGate.Risk.PROGRAM, gate.heldBack(apk, now = 1_000L))
        gate.approve(apk, now = 1_000L)
        assertNull(gate.heldBack(apk, now = 2_000L))
        assertEquals(FileGate.Risk.PROGRAM, gate.heldBack(apk, now = 1_000L + 11 * 60 * 1000L))

        val disguised = zip("photo.jpg", "AndroidManifest.xml")
        gate.approve(disguised, now = 1_000L)
        assertEquals(FileGate.Risk.DISGUISED, gate.heldBack(disguised, now = 2_000L))
    }
}

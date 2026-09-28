package com.hermes.android.runtime.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileGateTest {
    @get:Rule val tmp = TemporaryFolder()

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
        assertEquals(FileGate.Risk.SAFE, FileGate.riskOf(file("a.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))))
        assertEquals(FileGate.Risk.SAFE, FileGate.riskOf(file("notes.txt", "MZ is just text here".toByteArray())))
        assertEquals(FileGate.Risk.SAFE, FileGate.riskOf(zip("report.docx", "[Content_Types].xml", "word/document.xml")))
        assertEquals(FileGate.Risk.SAFE, FileGate.riskOf(file("empty.bin", ByteArray(0))))
    }

    @Test
    fun `programs under their own names ask first`() {
        assertEquals(FileGate.Risk.PROGRAM, FileGate.riskOf(zip("app.apk", "AndroidManifest.xml", "classes.dex")))
        assertEquals(FileGate.Risk.PROGRAM, FileGate.riskOf(zip("bundle.xapk", "base.apk", "manifest.json")))
        assertEquals(FileGate.Risk.PROGRAM, FileGate.riskOf(zip("stuff.zip", "inside/game.apk")))
        assertEquals(FileGate.Risk.PROGRAM, FileGate.riskOf(file("tool", elf)))
        assertEquals(FileGate.Risk.PROGRAM, FileGate.riskOf(file("setup.exe", windowsProgram())))
    }

    @Test
    fun `a program named like a photo or video is disguised`() {
        assertEquals(FileGate.Risk.DISGUISED, FileGate.riskOf(zip("photo.jpg", "AndroidManifest.xml")))
        assertEquals(FileGate.Risk.DISGUISED, FileGate.riskOf(file("movie.MP4", elf)))
        assertEquals(FileGate.Risk.DISGUISED, FileGate.riskOf(file("invoice.pdf", windowsProgram())))
    }

    @Test
    fun `an approval lasts ten minutes and ends when the file changes`() {
        val apk = zip("app.apk", "AndroidManifest.xml")
        assertFalse(FileGate.isApproved(apk))
        FileGate.approve(apk, now = 1_000L)
        assertTrue(FileGate.isApproved(apk, now = 1_000L + 9 * 60 * 1000L))
        assertFalse(FileGate.isApproved(apk, now = 1_000L + 11 * 60 * 1000L))

        FileGate.approve(apk, now = 0L)
        apk.appendBytes(byteArrayOf(1, 2, 3))
        assertFalse(FileGate.isApproved(apk, now = 1L))
    }
}

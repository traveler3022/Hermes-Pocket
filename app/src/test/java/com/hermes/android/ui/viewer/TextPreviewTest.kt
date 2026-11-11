package com.hermes.android.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** The text viewer's decoding, caps and line numbering, and the PDF page size. */
class TextPreviewTest {

    private fun preview(text: String, more: Boolean = false) =
        checkNotNull(decodeTextPreview(text.toByteArray(Charsets.UTF_8), more))

    @Test
    fun `lines split on every newline kind and a final newline adds no empty line`() {
        assertEquals(listOf("a", "b", "c"), preview("a\r\nb\rc\n").lines)
        assertEquals(listOf("a", "", "b"), preview("a\n\nb").lines)
        assertEquals(listOf(""), preview("").lines)
        assertFalse(preview("a\nb\n").truncated)
    }

    @Test
    fun `a byte-order mark picks the encoding and is not shown`() {
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "سلام".toByteArray(Charsets.UTF_8)
        assertEquals(listOf("سلام"), decodeTextPreview(utf8, false)?.lines)
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi\nyo".toByteArray(Charsets.UTF_16LE)
        assertEquals(listOf("hi", "yo"), decodeTextPreview(utf16, false)?.lines)
    }

    @Test
    fun `bad bytes are replaced, never a crash`() {
        val latin1 = "caf".toByteArray() + byteArrayOf(0xE9.toByte()) + "\nok".toByteArray()
        assertEquals(listOf("caf�", "ok"), decodeTextPreview(latin1, false)?.lines)
    }

    @Test
    fun `a NUL byte means binary`() {
        assertNull(decodeTextPreview(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00), false))
    }

    @Test
    fun `a character split by the byte cap is dropped, not shown broken`() {
        val bytes = "ab".toByteArray() + "ش".toByteArray(Charsets.UTF_8).copyOf(1)
        val cut = checkNotNull(decodeTextPreview(bytes, moreFollows = true))
        assertEquals(listOf("ab"), cut.lines)
        assertTrue(cut.truncated)
        // A whole character before the cut stays.
        assertEquals(listOf("abش"), preview("abش", more = true).lines)
    }

    @Test
    fun `line and line-length caps mark the preview truncated`() {
        val many = (1..30).joinToString("\n") { "line $it" }
        val capped = checkNotNull(decodeTextPreview(many.toByteArray(), false, maxLines = 10))
        assertEquals(10, capped.lines.size)
        assertEquals("line 10", capped.lines.last())
        assertTrue(capped.truncated)
        val long = checkNotNull(decodeTextPreview("x".repeat(50).toByteArray(), false, maxLineChars = 20))
        assertEquals("x".repeat(20) + "…", long.lines.single())
        assertTrue(long.truncated)
    }

    @Test
    fun `reading the head stops at the limit and says whether there was more`() {
        val (short, moreShort) = ByteArrayInputStream("hello".toByteArray()).readHead(10)
        assertEquals("hello", String(short))
        assertFalse(moreShort)
        val (exact, moreExact) = ByteArrayInputStream("hello".toByteArray()).readHead(5)
        assertEquals("hello", String(exact))
        assertFalse(moreExact)
        val (head, more) = ByteArrayInputStream("hello world".toByteArray()).readHead(5)
        assertEquals("hello", String(head))
        assertTrue(more)
    }

    @Test
    fun `line numbers get as many digits as the last line needs`() {
        assertEquals(1, lineNumberDigits(0))
        assertEquals(1, lineNumberDigits(9))
        assertEquals(2, lineNumberDigits(10))
        assertEquals(5, lineNumberDigits(20_000))
    }

    @Test
    fun `line ranges cut the joined text back into its lines`() {
        val lines = listOf("ab", "", "cde")
        val text = lines.joinToString("\n")
        assertEquals(lines, lineRanges(lines).map { text.substring(it.first, it.last + 1) })
    }

    @Test
    fun `plain text stays uncoloured and code is coloured by its extension`() {
        assertEquals("", highlightLanguageFor("server.log"))
        assertEquals("", highlightLanguageFor("notes.TXT"))
        assertEquals("", highlightLanguageFor("Makefile"))
        assertEquals("kt", highlightLanguageFor("/root/app/Main.kt"))
        assertEquals("py", highlightLanguageFor("file:///x/run.py?dl=1"))
    }

    @Test
    fun `a PDF page fills the width, keeps its shape, and is never huge`() {
        assertEquals(1080 to 1528, pdfPageBitmapSize(595, 842, 1080))
        assertEquals(2048 to 1447, pdfPageBitmapSize(842, 595, 4000))
        assertEquals(100 to (100 * A4_HEIGHT_RATIO).toInt(), pdfPageBitmapSize(0, 0, 100))
        assertEquals(1, pdfPageBitmapSize(595, 842, 0).first)
    }
}

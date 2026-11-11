package com.hermes.android.ui.viewer

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** The text viewer reads at most this much of a file; the rest is left to another app. */
internal const val TEXT_PREVIEW_MAX_BYTES = 1 shl 20

/** And shows at most this many lines of it. */
internal const val TEXT_PREVIEW_MAX_LINES = 20_000

/** A line longer than this (minified code) is cut, so one row cannot stall the screen. */
internal const val TEXT_PREVIEW_MAX_LINE_CHARS = 4_000

/** What the text viewer shows of a file. */
internal data class TextPreview(
    val lines: List<String>,
    /** Some of the file is not shown: past the byte or line cap, or a line cut short. */
    val truncated: Boolean,
) {
    /** The shown lines as one text, for highlighting and copying. */
    val text: String get() = lines.joinToString("\n")
}

/**
 * Up to [limit] bytes from the start of the stream, plus whether there was more. Reads one
 * byte past the limit to know, never the whole file.
 */
internal fun InputStream.readHead(limit: Int): Pair<ByteArray, Boolean> {
    val buffer = ByteArray(limit + 1)
    var filled = 0
    while (filled < buffer.size) {
        val read = read(buffer, filled, buffer.size - filled)
        if (read < 0) break
        filled += read
    }
    return if (filled > limit) buffer.copyOf(limit) to true else buffer.copyOf(filled) to false
}

/**
 * Decodes the head of a file for the text viewer: a byte-order mark picks UTF-16 or skips
 * UTF-8's, anything else is UTF-8 with bad bytes replaced (never a crash on a Latin-1 log).
 * Null when the bytes look binary (a NUL in the first few KB), which the text viewer must not
 * show. [moreFollows] is true when [head] was cut from a longer file: a character split by
 * the cut is dropped instead of shown as a replacement mark.
 */
internal fun decodeTextPreview(
    head: ByteArray,
    moreFollows: Boolean,
    maxLines: Int = TEXT_PREVIEW_MAX_LINES,
    maxLineChars: Int = TEXT_PREVIEW_MAX_LINE_CHARS,
): TextPreview? {
    val (charset, offset) = when {
        head.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
        head.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
        head.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
        else -> Charsets.UTF_8 to 0
    }
    val utf16 = charset != Charsets.UTF_8
    if (!utf16 && head.asSequence().take(BINARY_SNIFF_BYTES).any { it == 0.toByte() }) return null
    var end = head.size
    if (utf16 && (end - offset) % 2 != 0) end -= 1
    if (!utf16 && moreFollows) end = utf8CompleteEnd(head, offset, end)
    val decoded = decode(head, offset, end - offset, charset)
    val normalized = decoded.replace("\r\n", "\n").replace('\r', '\n')
    var lines = normalized.split('\n')
    // A file ending in a newline has no empty last line to show.
    if (lines.size > 1 && lines.last().isEmpty()) lines = lines.dropLast(1)
    var truncated = moreFollows
    if (lines.size > maxLines) {
        lines = lines.take(maxLines)
        truncated = true
    }
    if (lines.any { it.length > maxLineChars }) {
        lines = lines.map { if (it.length > maxLineChars) it.take(maxLineChars) + "…" else it }
        truncated = true
    }
    return TextPreview(lines, truncated)
}

/** How many digits the line-number gutter needs for [lineCount] lines. */
internal fun lineNumberDigits(lineCount: Int): Int = maxOf(1, lineCount).toString().length

/** Where each line starts and ends in [TextPreview.text], for cutting the highlighted text per row. */
internal fun lineRanges(lines: List<String>): List<IntRange> {
    var start = 0
    return lines.map { line ->
        val range = start until start + line.length
        start += line.length + 1
        range
    }
}

/**
 * The language name [com.hermes.android.ui.component.rememberHighlightedCode] colours [fileName]
 * as: its extension. Empty for prose-like files (logs, plain text, tables), which stay plain.
 */
internal fun highlightLanguageFor(fileName: String): String {
    val base = fileName.substringBefore('?').substringBefore('#').substringAfterLast('/')
    val ext = base.substringAfterLast('.', "").lowercase()
    return if (ext.isEmpty() || ext in plainTextExts) "" else ext
}

private val plainTextExts = setOf("txt", "text", "log", "csv", "tsv", "out", "err", "rst", "adoc", "org", "srt", "vtt")

/**
 * The size, in pixels, to render a PDF page of [pageWidth]×[pageHeight] points at so it fills
 * [targetWidthPx], no wider than [maxWidthPx] (a tablet would otherwise ask for huge bitmaps).
 */
internal fun pdfPageBitmapSize(pageWidth: Int, pageHeight: Int, targetWidthPx: Int, maxWidthPx: Int = 2_048): Pair<Int, Int> {
    val width = targetWidthPx.coerceIn(1, maxWidthPx)
    if (pageWidth <= 0 || pageHeight <= 0) return width to (width * A4_HEIGHT_RATIO).toInt().coerceAtLeast(1)
    val height = (width.toLong() * pageHeight / pageWidth).toInt().coerceIn(1, width * 8)
    return width to height
}

/** Height over width of an A4 page: the space a page holds before its own size is known. */
internal const val A4_HEIGHT_RATIO = 297f / 210f

private const val BINARY_SNIFF_BYTES = 8_000

/** [end], or where a UTF-8 character that the cut at [end] split in two begins. */
private fun utf8CompleteEnd(bytes: ByteArray, from: Int, end: Int): Int {
    var i = end - 1
    var continuation = 0
    while (i >= from && continuation < 3 && (bytes[i].toInt() and 0xC0) == 0x80) {
        i--
        continuation++
    }
    if (i < from) return end
    val lead = bytes[i].toInt() and 0xFF
    val length = when {
        lead < 0x80 -> 1
        lead shr 5 == 0x6 -> 2
        lead shr 4 == 0xE -> 3
        lead shr 3 == 0x1E -> 4
        else -> 1
    }
    return if (continuation + 1 < length) i else end
}

private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
    size >= bytes.size && bytes.indices.all { this[it] == bytes[it].toByte() }

private fun decode(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String =
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .decode(ByteBuffer.wrap(bytes, offset, maxOf(0, length)))
        .toString()

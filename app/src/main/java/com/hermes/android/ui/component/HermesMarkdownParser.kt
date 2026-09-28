package com.hermes.android.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Block model + parser behind [HermesMarkdown].
 *
 * Split out of the composable file so the rendering layer stays readable and
 * the parser can be unit-tested without Compose (`parseMdBlocks` is internal
 * and pure).
 */
internal sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Code(val language: String, val code: String) : MdBlock()
    data class Quote(val depth: Int, val text: String) : MdBlock()
    data class ListItem(
        val depth: Int,
        val ordinal: Int?,
        val checked: Boolean?,
        val text: String,
    ) : MdBlock()
    data class Table(val rows: List<List<String>>, val hasHeader: Boolean) : MdBlock()
    data class Para(val text: String) : MdBlock()
    data object Rule : MdBlock()
}

internal data class InlineStyle(
    val linkColor: Color,
    val codeBg: Color,
    val onCodeBg: Color,
)

/**
 * Streaming-aware wrapper around [parseMdBlocks].
 *
 * While a turn streams, the message text grows by a few characters at a time
 * and re-parsing all of it on every token is quadratic in message length.
 *
 * Markdown blocks are only closed at a blank line, so everything before the
 * last blank line can never change once seen. This parser keeps those blocks
 * and re-parses just the tail after it. Growth is detected by prefix check; any
 * other change (edit, retry, new message) falls back to a full parse.
 */
internal class IncrementalMdParser {
    private var lastSource: String = ""
    private var stableOffset: Int = 0
    private var stableBlocks: List<MdBlock> = emptyList()

    fun parse(source: String): List<MdBlock> {
        if (source == lastSource) return stableBlocks + parseMdBlocks(source.substring(stableOffset))

        val grew = source.length >= lastSource.length &&
            lastSource.isNotEmpty() &&
            source.startsWith(lastSource)
        if (!grew) {
            lastSource = ""
            stableOffset = 0
            stableBlocks = emptyList()
        }

        // Advance the stable point to the last blank line, but never inside an
        // open fenced code block.
        val boundary = lastStableBoundary(source, stableOffset)
        if (boundary > stableOffset) {
            stableBlocks = stableBlocks + parseMdBlocks(source.substring(stableOffset, boundary))
            stableOffset = boundary
        }

        lastSource = source
        return stableBlocks + parseMdBlocks(source.substring(stableOffset))
    }

    private fun lastStableBoundary(source: String, from: Int): Int {
        var fences = 0
        var lastBlank = from
        var i = from
        while (i < source.length) {
            val end = source.indexOf('\n', i).let { if (it < 0) source.length else it }
            val trimmed = source.substring(i, end).trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) fences++
            if (trimmed.isEmpty() && fences % 2 == 0) lastBlank = (end + 1).coerceAtMost(source.length)
            i = end + 1
        }
        // Keep the tail (everything after the last blank line) live.
        return if (fences % 2 == 0) lastBlank else from
    }
}

private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
private val bulletRe = Regex("^([-*+])\\s+(.*)$")
private val numberRe = Regex("^(\\d{1,9})[.)]\\s+(.*)$")
private val taskRe = Regex("^\\[([ xX])\\]\\s*(.*)$")
private val ruleRe = Regex("^(?:\\s*[-*_]){3,}\\s*$")
private val tableSepRe = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")

/** Nesting deeper than this is drawn at this depth, so an over-indented line cannot push text off screen. */
private const val MaxListDepth = 6

/** Split raw markdown into an ordered list of typed [MdBlock]s. Pure + testable. */
internal fun parseMdBlocks(md: String): List<MdBlock> {
    if (md.isBlank()) return emptyList()
    val lines = md.replace("\r\n", "\n").split("\n")
    val out = ArrayList<MdBlock>()
    val para = StringBuilder()

    fun flush() {
        if (para.isNotBlank()) out.add(MdBlock.Para(para.toString().trim()))
        para.setLength(0)
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
        val heading = headingRe.matchEntire(trimmed)
        val bullet = bulletRe.matchEntire(trimmed)
        val number = numberRe.matchEntire(trimmed)

        when {
            trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                flush()
                val fence = trimmed.take(3)
                val language = trimmed.removePrefix(fence).trim().takeWhile { !it.isWhitespace() }
                i++
                val code = StringBuilder()
                while (i < lines.size && !lines[i].trimStart().startsWith(fence)) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                // An unterminated fence is normal mid-stream: render what we have.
                out.add(MdBlock.Code(language, code.toString().trimEnd('\n')))
                i++
            }

            ruleRe.matches(line) -> {
                flush(); out.add(MdBlock.Rule); i++
            }

            heading != null -> {
                flush()
                out.add(MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()))
                i++
            }

            trimmed.startsWith(">") -> {
                flush()
                var depth = 0
                var rest = trimmed
                while (rest.startsWith(">")) {
                    depth++
                    rest = rest.removePrefix(">").trimStart()
                }
                out.add(MdBlock.Quote(depth, rest))
                i++
            }

            // Table: a header row followed by a |---|---| separator.
            trimmed.startsWith("|") && i + 1 < lines.size &&
                tableSepRe.matches(lines[i + 1].trim()) -> {
                flush()
                val rows = ArrayList<List<String>>()
                rows.add(splitRow(trimmed))
                i += 2
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows.add(splitRow(lines[i].trimStart()))
                    i++
                }
                out.add(MdBlock.Table(rows, hasHeader = true))
            }

            bullet != null -> {
                flush()
                val body = bullet.groupValues[2]
                val task = taskRe.matchEntire(body)
                out.add(
                    MdBlock.ListItem(
                        depth = (indent / 2).coerceAtMost(MaxListDepth),
                        ordinal = null,
                        checked = task?.let { it.groupValues[1].lowercase() == "x" },
                        text = task?.groupValues?.get(2) ?: body,
                    ),
                )
                i++
            }

            number != null -> {
                flush()
                out.add(
                    MdBlock.ListItem(
                        depth = (indent / 2).coerceAtMost(MaxListDepth),
                        ordinal = number.groupValues[1].toIntOrNull() ?: 1,
                        checked = null,
                        text = number.groupValues[2],
                    ),
                )
                i++
            }

            trimmed.isEmpty() -> { flush(); i++ }

            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(trimmed)
                i++
            }
        }
    }
    flush()
    return out
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

// ── Inline ───────────────────────────────────────────────────────────────

private val autoLinkRe = Regex("""https?://[^\s<>"')\]]+""")

/**
 * Inline markdown → [AnnotatedString]: escapes, code spans, bold, italic,
 * strikethrough, explicit links and bare URLs.
 *
 * Links are [LinkAnnotation.Url]s inside the string rather than annotations
 * resolved by a tap handler, so opening them is the platform's job and the text
 * stays an ordinary `Text` that can be selected across a link.
 *
 * Emphasis requires a delimiter that opens on a non-space character and is not
 * glued to a word character on the outside, so identifiers such as
 * `some_snake_case_name` and globs such as `*.kt` survive intact.
 */
internal fun inline(text: String, s: InlineStyle): AnnotatedString = buildAnnotatedString {
    var i = 0

    fun emphasisEnd(delim: String, start: Int): Int {
        var j = start
        while (j < text.length) {
            val at = text.indexOf(delim, j)
            if (at < 0) return -1
            // Closing delimiter must not follow a space and must not be escaped.
            val prev = text.getOrNull(at - 1)
            if (prev != null && !prev.isWhitespace() && prev != '\\') return at
            j = at + delim.length
        }
        return -1
    }

    fun link(url: String, label: String) {
        withLink(
            LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = s.linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
            ),
        ) { append(label) }
    }

    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' && i + 1 < text.length && !text[i + 1].isLetterOrDigit() -> {
                append(text[i + 1]); i += 2
            }

            c == '`' -> {
                val fence = if (text.startsWith("``", i)) "``" else "`"
                val end = text.indexOf(fence, i + fence.length)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = s.codeBg,
                            color = s.onCodeBg,
                        ),
                    ) { append(text.substring(i + fence.length, end).trim()) }
                    i = end + fence.length
                } else { append(c); i++ }
            }

            text.startsWith("***", i) || text.startsWith("___", i) -> {
                val delim = text.substring(i, i + 3)
                val end = emphasisEnd(delim, i + 3)
                if (end > i && text.getOrNull(i + 3)?.isWhitespace() == false) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 3, end))
                    }
                    i = end + 3
                } else { append(c); i++ }
            }

            text.startsWith("**", i) || text.startsWith("__", i) -> {
                val delim = text.substring(i, i + 2)
                val end = emphasisEnd(delim, i + 2)
                if (end > i && text.getOrNull(i + 2)?.isWhitespace() == false) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(c); i++ }
            }

            text.startsWith("~~", i) -> {
                val end = emphasisEnd("~~", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(c); i++ }
            }

            (c == '*' || c == '_') && canOpenEmphasis(text, i) -> {
                val end = emphasisEnd(c.toString(), i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(c); i++ }
            }

            c == '[' -> {
                val close = text.indexOf(']', i + 1)
                val open = if (close > 0) close + 1 else -1
                if (close > i && text.getOrNull(open) == '(') {
                    val pClose = text.indexOf(')', open + 1)
                    if (pClose > open) {
                        link(url = text.substring(open + 1, pClose).trim(), label = text.substring(i + 1, close))
                        i = pClose + 1
                    } else { append(c); i++ }
                } else { append(c); i++ }
            }

            c == 'h' && (i == 0 || !text[i - 1].isLetterOrDigit()) &&
                (text.startsWith("http://", i) || text.startsWith("https://", i)) -> {
                // The scheme is right here, so a match starts at i unless nothing follows it
                // ("https:// "), in which case find() would jump ahead — that is not ours.
                val match = autoLinkRe.find(text, i)?.takeIf { it.range.first == i }
                val url = match?.value.orEmpty().trimEnd('.', ',', '؛', '،')
                if (url.isEmpty()) { append(c); i++ } else {
                    link(url = url, label = url)
                    i += url.length
                }
            }

            else -> { append(c); i++ }
        }
    }
}

/**
 * Which way a block of prose reads: `true` for right-to-left, `false` for
 * left-to-right, `null` when it has no letters to decide by (numbers, symbols).
 *
 * The side with more words wins (words, not letters: a Persian sentence listing
 * package names like fontconfig or wayland has more Latin letters but is still
 * Persian), so one that happens to open with a Latin word (`git` …) still reads
 * right-to-left, and an English one with a Persian word in it stays left-to-right. Inline code and URLs are ignored:
 * they are Latin whatever language the sentence around them is in. A tie goes to
 * whichever letter came first.
 */
internal fun isRtlText(text: String): Boolean? {
    var rtl = 0
    var ltr = 0
    var first: Boolean? = null
    var inCode = false
    var inWord = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            ch == '`' -> { inCode = !inCode; inWord = false }
            inCode -> Unit
            (ch == 'h') && (text.startsWith("http://", i) || text.startsWith("https://", i)) -> {
                while (i < text.length && !text[i].isWhitespace()) i++
                inWord = false
                continue
            }
            // Zero-width non-joiner and combining marks sit inside Persian words (بسته‌ها).
            ch == '\u200C' || Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
            else -> {
                val rtlLetter = when (Character.getDirectionality(ch)) {
                    Character.DIRECTIONALITY_LEFT_TO_RIGHT -> false
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                    -> true
                    else -> null
                }
                if (rtlLetter != null && !inWord) {
                    if (rtlLetter) rtl++ else ltr++
                    if (first == null) first = rtlLetter
                }
                inWord = rtlLetter != null
            }
        }
        i++
    }
    return when {
        rtl > ltr -> true
        ltr > rtl -> false
        else -> first
    }
}

/** `*` / `_` may open emphasis only at a word boundary with non-space content. */
private fun canOpenEmphasis(text: String, i: Int): Boolean {
    val next = text.getOrNull(i + 1) ?: return false
    if (next.isWhitespace()) return false
    val prev = text.getOrNull(i - 1)
    // `snake_case` — an underscore glued to a word char never opens emphasis.
    if (text[i] == '_' && prev != null && (prev.isLetterOrDigit() || prev == '_')) return false
    return true
}

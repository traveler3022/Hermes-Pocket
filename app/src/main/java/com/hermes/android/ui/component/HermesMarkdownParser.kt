package com.hermes.android.ui.component

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

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

    /** A GitHub alert: `> [!NOTE]` and the quote lines under it, parsed as blocks of their own. */
    data class Alert(val kind: AlertKind, val children: List<MdBlock>) : MdBlock()
    /**
     * [text] is the item's first paragraph; [children] are the blocks indented under it
     * after a blank line (a second paragraph, a quote, a table). Nested items are not
     * children: they stay items of their own with a larger [depth].
     */
    data class ListItem(
        val depth: Int,
        val ordinal: Int?,
        val checked: Boolean?,
        val text: String,
        val children: List<MdBlock> = emptyList(),
    ) : MdBlock()

    /** `Term` on one line and `: definition` on each line under it. */
    data class Definition(val term: String, val definitions: List<String>) : MdBlock()

    /** `[^label]: text`. Parsed where it stands; [HermesMarkdown] moves them all to the end. */
    data class Footnote(val label: String, val text: String) : MdBlock()
    /**
     * [aligns] holds one entry per column of the `|:--|:-:|--:|` separator row; a row
     * with more cells than that leaves the extra ones at [TableAlign.None].
     */
    data class Table(
        val rows: List<List<String>>,
        val hasHeader: Boolean,
        val aligns: List<TableAlign> = emptyList(),
    ) : MdBlock()
    data class Para(val text: String) : MdBlock()

    /** Display math: `$$…$$` or `\[…\]`, drawn as a formula on its own line. */
    data class Math(val latex: String) : MdBlock()
    data object Rule : MdBlock()
}

/** GitHub's five alert kinds, as in Hermes Desktop. */
internal enum class AlertKind { Note, Tip, Important, Warning, Caution }

/** A table column's alignment from its separator cell: `---`, `:---`, `:---:`, `---:`. */
internal enum class TableAlign { None, Left, Center, Right }

internal data class InlineStyle(
    val linkColor: Color,
    val codeBg: Color,
    val onCodeBg: Color,
    /** Background of `==highlighted==` text. */
    val markBg: Color = codeBg,
    /** Whether `$…$`, `$$…$$` and `\(…\)` are formulas; off, they stay as typed. */
    val math: Boolean = true,
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
        var pendingBlank = -1
        var i = from
        while (i < source.length) {
            val end = source.indexOf('\n', i).let { if (it < 0) source.length else it }
            val line = source.substring(i, end)
            val trimmed = line.trimStart()
            if (fences % 2 == 0) {
                if (trimmed.isEmpty()) {
                    pendingBlank = (end + 1).coerceAtMost(source.length)
                } else {
                    // A blank line is a boundary only once the line after it is known to
                    // start at the margin: an indented one may still belong to the list
                    // item above, and a `: ` one makes the paragraph above a definition term.
                    if (pendingBlank >= 0 && line.length == trimmed.length && !trimmed.startsWith(":")) {
                        lastBlank = pendingBlank
                    }
                    pendingBlank = -1
                }
            }
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) fences++
            i = end + 1
        }
        // Keep the tail (everything after the last boundary) live.
        return if (fences % 2 == 0) lastBlank else from
    }
}

private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
private val bulletRe = Regex("^([-*+])\\s+(.*)$")
private val numberRe = Regex("^(\\d{1,9})[.)]\\s+(.*)$")
private val taskRe = Regex("^\\[([ xX])\\]\\s*(.*)$")
private val ruleRe = Regex("^(?:\\s*[-*_]){3,}\\s*$")
private val tableSepRe = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")
private val footnoteDefRe = Regex("^\\[\\^([^\\]\\s]+)]:\\s*(.*)$")
private val definitionRe = Regex("^:\\s+(.*)$")
private val alertMarkerRe = Regex("^\\[!(note|tip|important|warning|caution)]\\s*(.*)$", RegexOption.IGNORE_CASE)

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
        val footnote = footnoteDefRe.matchEntire(trimmed)
        val definition = definitionRe.matchEntire(trimmed)?.groupValues?.get(1)
        val displayMath = displayMathOpen(trimmed)
        val alert = if (trimmed.startsWith(">")) alertMarkerRe.matchEntire(trimmed.removePrefix(">").trim()) else null

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

            displayMath != null -> {
                flush()
                i = readDisplayMath(lines, i, displayMath.first, displayMath.second, out)
            }

            heading != null -> {
                flush()
                out.add(MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()))
                i++
            }

            // `> [!NOTE]`: the quote lines under it are the alert's body. Text after the
            // marker on its own line is accepted as the body's first line, as Desktop does.
            alert != null -> {
                flush()
                val body = ArrayList<String>()
                alert.groupValues[2].takeIf { it.isNotBlank() }?.let(body::add)
                i++
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    body.add(lines[i].trimStart().removePrefix(">").removePrefix(" "))
                    i++
                }
                val kind = AlertKind.valueOf(alert.groupValues[1].lowercase().replaceFirstChar { it.uppercase() })
                out.add(MdBlock.Alert(kind, parseMdBlocks(body.joinToString("\n"))))
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
                val aligns = splitRow(lines[i + 1]).map(::tableAlign)
                i += 2
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows.add(splitRow(lines[i].trimStart()))
                    i++
                }
                out.add(MdBlock.Table(rows, hasHeader = true, aligns = aligns))
            }

            bullet != null -> {
                flush()
                val body = bullet.groupValues[2]
                val task = taskRe.matchEntire(body)
                val item = listItemBody(lines, i, task?.groupValues?.get(2) ?: body, indent)
                out.add(
                    MdBlock.ListItem(
                        depth = (indent / 2).coerceAtMost(MaxListDepth),
                        ordinal = null,
                        checked = task?.let { it.groupValues[1].lowercase() == "x" },
                        text = item.text,
                        children = item.children,
                    ),
                )
                i = item.next
            }

            number != null -> {
                flush()
                val item = listItemBody(lines, i, number.groupValues[2], indent)
                out.add(
                    MdBlock.ListItem(
                        depth = (indent / 2).coerceAtMost(MaxListDepth),
                        ordinal = number.groupValues[1].toIntOrNull() ?: 1,
                        checked = null,
                        text = item.text,
                        children = item.children,
                    ),
                )
                i = item.next
            }

            footnote != null -> {
                flush()
                val text = StringBuilder(footnote.groupValues[2])
                i++
                // Indented lines right under it continue the note.
                while (i < lines.size && lines[i].startsWith("  ") && lines[i].isNotBlank()) {
                    text.append('\n').append(lines[i].trim())
                    i++
                }
                out.add(MdBlock.Footnote(footnote.groupValues[1], text.toString()))
            }

            // `: definition` under a term: the term is the paragraph line right above,
            // or, after a blank line, the last line of the paragraph before it.
            definition != null && (para.isNotBlank() || out.lastOrNull().isTermHolder()) -> {
                val last = out.lastOrNull()
                when {
                    para.isNotBlank() -> {
                        val held = para.toString().trim()
                        para.setLength(0)
                        val above = held.substringBeforeLast('\n', "")
                        if (above.isNotBlank()) out.add(MdBlock.Para(above.trim()))
                        out.add(MdBlock.Definition(held.substringAfterLast('\n').trim(), listOf(definition)))
                    }
                    last is MdBlock.Definition ->
                        out[out.lastIndex] = last.copy(definitions = last.definitions + definition)
                    last is MdBlock.Para -> {
                        out.removeAt(out.lastIndex)
                        val above = last.text.substringBeforeLast('\n', "")
                        if (above.isNotBlank()) out.add(MdBlock.Para(above.trim()))
                        out.add(MdBlock.Definition(last.text.substringAfterLast('\n').trim(), listOf(definition)))
                    }
                }
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

private fun MdBlock?.isTermHolder(): Boolean = this is MdBlock.Para || this is MdBlock.Definition

private fun isListLine(trimmed: String): Boolean = bulletRe.matches(trimmed) || numberRe.matches(trimmed)

/** A line that starts a block of its own, so it cannot run on as part of a paragraph above it. */
private fun opensBlock(trimmed: String): Boolean =
    trimmed.startsWith("```") || trimmed.startsWith("~~~") || trimmed.startsWith(">") ||
        displayMathOpen(trimmed) != null ||
        trimmed.startsWith("|") || headingRe.matches(trimmed) || ruleRe.matches(trimmed) ||
        isListLine(trimmed) || footnoteDefRe.matches(trimmed) || definitionRe.matches(trimmed)

/** The open/close marks of display math starting [trimmed], or null when it starts none. */
private fun displayMathOpen(trimmed: String): Pair<String, String>? = when {
    trimmed.startsWith("$$") -> "$$" to "$$"
    trimmed.startsWith("\\[") -> "\\[" to "\\]"
    else -> null
}

/**
 * Reads display math opened on line [start] into [out] and returns the line after it.
 * The formula may sit on one line (`$$x^2$$`) or span several up to the line holding
 * [close]. Text after the closing mark on that line is kept as a paragraph. Unclosed
 * math (mid-stream) takes the rest of the message, like an unclosed code fence.
 */
private fun readDisplayMath(lines: List<String>, start: Int, open: String, close: String, out: MutableList<MdBlock>): Int {
    val first = lines[start].trimStart().removePrefix(open)
    val body = StringBuilder()
    var rest = ""
    var i = start
    var line = first
    while (true) {
        val end = line.indexOf(close)
        if (end >= 0) {
            body.append(line, 0, end)
            rest = line.substring(end + close.length).trim()
            i++
            break
        }
        body.append(line).append('\n')
        i++
        if (i >= lines.size) break
        line = lines[i]
    }
    val latex = body.toString().trim()
    if (latex.isNotEmpty()) out.add(MdBlock.Math(latex))
    if (rest.isNotEmpty()) out.add(MdBlock.Para(rest))
    return i
}

private class ListItemBody(val text: String, val children: List<MdBlock>, val next: Int)

/**
 * The lines that belong to the list item opened on line [start]. Lines right under it
 * continue its first paragraph, indented or not ("lazy", as CommonMark has it). After
 * a blank line, lines indented past the marker become blocks of their own inside the
 * item: a second paragraph, a quote, a table. Any list line ends the item, so nested
 * items keep being flat items with a depth, and a line back at the item's margin
 * after a blank line ends it too.
 */
private fun listItemBody(lines: List<String>, start: Int, first: String, indent: Int): ListItemBody {
    val text = StringBuilder(first)
    val rest = ArrayList<String>()
    val inner = indent + 2
    var blanks = 0
    var next = start + 1
    var j = start + 1
    while (j < lines.size) {
        val line = lines[j]
        val trimmed = line.trimStart()
        if (trimmed.isEmpty()) {
            blanks++
            j++
            continue
        }
        if (isListLine(trimmed)) break
        val indented = line.length - trimmed.length >= inner
        when {
            blanks == 0 && rest.isEmpty() && !opensBlock(trimmed) -> text.append('\n').append(trimmed)
            indented -> {
                if (rest.isNotEmpty()) repeat(blanks) { rest.add("") }
                rest.add(line.substring(inner))
            }
            blanks == 0 && !opensBlock(trimmed) -> rest.add(trimmed)
            else -> break
        }
        blanks = 0
        j++
        next = j
    }
    return ListItemBody(text.toString(), parseMdBlocks(rest.joinToString("\n")), next)
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

/** One trimmed separator cell → its column alignment. */
private fun tableAlign(cell: String): TableAlign {
    val left = cell.startsWith(':')
    val right = cell.endsWith(':')
    return when {
        left && right -> TableAlign.Center
        right -> TableAlign.Right
        left -> TableAlign.Left
        else -> TableAlign.None
    }
}

// ── Inline ───────────────────────────────────────────────────────────────

private val autoLinkRe = Regex("""https?://[^\s<>"')\]]+""")

/**
 * Inline markdown → [AnnotatedString]: escapes, code spans, bold, italic,
 * strikethrough, `==highlight==`, `^superscript^`, `~subscript~`, `<kbd>keys</kbd>`,
 * footnote references, explicit links and bare URLs.
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

    // `^sup^` / `~sub~` (Pandoc's rule): one word, no spaces inside, so "~5 min" and
    // "x^2 + y^2" without a closing mark stay as typed; `~/path` is never a subscript.
    fun scriptEnd(start: Int): Int {
        val mark = text[start]
        val close = text.indexOf(mark, start + 1)
        if (close <= start + 1) return -1
        val content = text.substring(start + 1, close)
        if (content.any { it.isWhitespace() } || content.none { it.isLetterOrDigit() }) return -1
        if (mark == '~' && content.startsWith("/")) return -1
        return close
    }

    fun footnoteRefEnd(start: Int): Int {
        val close = text.indexOf(']', start + 2)
        if (close <= start + 2) return -1
        if (text.substring(start + 2, close).any { it.isWhitespace() }) return -1
        return if (text.getOrNull(close + 1) == '(') -1 else close
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

    fun math(latex: String) {
        appendInlineContent(INLINE_MATH_PREFIX + latex, latex)
    }

    while (i < text.length) {
        val c = text[i]
        when {
            // `\(x\)` and `$$x$$` inside a sentence: checked before escapes eat the `\(`.
            s.math && text.startsWith("\\(", i) && text.indexOf("\\)", i + 2) > i + 2 -> {
                val end = text.indexOf("\\)", i + 2)
                math(text.substring(i + 2, end).trim())
                i = end + 2
            }

            s.math && text.startsWith("$$", i) && text.indexOf("$$", i + 2) > i + 2 -> {
                val end = text.indexOf("$$", i + 2)
                math(text.substring(i + 2, end).trim())
                i = end + 2
            }

            s.math && c == '$' && inlineMathEnd(text, i) > i -> {
                val end = inlineMathEnd(text, i)
                math(text.substring(i + 1, end))
                i = end + 1
            }

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

            text.startsWith("==", i) -> {
                val end = emphasisEnd("==", i + 2)
                val content = if (end > i + 2) text.substring(i + 2, end) else ""
                // "====" under a title is a rule of equals signs, not an empty highlight.
                if (content.isNotEmpty() && !content[0].isWhitespace() && content.any { it != '=' }) {
                    withStyle(SpanStyle(background = s.markBg)) { append(content) }
                    i = end + 2
                } else { append(c); i++ }
            }

            c == '^' || c == '~' -> {
                val end = scriptEnd(i)
                if (end > i) {
                    val shift = if (c == '^') BaselineShift.Superscript else BaselineShift.Subscript
                    withStyle(SpanStyle(baselineShift = shift, fontSize = 0.75.em)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(c); i++ }
            }

            c == '<' && text.startsWith("<kbd>", i, ignoreCase = true) -> {
                val end = text.indexOf("</kbd>", i + 5, ignoreCase = true)
                if (end > i + 5) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = s.codeBg,
                            color = s.onCodeBg,
                        ),
                    ) {
                        // Thin spaces pad the key so its background reads as a keycap.
                        append('\u2009')
                        append(text.substring(i + 5, end).trim())
                        append('\u2009')
                    }
                    i = end + 6
                } else { append(c); i++ }
            }

            // `[^1]`: a reference to a footnote, drawn as its raised label. `[^x](url)`
            // is a link whose label starts with a caret, left to the branch below.
            text.startsWith("[^", i) && footnoteRefEnd(i) > i -> {
                val close = footnoteRefEnd(i)
                withStyle(
                    SpanStyle(
                        baselineShift = BaselineShift.Superscript,
                        fontSize = 0.75.em,
                        color = s.linkColor,
                    ),
                ) { append(text.substring(i + 2, close)) }
                i = close + 1
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

/** Id prefix of the inline content holding a `$…$` formula; the rest of the id is its LaTeX. */
internal const val INLINE_MATH_PREFIX = "math:"

/**
 * Where the `$…$` formula opened at [start] closes, or -1. Pandoc's rule, so prices
 * stay text: no space just inside either `$`, and no digit right after the closing
 * one ("$5 and $10" is not a formula). The formula stays on one line.
 */
internal fun inlineMathEnd(text: String, start: Int): Int {
    if (start > 0 && text[start - 1] == '\\') return -1
    val first = text.getOrNull(start + 1) ?: return -1
    if (first.isWhitespace() || first == '$') return -1
    var j = start + 1
    while (j < text.length) {
        when (text[j]) {
            '\n' -> return -1
            '\\' -> { j += 2; continue }
            '$' -> {
                val before = text[j - 1]
                val after = text.getOrNull(j + 1)
                return if (!before.isWhitespace() && (after == null || !after.isDigit())) j else -1
            }
        }
        j++
    }
    return -1
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

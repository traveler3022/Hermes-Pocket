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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.autolink.AutolinkType
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Block model + parser behind [HermesMarkdown].
 *
 * Parsing is CommonMark + GFM (tables, strikethrough, task items, bare links)
 * plus `$`/`$$` math ([MathExtension]) by commonmark-java, the same parser Telegram's markdown messages go through
 * (`MarkdownParser.java`) and the same spec the desktop app's remark follows,
 * so a reply reads here the way it reads there. This file only flattens the
 * syntax tree into the few block kinds the renderer draws.
 *
 * Split out of the composable file so the rendering layer stays readable and
 * the parser can be unit-tested without Compose (`parseMdBlocks` is internal
 * and pure). Blocks hold [MdSpan]s, not colours: the theme is applied at draw
 * time by [inline].
 */
internal sealed class MdBlock {
    data class Heading(val level: Int, val text: List<MdSpan>) : MdBlock()
    data class Code(val language: String, val code: String) : MdBlock()
    data class Quote(val depth: Int, val text: List<MdSpan>) : MdBlock()
    data class ListItem(
        val depth: Int,
        val ordinal: Int?,
        val checked: Boolean?,
        val text: List<MdSpan>,
    ) : MdBlock()
    data class Table(val rows: List<List<List<MdSpan>>>, val hasHeader: Boolean) : MdBlock()
    data class Para(val text: List<MdSpan>) : MdBlock()

    /** A `$$` formula block; [closed] is false while a streaming reply is still writing it. */
    data class Math(val latex: String, val closed: Boolean) : MdBlock()
    data object Rule : MdBlock()
}

/** One inline run: text, a code span, emphasis around runs, or a link over runs. */
internal sealed class MdSpan {
    data class Text(val text: String) : MdSpan()
    data class Code(val code: String) : MdSpan()
    data class Styled(val style: MdStyle, val children: List<MdSpan>) : MdSpan()
    data class Link(val url: String, val children: List<MdSpan>) : MdSpan()
    data class Math(val latex: String) : MdSpan()
}

internal enum class MdStyle { Bold, Italic, Strike }

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
 * A blank line followed by a line that starts in column 0 and is not a list
 * marker closes every open block in CommonMark (paragraph, list, quote, table),
 * so nothing before such a point can change once it has been seen. This parser
 * keeps those blocks and re-parses just the tail after it. Growth is detected by
 * prefix check; any other change (edit, retry, new message) falls back to a full
 * parse.
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

        val boundary = lastStableBoundary(source, stableOffset)
        if (boundary > stableOffset) {
            stableBlocks = stableBlocks + parseMdBlocks(source.substring(stableOffset, boundary))
            stableOffset = boundary
        }

        lastSource = source
        return stableBlocks + parseMdBlocks(source.substring(stableOffset))
    }

    /**
     * The start of the last line that opens a fresh top-level block: a complete
     * line that follows a blank line, starts in column 0, is not a list item
     * (which could continue the list before it) and is not inside a fenced code
     * block or a `$$` formula.
     */
    private fun lastStableBoundary(source: String, from: Int): Int {
        var boundary = from
        var fence: String? = null
        var previousBlank = false
        var i = from
        while (i < source.length) {
            val end = source.indexOf('\n', i).let { if (it < 0) source.length else it }
            val line = source.substring(i, end).trimEnd('\r')
            val trimmed = line.trimStart()
            // Only a finished line can be judged: "1" may still become "1. item".
            if (fence == null && previousBlank && i > from && end < source.length && trimmed.isNotEmpty() &&
                line.length == trimmed.length && !listMarkerRe.containsMatchIn(line)
            ) {
                boundary = i
            }
            val run = fenceRe.find(trimmed)?.value
            if (fence == null) {
                if (run != null) {
                    fence = run
                } else if (trimmed.startsWith("$$") && !trimmed.substring(2).trimEnd().endsWith("$$")) {
                    fence = "$$"
                }
            } else if (fence == "$$") {
                if (trimmed.endsWith("$$")) fence = null
            } else if (run != null && run[0] == fence[0] && run.length >= fence.length &&
                trimmed.substring(run.length).isBlank()
            ) {
                fence = null
            }
            previousBlank = trimmed.isEmpty()
            i = end + 1
        }
        return boundary
    }
}

private val listMarkerRe = Regex("^(?:[-*+]|\\d{1,9}[.)])(?:[ \t]|$)")
private val fenceRe = Regex("^(?:`{3,}|~{3,})")

/** Nesting deeper than this is drawn at this depth, so a deep list cannot push text off screen. */
private const val MaxListDepth = 6

/** Characters a bare link may run into in Persian prose that are not part of the address. */
private val trailingPersianPunctuation = charArrayOf('،', '؛')

private val blockParser: Parser by lazy {
    Parser.builder()
        .extensions(
            listOf(
                TablesExtension.create(),
                // One tilde would strike through `~/a and ~/b`.
                StrikethroughExtension.builder().requireTwoTildes(true).build(),
                AutolinkExtension.builder().linkTypes(AutolinkType.URL).build(),
                TaskListItemsExtension.create(),
                MathExtension,
            ),
        )
        .build()
}

/** Paragraphs only: what a single run of inline text means, whatever it opens with. */
private val inlineParser: Parser by lazy {
    Parser.builder()
        .enabledBlockTypes(emptySet())
        .extensions(
            listOf(
                StrikethroughExtension.builder().requireTwoTildes(true).build(),
                AutolinkExtension.builder().linkTypes(AutolinkType.URL).build(),
                MathExtension,
            ),
        )
        .build()
}

/** Split raw markdown into an ordered list of typed [MdBlock]s. Pure + testable. */
internal fun parseMdBlocks(md: String): List<MdBlock> {
    if (md.isBlank()) return emptyList()
    val out = ArrayList<MdBlock>()
    emitChildren(blockParser.parse(md), out, listDepth = 0, quoteDepth = 0)
    return out
}

/** Inline markdown on its own, e.g. a table cell or a line of a caption. */
internal fun parseInline(text: String): List<MdSpan> {
    val out = ArrayList<MdSpan>()
    var block = inlineParser.parse(text).firstChild
    while (block != null) {
        if (out.isNotEmpty()) out.add(MdSpan.Text("\n"))
        out.addAll(inlines(block))
        block = block.next
    }
    return mergeText(out)
}

private fun emitChildren(parent: Node, out: MutableList<MdBlock>, listDepth: Int, quoteDepth: Int) {
    var node = parent.firstChild
    while (node != null) {
        emitBlock(node, out, listDepth, quoteDepth)
        node = node.next
    }
}

private fun emitBlock(node: Node, out: MutableList<MdBlock>, listDepth: Int, quoteDepth: Int) {
    when (node) {
        is Paragraph -> {
            val text = inlines(node)
            if (text.isNotEmpty()) {
                out.add(if (quoteDepth > 0) MdBlock.Quote(quoteDepth, text) else MdBlock.Para(text))
            }
        }
        is Heading -> out.add(MdBlock.Heading(node.level, inlines(node)))
        is FencedCodeBlock -> out.add(
            MdBlock.Code(
                language = node.info.orEmpty().trim().takeWhile { !it.isWhitespace() },
                code = node.literal.orEmpty().trimEnd('\n'),
            ),
        )
        is IndentedCodeBlock -> out.add(MdBlock.Code("", node.literal.orEmpty().trimEnd('\n')))
        is ThematicBreak -> out.add(MdBlock.Rule)
        is BlockQuote -> emitChildren(node, out, listDepth, quoteDepth + 1)
        is ListBlock -> emitList(node, out, listDepth, quoteDepth)
        is TableBlock -> out.add(table(node))
        is MathBlock -> if (node.latex.isNotEmpty()) out.add(MdBlock.Math(node.latex, node.closed))
        // Raw HTML is shown as written, as before.
        is HtmlBlock -> node.literal.orEmpty().trimEnd('\n').takeIf { it.isNotBlank() }?.let {
            out.add(MdBlock.Para(listOf(MdSpan.Text(it))))
        }
        is LinkReferenceDefinition -> Unit
        else -> emitChildren(node, out, listDepth, quoteDepth)
    }
}

/**
 * One [MdBlock.ListItem] per item, numbered from the list's own start. What an
 * item holds after its first paragraph (a nested list, a second paragraph, code)
 * follows it one level deeper.
 */
private fun emitList(list: ListBlock, out: MutableList<MdBlock>, depth: Int, quoteDepth: Int) {
    var number = (list as? OrderedList)?.markerStartNumber ?: 1
    var item = list.firstChild
    while (item != null) {
        if (item is ListItem) {
            var child: Node? = item.firstChild
            val checked = (child as? TaskListItemMarker)?.isChecked
            if (checked != null) child = child?.next
            var text = emptyList<MdSpan>()
            if (child is Paragraph) {
                text = inlines(child)
                child = child.next
            }
            out.add(
                MdBlock.ListItem(
                    depth = depth.coerceAtMost(MaxListDepth),
                    ordinal = if (list is OrderedList) number else null,
                    checked = checked,
                    text = text,
                ),
            )
            while (child != null) {
                emitBlock(child, out, depth + 1, quoteDepth)
                child = child.next
            }
            number++
        }
        item = item.next
    }
}

private fun table(block: TableBlock): MdBlock.Table {
    val rows = ArrayList<List<List<MdSpan>>>()
    var hasHeader = false
    var section = block.firstChild
    while (section != null) {
        if (section is TableHead) hasHeader = true
        var row = section.firstChild
        while (row != null) {
            if (row is TableRow) {
                val cells = ArrayList<List<MdSpan>>()
                var cell = row.firstChild
                while (cell != null) {
                    if (cell is TableCell) cells.add(inlines(cell))
                    cell = cell.next
                }
                rows.add(cells)
            }
            row = row.next
        }
        section = section.next
    }
    return MdBlock.Table(rows, hasHeader)
}

private fun inlines(parent: Node): List<MdSpan> {
    val out = ArrayList<MdSpan>()
    var node = parent.firstChild
    while (node != null) {
        addInline(node, out)
        node = node.next
    }
    return mergeText(out)
}

private fun addInline(node: Node, out: MutableList<MdSpan>) {
    when (node) {
        is Text -> out.add(MdSpan.Text(node.literal))
        is Code -> out.add(MdSpan.Code(node.literal))
        is SoftLineBreak, is HardLineBreak -> out.add(MdSpan.Text("\n"))
        is StrongEmphasis -> out.add(MdSpan.Styled(MdStyle.Bold, inlines(node)))
        is Emphasis -> out.add(MdSpan.Styled(MdStyle.Italic, inlines(node)))
        is Strikethrough -> out.add(MdSpan.Styled(MdStyle.Strike, inlines(node)))
        is Link -> addLink(node, out)
        is HtmlInline -> out.add(MdSpan.Text(node.literal))
        is MathInline -> out.add(MdSpan.Math(node.latex))
        // Images are lifted out upstream (parseContentBlocks); one that is left
        // reads as its alt text, as in Telegram.
        else -> out.addAll(inlines(node))
    }
}

private fun addLink(link: Link, out: MutableList<MdSpan>) {
    val children = inlines(link)
    val url = link.destination.orEmpty()
    // A bare link in Persian prose runs into the Persian comma; leave it outside.
    val bare = children == listOf(MdSpan.Text(url))
    val trimmed = if (bare) url.trimEnd(*trailingPersianPunctuation) else url
    if (bare && trimmed.length < url.length) {
        out.add(MdSpan.Link(trimmed, listOf(MdSpan.Text(trimmed))))
        out.add(MdSpan.Text(url.substring(trimmed.length)))
    } else {
        out.add(MdSpan.Link(url, children))
    }
}

/** Adjacent text runs as one, so equal text always gives equal spans. */
private fun mergeText(spans: List<MdSpan>): List<MdSpan> {
    val out = ArrayList<MdSpan>(spans.size)
    for (span in spans) {
        val last = out.lastOrNull()
        if (span is MdSpan.Text && last is MdSpan.Text) {
            out[out.lastIndex] = MdSpan.Text(last.text + span.text)
        } else {
            out.add(span)
        }
    }
    return out
}

// ── Inline ───────────────────────────────────────────────────────────────

/**
 * [MdSpan]s → [AnnotatedString], with the theme's colours.
 *
 * Links are [LinkAnnotation.Url]s inside the string rather than annotations
 * resolved by a tap handler, so opening them is the platform's job and the text
 * stays an ordinary `Text` that can be selected across a link.
 */
internal fun inline(
    spans: List<MdSpan>,
    s: InlineStyle,
    mathDrawn: (latex: String) -> Boolean = { true },
): AnnotatedString = buildAnnotatedString {
    appendSpans(spans, s, mathDrawn)
}

private fun AnnotatedString.Builder.appendSpans(
    spans: List<MdSpan>,
    s: InlineStyle,
    mathDrawn: (String) -> Boolean,
) {
    for (span in spans) {
        when (span) {
            is MdSpan.Text -> append(span.text)
            is MdSpan.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = s.codeBg, color = s.onCodeBg),
            ) { append(span.code) }
            is MdSpan.Styled -> withStyle(
                when (span.style) {
                    MdStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                    MdStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                    MdStyle.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                },
            ) { appendSpans(span.children, s, mathDrawn) }
            is MdSpan.Link -> withLink(
                LinkAnnotation.Url(
                    url = span.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = s.linkColor, textDecoration = TextDecoration.Underline),
                    ),
                ),
            ) { appendSpans(span.children, s, mathDrawn) }
            // Drawn by the renderer in the placeholder; copying the text gives the source.
            // A formula JLatexMath cannot draw reads as its source, like code.
            is MdSpan.Math -> if (mathDrawn(span.latex)) {
                appendInlineContent(mathContentId(span.latex), span.latex)
            } else {
                withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = s.codeBg, color = s.onCodeBg),
                ) { append(span.latex) }
            }
        }
    }
}

internal fun mathContentId(latex: String) = "math:$latex"

/** Every inline formula in [this], once each. */
internal fun List<MdSpan>.mathSources(): List<String> {
    val out = LinkedHashSet<String>()
    fun walk(spans: List<MdSpan>) {
        for (span in spans) {
            when (span) {
                is MdSpan.Math -> out.add(span.latex)
                is MdSpan.Styled -> walk(span.children)
                is MdSpan.Link -> walk(span.children)
                else -> Unit
            }
        }
    }
    walk(this)
    return out.toList()
}

/**
 * The text [isRtlText] decides a block's direction by: what it reads, with code
 * spans back in backticks so they do not count.
 */
internal fun List<MdSpan>.directionText(): String = buildString { appendDirection(this@directionText) }

private fun StringBuilder.appendDirection(spans: List<MdSpan>) {
    for (span in spans) {
        when (span) {
            is MdSpan.Text -> append(span.text)
            is MdSpan.Code -> append('`').append(span.code.replace("`", "")).append('`')
            is MdSpan.Styled -> appendDirection(span.children)
            is MdSpan.Link -> appendDirection(span.children)
            is MdSpan.Math -> append('`').append(span.latex.replace("`", "")).append('`')
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

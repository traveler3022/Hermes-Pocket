package com.hermes.android.ui.component

import com.hermes.android.ui.design.HxRadius
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import com.hrm.latex.renderer.LatexAutoWrap
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.hermes.android.R
import com.hermes.android.domain.markdown.TableColumnWidths
import com.hermes.android.domain.markdown.markdownTableKey
import com.hermes.android.ui.icons.filled.Lightbulb

/**
 * Native-Compose markdown renderer for Hermes assistant/tool output.
 *
 * ## Why this is native
 * The original implementation wrapped Markwon in a `TextView` via `AndroidView`.
 * Inside a `LazyColumn` that View interop is expensive, and during streaming it
 * re-parsed + re-laid-out the whole TextView on every token. This renderer
 * parses markdown into a light block list and emits pure Compose `Text` /
 * `AnnotatedString` — no View interop, cheap to recompose while streaming.
 *
 * ## What it handles
 * 1. **RTL.** Every block resolves its own direction from the words it holds
 *    ([isRtlText]) and renders with that direction + `TextAlign.Start`
 *    across the full width, so a Persian paragraph is right-aligned and an
 *    English one left-aligned whatever the surrounding layout direction is
 *    (the in-app language does not change it). Code is always LTR.
 * 2. **Selection + links.** Links are `LinkAnnotation`s inside an ordinary
 *    `Text`, so the text stays selectable inside a `SelectionContainer`.
 * 3. **Streaming cost.** [IncrementalMdParser] keeps the blocks before the last
 *    blank line and re-parses only the growing tail, so callers can render real
 *    markdown while streaming.
 * 4. **Inline correctness.** `snake_case_name` and `*.kt` are not emphasis;
 *    `\*` escapes work; `__bold__`, `***both***`, `~~strike~~` and bare URLs
 *    are supported.
 * 5. **Block coverage.** Nested lists (by indent), list items with more than
 *    one paragraph, task lists, tables, definition lists, footnotes (drawn at
 *    the end) and thematic breaks render instead of leaking their raw syntax.
 * 6. **Type scale.** Heading sizes derive from the caller's `style.fontSize`.
 *
 * Code/images/mermaid/html are already split out upstream by
 * `parseContentBlocks`, so this only renders the text segments. The signature
 * only grew a trailing, defaulted [persianDigits] — every call site keeps
 * working unchanged.
 */
@Composable
fun HermesMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
    ),
    linkColor: Color = MaterialTheme.colorScheme.primary,
    persianDigits: Boolean = stringResource(R.string.ui_language) == "fa",
    math: Boolean = true,
    /** The chat search's query; blank marks nothing. */
    highlight: String = "",
) {
    val parser = remember { IncrementalMdParser() }
    val blocks = remember(markdown) { footnotesLast(parser.parse(markdown)) }

    // A caller may hand over a style with no colour, size or line height; give
    // every block a concrete one so the arithmetic below cannot hit "unspecified".
    val base = if (style.fontSize.isSpecified) style.fontSize else DefaultFontSize
    val textColor = if (style.color.isSpecified) style.color else MaterialTheme.colorScheme.onSurface
    val body = style.copy(
        color = textColor,
        fontSize = base,
        lineHeight = if (style.lineHeight.isSpecified) style.lineHeight else base * 1.75f,
    )

    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val theme = MdTheme(
        body = body,
        base = base,
        codeBg = codeBg,
        onCode = MaterialTheme.colorScheme.onSurfaceVariant,
        muted = textColor.copy(alpha = 0.72f),
        rule = MaterialTheme.colorScheme.outlineVariant,
        linkColor = linkColor,
        inlineStyle = InlineStyle(
            linkColor = linkColor,
            codeBg = codeBg,
            onCodeBg = MaterialTheme.colorScheme.onSurfaceVariant,
            markBg = MaterialTheme.colorScheme.tertiaryContainer,
            math = math,
            highlight = highlight,
            hitColor = MaterialTheme.colorScheme.tertiary,
            hitBg = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        persianDigits = persianDigits,
    )

    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            if (block is MdBlock.Footnote && blocks.getOrNull(index - 1) !is MdBlock.Footnote) {
                // A short rule sets the notes off from the reply, as in print.
                Spacer(
                    Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .width(72.dp)
                        .height(1.dp)
                        .background(theme.rule),
                )
            }
            MdBlockView(block, theme)
        }
    }
}

/** Everything a block needs to draw itself, resolved once per [HermesMarkdown]. */
@Immutable
private class MdTheme(
    val body: TextStyle,
    val base: TextUnit,
    val codeBg: Color,
    val onCode: Color,
    val muted: Color,
    val rule: Color,
    val linkColor: Color,
    val inlineStyle: InlineStyle,
    val persianDigits: Boolean,
)

/** Footnotes are parsed where they were written and drawn after everything else. */
private fun footnotesLast(blocks: List<MdBlock>): List<MdBlock> {
    if (blocks.none { it is MdBlock.Footnote }) return blocks
    val (notes, rest) = blocks.partition { it is MdBlock.Footnote }
    return rest + notes
}

@Composable
private fun MdBlockView(block: MdBlock, theme: MdTheme) {
    val body = theme.body
    val base = theme.base
    val muted = theme.muted
    val linkColor = theme.linkColor
    val inlineStyle = theme.inlineStyle
    when (block) {
        is MdBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.45f
                2 -> 1.28f
                3 -> 1.14f
                else -> 1.04f
            }
            MdText(
                raw = block.text,
                style = body.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = base * scale,
                    lineHeight = base * scale * 1.45f,
                ),
                inlineStyle = inlineStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (block.level <= 2) 10.dp else 6.dp,
                        bottom = 2.dp,
                    ),
            )
        }

        is MdBlock.Code -> HermesCodeBlock(
            language = block.language,
            code = block.code,
            modifier = Modifier.padding(vertical = 6.dp),
            searchQuery = inlineStyle.highlight,
        )

        is MdBlock.Math -> if (inlineStyle.math) {
            MathBlock(block.latex, base * 1.15f, body.color)
        } else {
            MdText(raw = "$$${block.latex}$$", style = body, inlineStyle = inlineStyle)
        }

        is MdBlock.Rule -> Spacer(
            Modifier
                .padding(vertical = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(theme.rule),
        )

        is MdBlock.Quote -> BlockRow(block.text) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .height(IntrinsicSize.Min),
            ) {
                repeat(block.depth.coerceAtMost(MaxQuoteBars)) {
                    Spacer(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(linkColor.copy(alpha = 0.45f)),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                MdText(
                    raw = block.text,
                    // Persian has no italic (Vazirmatn ships none, so it was a synthetic slant
                    // that bent the letters); a right-to-left quote is told apart by its bar and colour.
                    style = body.copy(
                        color = muted,
                        fontStyle = if (isRtlText(block.text) == true) FontStyle.Normal else FontStyle.Italic,
                    ),
                    inlineStyle = inlineStyle,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        is MdBlock.ListItem -> BlockRow(block.text) {
            val checked = block.checked
            val ordinal = block.ordinal
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (4 + block.depth * 14).dp,
                        top = 2.dp,
                        bottom = 2.dp,
                    ),
                verticalAlignment = Alignment.Top,
            ) {
                val marker = when {
                    checked != null -> if (checked) "☑" else "☐"
                    ordinal != null -> "${ordinal.localizeDigits(theme.persianDigits)}."
                    else -> bulletFor(block.depth)
                }
                Text(
                    text = marker,
                    style = body.copy(color = if (checked == true) linkColor else muted),
                )
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    MdText(
                        raw = block.text,
                        style = if (checked == true) {
                            body.copy(color = muted, textDecoration = TextDecoration.LineThrough)
                        } else {
                            body
                        },
                        inlineStyle = inlineStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // The blocks indented under the item sit in its text column.
                    block.children.forEach { MdBlockView(it, theme) }
                }
            }
        }

        is MdBlock.Definition -> BlockRow(block.term) {
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                MdText(
                    raw = block.term,
                    style = body.copy(fontWeight = FontWeight.Bold),
                    inlineStyle = inlineStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
                block.definitions.forEach { definition ->
                    BlockRow(definition) {
                        MdText(
                            raw = definition,
                            style = body,
                            inlineStyle = inlineStyle,
                            modifier = Modifier.fillMaxWidth().padding(start = 18.dp),
                        )
                    }
                }
            }
        }

        is MdBlock.Footnote -> BlockRow(block.text) {
            val small = body.copy(fontSize = base * 0.86f, lineHeight = base * 1.45f)
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(text = block.label, style = small.copy(color = linkColor))
                Spacer(Modifier.width(6.dp))
                MdText(
                    raw = block.text,
                    style = small.copy(color = muted),
                    inlineStyle = inlineStyle,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        is MdBlock.Table -> MdTable(block, body, inlineStyle, theme.rule, muted)

        is MdBlock.Alert -> MdAlert(block, theme)

        is MdBlock.Para -> BlockRow(block.text) {
            MdText(
                raw = block.text,
                style = body,
                inlineStyle = inlineStyle,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
        }
    }
}

/**
 * A GitHub alert (`> [!NOTE]`), drawn as Hermes Desktop draws it: a bordered box
 * whose first line is the kind's icon and name in its accent colour, then the body.
 */
@Composable
private fun MdAlert(block: MdBlock.Alert, theme: MdTheme) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Desktop's Tailwind accents: the 600 shade on light, the 400 shade on dark.
    val (icon, label, accent) = when (block.kind) {
        AlertKind.Note -> Triple(Icons.Filled.Info, R.string.md_alert_note, if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB))
        AlertKind.Tip -> Triple(Icons.Filled.Lightbulb, R.string.md_alert_tip, if (dark) Color(0xFF34D399) else Color(0xFF059669))
        AlertKind.Important -> Triple(Icons.Filled.Info, R.string.md_alert_important, if (dark) Color(0xFFA78BFA) else Color(0xFF7C3AED))
        AlertKind.Warning -> Triple(Icons.Filled.Warning, R.string.md_alert_warning, if (dark) Color(0xFFFBBF24) else Color(0xFFD97706))
        AlertKind.Caution -> Triple(Icons.Filled.Warning, R.string.md_alert_caution, if (dark) Color(0xFFFB7185) else Color(0xFFE11D48))
    }
    val firstText = block.children.firstOrNull()?.let(::blockText).orEmpty()
    BlockRow(firstText) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(HxRadius.sm))
                .border(1.dp, theme.rule, RoundedCornerShape(HxRadius.sm))
                .background(theme.codeBg.copy(alpha = 0.25f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(label),
                    style = theme.body.copy(color = accent, fontWeight = FontWeight.SemiBold, fontSize = theme.base * 0.93f),
                )
            }
            Spacer(Modifier.height(4.dp))
            block.children.forEach { MdBlockView(it, theme) }
        }
    }
}

/** The words a block reads by, for picking its direction. */
private fun blockText(block: MdBlock): String = when (block) {
    is MdBlock.Heading -> block.text
    is MdBlock.Quote -> block.text
    is MdBlock.ListItem -> block.text
    is MdBlock.Definition -> block.term
    is MdBlock.Footnote -> block.text
    is MdBlock.Para -> block.text
    is MdBlock.Alert -> block.children.firstOrNull()?.let(::blockText).orEmpty()
    is MdBlock.Table -> block.rows.firstOrNull()?.joinToString(" ").orEmpty()
    is MdBlock.Code, is MdBlock.Math, MdBlock.Rule -> ""
}

/**
 * Give one block its own layout direction, so a Persian paragraph is not laid
 * out LTR just because the surrounding message happened to start with Latin
 * text (and vice-versa). Applies to the row/bullet geometry; the text itself
 * additionally uses `TextDirection.Content`.
 */
@Composable
private fun BlockRow(text: String, content: @Composable () -> Unit) {
    val fallback = LocalLayoutDirection.current
    val direction = when (isRtlText(text)) {
        true -> LayoutDirection.Rtl
        false -> LayoutDirection.Ltr
        null -> fallback
    }
    CompositionLocalProvider(LocalLayoutDirection provides direction) { content() }
}

/**
 * Where [MdTable] keeps the column widths a reader dragged to. Null (the default,
 * as in previews and tests) still lets a table be resized, just not remembered.
 */
val LocalTableColumnWidths = staticCompositionLocalOf<TableColumnWidths?> { null }

/**
 * A markdown table whose columns can be resized, as in Hermes Desktop: drag the
 * seam after a header cell, double-tap it to go back to the default widths.
 *
 * A drag moves exactly one seam: the two columns either side trade width and
 * keep their sum, so the table never changes size under the finger. The widths
 * are remembered by the table's header row (see [markdownTableKey]).
 */
@Composable
private fun MdTable(
    block: MdBlock.Table,
    style: TextStyle,
    inlineStyle: InlineStyle,
    rule: Color,
    muted: Color,
) {
    val store = LocalTableColumnWidths.current
    val header = if (block.hasHeader) block.rows.firstOrNull().orEmpty() else emptyList()
    val columns = block.rows.maxOfOrNull { it.size } ?: 0
    val key = remember(header) { if (header.size >= 2) markdownTableKey(header) else null }
    var widths by remember(key, columns) {
        mutableStateOf(key?.let { store?.read(it, columns) } ?: List(columns) { TableCellWidth.value })
    }
    val density = LocalDensity.current
    // In a right-to-left table the columns run the other way, so a drag does too.
    val rtlTable = LocalLayoutDirection.current == LayoutDirection.Rtl

    // The scroll container hands its child unbounded width, where a divider
    // that fills the width would collapse to nothing; the column is pinned to
    // its widest row so the dividers span the table.
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(HxRadius.sm))
            .background(inlineStyle.codeBg.copy(alpha = 0.45f))
            .horizontalScroll(rememberScrollState()),
    ) {
        // Columns follow the layout direction, so a right-to-left screen mirrors the
        // whole table: markdown's left and right are taken as the table's start and
        // end and mirror with it. Center is the same both ways, and a column with no
        // colon keeps each cell at the side its own words start on.
        Column(Modifier.width(IntrinsicSize.Max)) {
            block.rows.forEachIndexed { index, row ->
                val isHeader = index == 0 && block.hasHeader
                Row(
                    Modifier
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .height(IntrinsicSize.Min),
                ) {
                    row.forEachIndexed { column, cell ->
                        if (column > 0) {
                            if (isHeader && column < columns) {
                                ColumnSeam(
                                    color = rule,
                                    onDrag = { dx ->
                                        val delta = with(density) { dx.toDp().value } * if (rtlTable) -1 else 1
                                        widths = resizeSeam(widths, column - 1, delta, MinColumnWidth.value)
                                    },
                                    onDragEnd = { if (key != null) store?.write(key, widths) },
                                    onReset = {
                                        if (key != null) store?.clear(key)
                                        widths = List(columns) { TableCellWidth.value }
                                    },
                                )
                            } else {
                                Spacer(Modifier.width(SeamWidth))
                            }
                        }
                        MdText(
                            raw = cell,
                            style = if (isHeader) {
                                style.copy(fontWeight = FontWeight.Bold)
                            } else {
                                style.copy(color = muted)
                            },
                            inlineStyle = inlineStyle,
                            modifier = Modifier.width((widths.getOrNull(column) ?: TableCellWidth.value).dp),
                            align = when (block.aligns.getOrElse(column) { TableAlign.None }) {
                                TableAlign.None -> TextAlign.Start
                                TableAlign.Center -> TextAlign.Center
                                TableAlign.Left -> if (rtlTable) TextAlign.Right else TextAlign.Left
                                TableAlign.Right -> if (rtlTable) TextAlign.Left else TextAlign.Right
                            },
                        )
                    }
                }
                if (index < block.rows.lastIndex) {
                    Spacer(Modifier.fillMaxWidth().height(1.dp).background(rule))
                }
            }
        }
    }
}

/**
 * The gap between two header cells, and the handle for resizing them: a drag
 * hands its distance (px, screen direction) to [onDrag], a double tap resets.
 */
@Composable
private fun ColumnSeam(
    color: Color,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onReset: () -> Unit,
) {
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val reset by rememberUpdatedState(onReset)
    Box(
        Modifier
            .width(SeamWidth)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                ) { change, dx ->
                    change.consume()
                    drag(dx)
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { reset() }) },
        contentAlignment = Alignment.Center,
    ) {
        Spacer(Modifier.width(1.dp).fillMaxHeight().background(color))
    }
}

/**
 * Renders one inline run. The inline parse is remembered per (text, style) so
 * scrolling and streaming recompositions don't re-tokenise text that has not
 * changed.
 *
 * Fills the width it is given: `TextAlign.Start` puts the text at the side its
 * direction starts on, but only inside a box wider than the text. The direction
 * is [isRtlText]'s, the same one [BlockRow] lays the bullet out by: Compose's
 * `TextDirection.Content` goes by the first letter instead, so a Persian item
 * opening with a path read left-to-right beside a right-hand bullet.
 */
@Composable
private fun MdText(
    raw: String,
    style: TextStyle,
    inlineStyle: InlineStyle,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    val text = remember(raw, inlineStyle) {
        inline(raw, inlineStyle).withSearchHits(
            inlineStyle.highlight,
            SpanStyle(color = inlineStyle.hitColor, fontWeight = FontWeight.Bold, background = inlineStyle.hitBg),
        )
    }
    val direction = remember(raw) {
        when (isRtlText(raw)) {
            true -> TextDirection.Rtl
            false -> TextDirection.Ltr
            null -> TextDirection.Content
        }
    }
    Text(
        text = text,
        style = style.copy(
            textDirection = direction,
            textAlign = align,
        ),
        inlineContent = inlineMath(text, style),
        modifier = modifier,
    )
}

/**
 * The `$…$` formulas of [text], measured and drawn by the LaTeX renderer at the
 * size and colour of the words around them. A formula the renderer cannot read
 * is shown as its LaTeX source.
 */
@Composable
private fun inlineMath(text: AnnotatedString, style: TextStyle): Map<String, InlineTextContent> {
    val ids = remember(text) {
        text.getStringAnnotations(INLINE_CONTENT_TAG, 0, text.length)
            .map { it.item }
            .filter { it.startsWith(INLINE_MATH_PREFIX) }
            .distinct()
    }
    if (ids.isEmpty()) return emptyMap()
    val measurer = rememberLatexMeasurer()
    val config = remember(style.fontSize, style.color) {
        LatexConfig(fontSize = style.fontSize, theme = LatexTheme.light(color = style.color))
    }
    return remember(ids, measurer, config) {
        ids.associateWith { id ->
            val latex = id.removePrefix(INLINE_MATH_PREFIX)
            measurer.inlineContent(latex, config) ?: sourceFallback(latex, style)
        }
    }
}

/** Shows a formula's LaTeX as plain text, roughly as wide as its characters. */
private fun sourceFallback(latex: String, style: TextStyle): InlineTextContent =
    InlineTextContent(
        Placeholder(
            width = (latex.length * 0.6f).em,
            height = 1.3.em,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        ),
    ) {
        Text(latex, style = style.copy(fontFamily = FontFamily.Monospace), maxLines = 1)
    }

/** A display formula, centred on its own line; a long one wraps at its operators. */
@Composable
private fun MathBlock(latex: String, size: TextUnit, color: Color) {
    val config = remember(size, color) {
        LatexConfig(fontSize = size, theme = LatexTheme.light(color = color))
    }
    // Formulas read left to right in a Persian reply too.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            LatexAutoWrap(latex = latex, config = config)
        }
    }
}

/**
 * This text with every occurrence of [query] (ignoring case) given [hit]: the chat
 * search's mark. Found in the text itself, so the ranges never drift from it.
 */
fun AnnotatedString.withSearchHits(query: String, hit: SpanStyle): AnnotatedString {
    if (query.isBlank()) return this
    val plain = text
    var at = plain.indexOf(query, 0, ignoreCase = true)
    if (at < 0) return this
    val source = this
    return buildAnnotatedString {
        append(source)
        while (at >= 0) {
            addStyle(hit, at, at + query.length)
            at = plain.indexOf(query, at + query.length, ignoreCase = true)
        }
    }
}

private const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"

private val DefaultFontSize = 14.sp
private val TableCellWidth = 132.dp

/** Gap between two table columns; between header cells it is the resize handle. */
private val SeamWidth = 20.dp

/** A column can't be dragged narrower than this, as in Desktop. */
private val MinColumnWidth = 48.dp
private const val MaxQuoteBars = 3

private fun bulletFor(depth: Int): String = when (depth % 3) {
    0 -> "•"
    1 -> "◦"
    else -> "▪"
}

private fun Int.localizeDigits(persian: Boolean): String =
    if (!persian) toString() else toString().map { PERSIAN_DIGITS[it - '0'] }.joinToString("")

private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

package com.hermes.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
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
import com.hermes.android.ui.i18n.t

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
 * 5. **Block coverage.** Nested lists (by indent), task lists, tables and
 *    thematic breaks render instead of leaking their raw syntax.
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
    persianDigits: Boolean = t("en", "fa") == "fa",
) {
    val parser = remember { IncrementalMdParser() }
    val blocks = remember(markdown) { parser.parse(markdown) }

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
    val onCode = MaterialTheme.colorScheme.onSurfaceVariant
    val muted = textColor.copy(alpha = 0.72f)
    val rule = MaterialTheme.colorScheme.outlineVariant
    val inlineStyle = InlineStyle(linkColor, codeBg, onCode)

    Column(modifier) {
        blocks.forEach { block ->
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

                is MdBlock.Code -> CodeBlock(block, body, base, codeBg, onCode, muted)

                is MdBlock.Rule -> Spacer(
                    Modifier
                        .padding(vertical = 10.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(rule),
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
                            style = body.copy(color = muted, fontStyle = FontStyle.Italic),
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
                            ordinal != null -> "${ordinal.localizeDigits(persianDigits)}."
                            else -> bulletFor(block.depth)
                        }
                        Text(
                            text = marker,
                            style = body.copy(color = if (checked == true) linkColor else muted),
                        )
                        Spacer(Modifier.width(7.dp))
                        MdText(
                            raw = block.text,
                            style = if (checked == true) {
                                body.copy(color = muted, textDecoration = TextDecoration.LineThrough)
                            } else {
                                body
                            },
                            inlineStyle = inlineStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MdBlock.Table -> MdTable(block, body, inlineStyle, rule, muted)

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
    }
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

@Composable
private fun CodeBlock(
    block: MdBlock.Code,
    style: TextStyle,
    base: TextUnit,
    codeBg: Color,
    onCode: Color,
    muted: Color,
) {
    // Code is always LTR, whatever the surrounding prose direction is.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            Modifier
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(codeBg),
        ) {
            if (block.language.isNotBlank()) {
                Text(
                    text = block.language,
                    style = style.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = base * 0.82f,
                        color = muted,
                    ),
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                )
            }
            Box(
                Modifier
                    .padding(10.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                Text(
                    text = block.code,
                    style = style.copy(
                        fontFamily = FontFamily.Monospace,
                        color = onCode,
                        lineHeight = base * 1.5f,
                        textDirection = TextDirection.Ltr,
                    ),
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun MdTable(
    block: MdBlock.Table,
    style: TextStyle,
    inlineStyle: InlineStyle,
    rule: Color,
    muted: Color,
) {
    // The scroll container hands its child unbounded width, where a divider
    // that fills the width would collapse to nothing; the column is pinned to
    // its widest row so the dividers span the table.
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(inlineStyle.codeBg.copy(alpha = 0.45f))
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            block.rows.forEachIndexed { index, row ->
                val header = index == 0 && block.hasHeader
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    row.forEach { cell ->
                        MdText(
                            raw = cell,
                            style = if (header) {
                                style.copy(fontWeight = FontWeight.Bold)
                            } else {
                                style.copy(color = muted)
                            },
                            inlineStyle = inlineStyle,
                            modifier = Modifier.width(TableCellWidth),
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
) {
    val text = remember(raw, inlineStyle) { inline(raw, inlineStyle) }
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
            textAlign = TextAlign.Start,
        ),
        modifier = modifier,
    )
}

private val DefaultFontSize = 14.sp
private val TableCellWidth = 132.dp
private const val MaxQuoteBars = 3

private fun bulletFor(depth: Int): String = when (depth % 3) {
    0 -> "•"
    1 -> "◦"
    else -> "▪"
}

private fun Int.localizeDigits(persian: Boolean): String =
    if (!persian) toString() else toString().map { PERSIAN_DIGITS[it - '0'] }.joinToString("")

private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

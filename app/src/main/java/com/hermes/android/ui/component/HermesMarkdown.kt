package com.hermes.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Native-Compose markdown renderer for Hermes assistant/tool output.
 *
 * ## Why this is native
 * The previous implementation wrapped Markwon in an Android `TextView` via
 * `AndroidView` (the `compose-markdown` library). Inside a `LazyColumn` that
 * View interop is expensive, and during streaming it re-parsed + re-laid-out
 * the whole TextView on every token. This renderer parses markdown into a
 * light block list and emits pure Compose `Text` / `AnnotatedString` — no View
 * interop, cheap to recompose while streaming.
 *
 * Scope: prose-level markdown (headings, bold, italic, inline code, links,
 * bullet/numbered lists, blockquotes, fenced code). Code/images/mermaid/html
 * are already split out upstream by [parseContentBlocks], so this only needs
 * to render the text segments.
 *
 * The signature is unchanged so every call site keeps working.
 */
@Composable
fun HermesMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
    ),
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val onCode = MaterialTheme.colorScheme.onSurfaceVariant
    val uriHandler = LocalUriHandler.current

    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        3 -> 16.sp
                        else -> 15.sp
                    }
                    MdText(
                        text = inline(block.text, linkColor, codeBg),
                        style = style.copy(fontWeight = FontWeight.Bold, fontSize = size),
                        uriHandler = uriHandler,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }
                is MdBlock.Code -> {
                    Column(
                        Modifier
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(codeBg)
                            .padding(10.dp)
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = block.code,
                            style = style.copy(fontFamily = FontFamily.Monospace, color = onCode),
                        )
                    }
                }
                is MdBlock.Quote -> Row(Modifier.padding(vertical = 2.dp)) {
                    Spacer(
                        Modifier
                            .width(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(linkColor.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.width(8.dp))
                    MdText(
                        inline(block.text, linkColor, codeBg),
                        style.copy(color = style.color.copy(alpha = 0.85f)),
                        uriHandler,
                    )
                }
                is MdBlock.ListItem -> Row(Modifier.padding(start = 4.dp, top = 1.dp, bottom = 1.dp)) {
                    Text(block.marker, style = style)
                    Spacer(Modifier.width(6.dp))
                    MdText(inline(block.text, linkColor, codeBg), style, uriHandler)
                }
                is MdBlock.Para -> MdText(
                    inline(block.text, linkColor, codeBg),
                    style,
                    uriHandler,
                    Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun MdText(
    text: AnnotatedString,
    style: TextStyle,
    uriHandler: UriHandler,
    modifier: Modifier = Modifier,
) {
    ClickableText(
        text = text,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                runCatching { uriHandler.openUri(it.item) }
            }
        },
    )
}

// ── Parsing ──────────────────────────────────────────────────────────────

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Code(val code: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class ListItem(val marker: String, val text: String) : MdBlock()
    data class Para(val text: String) : MdBlock()
}

private val headingRe = Regex("^(#{1,6})\\s+(.*)")
private val bulletRe = Regex("^[-*+]\\s+(.*)")
private val numberRe = Regex("^(\\d+)[.)]\\s+(.*)")

private fun parseMarkdownBlocks(md: String): List<MdBlock> {
    val lines = md.split("\n")
    val out = ArrayList<MdBlock>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out.add(MdBlock.Para(para.toString().trim()))
        para.setLength(0)
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trimStart()
        when {
            t.startsWith("```") -> {
                flush(); i++
                val sb = StringBuilder()
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    sb.append(lines[i]).append('\n'); i++
                }
                out.add(MdBlock.Code(sb.toString().trimEnd('\n'))); i++
            }
            headingRe.matches(t) -> {
                val m = headingRe.find(t); if (m != null) { flush(); out.add(MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])); }; i++
            }
            t.startsWith(">") -> {
                flush(); out.add(MdBlock.Quote(t.removePrefix(">").trim())); i++
            }
            bulletRe.matches(t) -> {
                val bm = bulletRe.find(t); if (bm != null) { flush(); out.add(MdBlock.ListItem("•", bm.groupValues[1])); }; i++
            }
            numberRe.matches(t) -> {
                val m = numberRe.find(t); if (m != null) { flush(); out.add(MdBlock.ListItem("${m.groupValues[1]}.", m.groupValues[2])); }; i++
            }
            t.isBlank() -> { flush(); i++ }
            else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line); i++ }
        }
    }
    flush()
    return out
}

// Inline markdown -> AnnotatedString: code spans, bold, italic (* or _), and links.
private fun inline(text: String, linkColor: Color, codeBg: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(c); i++ }
                }
                c == '*' && i + 1 < text.length && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(c); i++ }
                }
                c == '*' || c == '_' -> {
                    val end = text.indexOf(c, i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(c); i++ }
                }
                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    if (close > i && close + 1 < text.length && text[close + 1] == '(') {
                        val pClose = text.indexOf(')', close + 2)
                        if (pClose > close) {
                            val label = text.substring(i + 1, close)
                            val url = text.substring(close + 2, pClose)
                            pushStringAnnotation("URL", url)
                            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                                append(label)
                            }
                            pop()
                            i = pClose + 1
                        } else { append(c); i++ }
                    } else { append(c); i++ }
                }
                else -> { append(c); i++ }
            }
        }
    }

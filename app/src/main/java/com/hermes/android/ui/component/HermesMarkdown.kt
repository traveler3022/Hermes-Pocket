package com.hermes.android.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import dev.jeziellago.compose.markdowntext.MarkdownText

/**
 * Single source of truth for rendering Hermes assistant/tool markdown.
 *
 * ## Why this wrapper exists
 * Hermes Agent sends assistant content as raw **markdown text** (the
 * `message.delta` / `message.complete` `text` field). It is NOT a structured
 * block system — mirroring Hermes' own TUI, which parses markdown on the fly.
 * The sibling `rendered` field is ANSI terminal output and is useless on
 * Android, so we always render `text`.
 *
 * Centralizing the renderer here means link colors and image handling are
 * configured in exactly one place — change it once, every bubble updates.
 *
 * ## Images
 * `compose-markdown` 0.7.x renders `![alt](url)` images through its own
 * bundled Coil 3 image loader, so no custom [coil.ImageLoader] is wired here.
 * (An earlier attempt passed a Coil 2 loader, which fails to compile against
 * 0.7.x's Coil 3 `imageLoader` parameter.)
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
    MarkdownText(
        markdown = markdown,
        modifier = modifier,
        style = style,
        linkColor = linkColor,
    )
}

package com.hermes.android.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.request.CachePolicy
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun HermesMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
    ),
) {
    val context = LocalContext.current
    val imageLoader = ImageLoader.Builder(context)
        .components { add(GifDecoder.Factory()) }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()

    MarkdownText(
        markdown = markdown,
        modifier = modifier,
        style = style,
        linkColor = MaterialTheme.colorScheme.primary,
        imageLoader = imageLoader,
    )
}

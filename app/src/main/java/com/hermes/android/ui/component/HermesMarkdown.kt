package com.hermes.android.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.request.CachePolicy
import dev.jeziellago.compose.markdowntext.MarkdownText

private object HermesImageLoaderHolder {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: android.content.Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .components { add(GifDecoder.Factory()) }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
                .also { instance = it }
        }
}

@Composable
fun HermesMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
    ),
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    val context = LocalContext.current
    val imageLoader = remember { HermesImageLoaderHolder.get(context) }

    MarkdownText(
        markdown = markdown,
        modifier = modifier,
        style = style,
        linkColor = linkColor,
        imageLoader = imageLoader,
    )
}

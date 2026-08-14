package com.hermes.android.ui.screen.message

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import com.hermes.android.ui.component.HermesMarkdown

/**
 * Aether-style streaming text with chunk-by-chunk fade animation.
 *
 * When [markdown] changes (grows), the new content fades in over [fadeDurationMillis].
 * Uses [HermesMarkdown] for rendering.
 */
@Composable
internal fun StreamingMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
    ),
    fadeDurationMillis: Int = 400,
) {
    var trackedSource by remember { mutableStateOf("") }
    val fadeProgress = remember { Animatable(1f) }

    LaunchedEffect(markdown) {
        if (markdown.isBlank()) {
            trackedSource = ""
            fadeProgress.snapTo(1f)
            return@LaunchedEffect
        }

        // Only fade if the text actually grew (new content appended)
        if (markdown.startsWith(trackedSource) && trackedSource.isNotEmpty()) {
            fadeProgress.snapTo(0f)
            fadeProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = fadeDurationMillis,
                    easing = LinearEasing,
                ),
            )
        } else {
            fadeProgress.snapTo(1f)
        }
        trackedSource = markdown
    }

    HermesMarkdown(
        markdown = markdown,
        style = style,
        modifier = modifier.alpha(fadeProgress.value),
    )
}
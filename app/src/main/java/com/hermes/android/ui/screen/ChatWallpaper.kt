package com.hermes.android.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * Telegram-style chat wallpaper: a soft vertical gradient with a staggered
 * grid of tiny, barely-visible doodle icons (pencil, pin, star, ...) —
 * the same feel as Telegram's default background, but drawn from our own
 * Material icons (no copyrighted asset) and tinted from the active theme,
 * so it looks right in both light and dark mode.
 */
@Composable
internal fun ChatWallpaper(modifier: Modifier = Modifier) {
    val painters = listOf(
        rememberVectorPainter(Icons.Default.Edit),
        rememberVectorPainter(Icons.AutoMirrored.Filled.Send),
        rememberVectorPainter(Icons.Default.AttachFile),
        rememberVectorPainter(Icons.Default.Star),
        rememberVectorPainter(Icons.Default.Search),
        rememberVectorPainter(Icons.Default.PushPin),
        rememberVectorPainter(Icons.Default.Psychology),
        rememberVectorPainter(Icons.Default.History),
        rememberVectorPainter(Icons.Default.Settings),
    )
    val top = MaterialTheme.colorScheme.surface
    val bottom = MaterialTheme.colorScheme.surfaceVariant
    val doodle = MaterialTheme.colorScheme.primary

    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        val cell = 72.dp.toPx()
        val icon = 22.dp.toPx()
        var row = 0
        var y = -cell / 2
        while (y < size.height + cell) {
            val xStagger = if (row % 2 == 0) 0f else cell / 2
            var col = 0
            var x = -cell / 2
            while (x < size.width + cell) {
                val painter = painters[(row * 5 + col * 3) % painters.size]
                val rot = if ((row + col) % 2 == 0) -16f else 14f
                translate(x + xStagger, y) {
                    rotate(rot, pivot = Offset(icon / 2, icon / 2)) {
                        with(painter) {
                            draw(
                                size = Size(icon, icon),
                                alpha = 0.06f,
                                colorFilter = ColorFilter.tint(doodle),
                            )
                        }
                    }
                }
                x += cell
                col++
            }
            y += cell
            row++
        }
    }
}

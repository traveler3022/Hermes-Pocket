package com.hermes.android.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons drawn from [Lucide](https://lucide.dev) (ISC licence), transcribed as
 * Compose [ImageVector]s.
 *
 * Two reasons they are here rather than pulled from the Material set:
 *
 * Material's own icons keep moving. Four of the ones this app used —  `Undo`,
 * `OpenInNew`, `CallSplit`, `Sort` — are deprecated in favour of AutoMirrored
 * variants, and the build carried a warning for each. An icon transcribed into
 * the repo does not deprecate underneath us.
 *
 * And Lucide's line work is a single consistent family: one weight, one join,
 * one corner radius across every glyph, which Material's mixed filled/outlined
 * set is not.
 *
 * Every glyph is stroked, never filled, so tint comes from `Icon(tint = …)` the
 * same way Material's do. To add one: take the `<path>`/`<line>`/`<polyline>`
 * geometry from the icon's SVG on lucide.dev and pass each as its own string.
 */
object HxIcons {

    /** lucide `undo-2` */
    val Undo: ImageVector by lazy {
        lucideIcon(
            name = "Undo",
            "M9 14 4 9l5-5",
            "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11",
        )
    }

    /** lucide `external-link` */
    val ExternalLink: ImageVector by lazy {
        lucideIcon(
            name = "ExternalLink",
            "M15 3h6v6",
            "M10 14 21 3",
            "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
        )
    }

    /** lucide `git-branch` */
    val GitBranch: ImageVector by lazy {
        lucideIcon(
            name = "GitBranch",
            "M18 9a9 9 0 0 1-9 9",
            "M6 3L6 15",
            "M15 6a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z",
            "M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z",
        )
    }

    /** lucide `arrow-up-down` */
    val SortArrows: ImageVector by lazy {
        lucideIcon(
            name = "SortArrows",
            "m21 16-4 4-4-4",
            "M17 20V4",
            "m3 8 4-4 4 4",
            "M7 4v16",
        )
    }

    /** lucide `terminal` */
    val Terminal: ImageVector by lazy {
        lucideIcon(
            name = "Terminal",
            "M4 17L10 11L4 5",
            "M12 19L20 19",
        )
    }

    /** lucide `globe` */
    val Globe: ImageVector by lazy {
        lucideIcon(
            name = "Globe",
            "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
            "M2 12h20",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z",
        )
    }

    /** lucide `wrench` */
    val Wrench: ImageVector by lazy {
        lucideIcon(
            name = "Wrench",
            "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z",
        )
    }

    /** lucide `circle-check` */
    val CircleCheck: ImageVector by lazy {
        lucideIcon(
            name = "CircleCheck",
            "m9 12 2 2 4-4",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z",
        )
    }
    /** lucide `sparkles` */
    val Sparkles: ImageVector by lazy {
        lucideIcon(
            name = "Sparkles",
            "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z",
            "M20 3v4",
            "M22 5h-4",
            "M4 17v2",
            "M5 18H3",
        )
    }
}

/**
 * Builds one Lucide glyph. Every Lucide icon is a 24×24 outline stroked at 2px
 * with round caps and joins, so those are fixed here rather than repeated at
 * each call site.
 */
private fun lucideIcon(name: String, vararg pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        pathData.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                // Black is a placeholder the way Material's icons use it: an
                // Icon() tint replaces it with a colour filter.
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

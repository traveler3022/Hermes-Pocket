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

    /** lucide `smile-plus` */
    val SmilePlus: ImageVector by lazy {
        lucideIcon(
            name = "SmilePlus",
            "M13.267 2.08a10 10 0 1 0 8.653 8.653",
            "M15 10V9",
            "M16 5h6",
            "M16.472 15a6 6 0 0 1-8.943 0",
            "M19 2v6",
            "M9 10V9",
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

    // ── Chat chrome & drawer (frames 7a/7b) ──────────────────────────────

    /** Two lines, the lower one shorter — opens the drawer. Drawn for this app. */
    val MenuShort: ImageVector by lazy {
        lucideIcon(
            name = "MenuShort",
            "M4 9h16",
            "M4 15h9",
        )
    }

    /**
     * New chat: a speech bubble whose outline is broken into three arcs, with
     * its tail at the lower left. Drawn for this app on a r=8 circle.
     */
    val NewChat: ImageVector by lazy {
        lucideIcon(
            name = "NewChat",
            "M4.48 14.74A8 8 0 0 1 14.07 4.27",
            "M17.66 6.34A8 8 0 0 1 19.73 14.07",
            "M17.66 17.66A8 8 0 0 1 9.26 19.52",
            "M4.48 14.74L3.6 20.4L7.6 18.9",
        )
    }

    /** lucide `search` */
    val Search: ImageVector by lazy {
        lucideIcon(
            name = "Search",
            "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z",
            "m21 21-4.3-4.3",
        )
    }

    /** lucide `layout-grid` */
    val LayoutGrid: ImageVector by lazy {
        lucideIcon(
            name = "LayoutGrid",
            "M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z",
            "M15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z",
            "M15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1Z",
            "M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1Z",
        )
    }

    /** lucide `bot` */
    val Bot: ImageVector by lazy {
        lucideIcon(
            name = "Bot",
            "M12 8V4H8",
            "M6 8h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2Z",
            "M2 14h2",
            "M20 14h2",
            "M15 13v2",
            "M9 13v2",
        )
    }

    /** lucide `clock` */
    val Clock: ImageVector by lazy {
        lucideIcon(
            name = "Clock",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z",
            "M12 6v6l4 2",
        )
    }

    /** lucide `server` */
    val Server: ImageVector by lazy {
        lucideIcon(
            name = "Server",
            "M4 2h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Z",
            "M4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2Z",
            "M6 6h.01",
            "M6 18h.01",
        )
    }

    /** lucide `square-pen` */
    val SquarePen: ImageVector by lazy {
        lucideIcon(
            name = "SquarePen",
            "M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7",
            "M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852Z",
        )
    }

    /** lucide `user` */
    val User: ImageVector by lazy {
        lucideIcon(
            name = "User",
            "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
            "M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z",
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

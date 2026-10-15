/*
 * Transport and volume glyphs of SimpMusic's Apple Music style
 * (https://github.com/maxrave-dev/SimpMusic, GPL-3.0), as drawn there.
 */

package com.hermes.android.ui.viewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object AppleMusicIcons {
    val FastRewind: ImageVector by lazy {
      ImageVector.Builder(
          name = "FastRewind",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(19.95f, 16.98f)
            lineToRelative(-6.2f, -4.15f)
            quadTo(13.53f, 12.68f, 13.41f, 12.46f)
            reflectiveQuadTo(13.3f, 12f)
            reflectiveQuadToRelative(0.11f, -0.46f)
            reflectiveQuadToRelative(0.34f, -0.36f)
            lineToRelative(6.2f, -4.15f)
            quadTo(20.08f, 6.93f, 20.23f, 6.9f)
            quadTo(20.38f, 6.88f, 20.5f, 6.88f)
            quadToRelative(0.4f, 0f, 0.7f, 0.27f)
            quadToRelative(0.3f, 0.28f, 0.3f, 0.73f)
            verticalLineToRelative(8.25f)
            quadToRelative(0f, 0.45f, -0.3f, 0.73f)
            reflectiveQuadToRelative(-0.7f, 0.27f)
            quadToRelative(-0.13f, 0f, -0.27f, -0.02f)
            reflectiveQuadTo(19.95f, 16.98f)
            close()
            moveToRelative(-10f, 0f)
            lineTo(3.75f, 12.83f)
            quadTo(3.53f, 12.68f, 3.41f, 12.46f)
            reflectiveQuadTo(3.3f, 12f)
            reflectiveQuadTo(3.41f, 11.54f)
            reflectiveQuadTo(3.75f, 11.18f)
            lineTo(9.95f, 7.02f)
            quadTo(10.08f, 6.93f, 10.23f, 6.9f)
            quadTo(10.38f, 6.88f, 10.5f, 6.88f)
            quadToRelative(0.4f, 0f, 0.7f, 0.27f)
            quadToRelative(0.3f, 0.28f, 0.3f, 0.73f)
            verticalLineToRelative(8.25f)
            quadToRelative(0f, 0.45f, -0.3f, 0.73f)
            reflectiveQuadToRelative(-0.7f, 0.27f)
            quadToRelative(-0.13f, 0f, -0.27f, -0.02f)
            reflectiveQuadTo(9.95f, 16.98f)
            close()
          }
        }
        .build()
    }

    val FastForward: ImageVector by lazy {
      ImageVector.Builder(
          name = "FastForward",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(2.5f, 16.13f)
            verticalLineTo(7.88f)
            quadTo(2.5f, 7.43f, 2.8f, 7.15f)
            quadTo(3.1f, 6.88f, 3.5f, 6.88f)
            quadToRelative(0.13f, 0f, 0.28f, 0.02f)
            quadTo(3.93f, 6.93f, 4.05f, 7.02f)
            lineToRelative(6.2f, 4.15f)
            quadToRelative(0.23f, 0.15f, 0.34f, 0.36f)
            reflectiveQuadTo(10.7f, 12f)
            reflectiveQuadToRelative(-0.11f, 0.46f)
            quadToRelative(-0.11f, 0.21f, -0.34f, 0.36f)
            lineToRelative(-6.2f, 4.15f)
            quadTo(3.93f, 17.08f, 3.78f, 17.1f)
            reflectiveQuadTo(3.5f, 17.13f)
            quadToRelative(-0.4f, 0f, -0.7f, -0.27f)
            reflectiveQuadTo(2.5f, 16.13f)
            close()
            moveToRelative(10f, 0f)
            verticalLineTo(7.88f)
            quadToRelative(0f, -0.45f, 0.3f, -0.73f)
            quadTo(13.1f, 6.88f, 13.5f, 6.88f)
            quadToRelative(0.13f, 0f, 0.28f, 0.02f)
            quadToRelative(0.15f, 0.03f, 0.28f, 0.13f)
            lineToRelative(6.2f, 4.15f)
            quadToRelative(0.23f, 0.15f, 0.34f, 0.36f)
            reflectiveQuadTo(20.7f, 12f)
            reflectiveQuadToRelative(-0.11f, 0.46f)
            quadToRelative(-0.11f, 0.21f, -0.34f, 0.36f)
            lineToRelative(-6.2f, 4.15f)
            quadToRelative(-0.13f, 0.1f, -0.28f, 0.13f)
            reflectiveQuadTo(13.5f, 17.13f)
            quadToRelative(-0.4f, 0f, -0.7f, -0.27f)
            reflectiveQuadTo(12.5f, 16.13f)
            close()
          }
        }
        .build()
    }

    val PlayArrow: ImageVector by lazy {
      ImageVector.Builder(
          name = "PlayArrow",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(8f, 17.18f)
            verticalLineTo(6.82f)
            quadTo(8f, 6.4f, 8.3f, 6.11f)
            quadTo(8.6f, 5.82f, 9f, 5.82f)
            quadToRelative(0.13f, 0f, 0.26f, 0.04f)
            reflectiveQuadTo(9.53f, 5.97f)
            lineToRelative(8.15f, 5.18f)
            quadToRelative(0.23f, 0.15f, 0.34f, 0.38f)
            quadToRelative(0.11f, 0.23f, 0.11f, 0.48f)
            reflectiveQuadToRelative(-0.11f, 0.47f)
            reflectiveQuadToRelative(-0.34f, 0.38f)
            lineTo(9.53f, 18.02f)
            quadTo(9.4f, 18.1f, 9.26f, 18.14f)
            quadTo(9.13f, 18.18f, 9f, 18.18f)
            quadToRelative(-0.4f, 0f, -0.7f, -0.29f)
            reflectiveQuadTo(8f, 17.18f)
            close()
          }
        }
        .build()
    }

    val Pause: ImageVector by lazy {
      ImageVector.Builder(
          name = "Pause",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(16f, 19f)
            quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
            reflectiveQuadTo(14f, 17f)
            verticalLineTo(7f)
            quadTo(14f, 6.18f, 14.59f, 5.59f)
            reflectiveQuadTo(16f, 5f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            quadTo(18f, 6.18f, 18f, 7f)
            verticalLineTo(17f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(16f, 19f)
            close()
            moveTo(8f, 19f)
            quadTo(7.18f, 19f, 6.59f, 18.41f)
            reflectiveQuadTo(6f, 17f)
            verticalLineTo(7f)
            quadTo(6f, 6.18f, 6.59f, 5.59f)
            reflectiveQuadTo(8f, 5f)
            quadTo(8.83f, 5f, 9.41f, 5.59f)
            quadTo(10f, 6.18f, 10f, 7f)
            verticalLineTo(17f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            quadTo(8.83f, 19f, 8f, 19f)
            close()
          }
        }
        .build()
    }

    val VolumeDown: ImageVector by lazy {
      ImageVector.Builder(
          name = "VolumeDown",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(9f, 15f)
            horizontalLineTo(6f)
            quadTo(5.58f, 15f, 5.29f, 14.71f)
            reflectiveQuadTo(5f, 14f)
            verticalLineTo(10f)
            quadTo(5f, 9.57f, 5.29f, 9.29f)
            reflectiveQuadTo(6f, 9f)
            horizontalLineTo(9f)
            lineTo(12.3f, 5.7f)
            quadTo(12.78f, 5.22f, 13.39f, 5.49f)
            reflectiveQuadTo(14f, 6.43f)
            verticalLineTo(17.58f)
            quadToRelative(0f, 0.68f, -0.61f, 0.94f)
            reflectiveQuadTo(12.3f, 18.3f)
            lineTo(9f, 15f)
            close()
            moveToRelative(9.5f, -3f)
            quadToRelative(0f, 1.05f, -0.47f, 1.99f)
            reflectiveQuadToRelative(-1.25f, 1.54f)
            quadToRelative(-0.25f, 0.15f, -0.51f, 0.01f)
            reflectiveQuadTo(16f, 15.1f)
            verticalLineTo(8.85f)
            quadToRelative(0f, -0.3f, 0.26f, -0.44f)
            quadToRelative(0.26f, -0.14f, 0.51f, 0.01f)
            quadTo(17.55f, 9.05f, 18.03f, 10f)
            reflectiveQuadToRelative(0.47f, 2f)
            close()
          }
        }
        .build()
    }

    val VolumeUp: ImageVector by lazy {
      ImageVector.Builder(
          name = "VolumeUp",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(19f, 11.98f)
            quadTo(19f, 9.9f, 17.9f, 8.19f)
            quadTo(16.8f, 6.47f, 14.95f, 5.63f)
            quadTo(14.58f, 5.45f, 14.4f, 5.09f)
            reflectiveQuadTo(14.35f, 4.35f)
            quadTo(14.5f, 3.95f, 14.89f, 3.77f)
            reflectiveQuadToRelative(0.79f, 0f)
            quadToRelative(2.43f, 1.07f, 3.88f, 3.29f)
            quadTo(21f, 9.27f, 21f, 11.98f)
            reflectiveQuadToRelative(-1.45f, 4.91f)
            reflectiveQuadToRelative(-3.88f, 3.29f)
            quadToRelative(-0.4f, 0.18f, -0.79f, 0f)
            reflectiveQuadTo(14.35f, 19.6f)
            quadTo(14.23f, 19.23f, 14.4f, 18.86f)
            reflectiveQuadToRelative(0.55f, -0.54f)
            quadTo(16.8f, 17.48f, 17.9f, 15.76f)
            reflectiveQuadTo(19f, 11.98f)
            close()
            moveTo(7f, 15f)
            horizontalLineTo(4f)
            quadTo(3.58f, 15f, 3.29f, 14.71f)
            reflectiveQuadTo(3f, 14f)
            verticalLineTo(10f)
            quadTo(3f, 9.57f, 3.29f, 9.29f)
            reflectiveQuadTo(4f, 9f)
            horizontalLineTo(7f)
            lineTo(10.3f, 5.7f)
            quadTo(10.78f, 5.22f, 11.39f, 5.49f)
            reflectiveQuadTo(12f, 6.43f)
            verticalLineTo(17.58f)
            quadToRelative(0f, 0.68f, -0.61f, 0.94f)
            reflectiveQuadTo(10.3f, 18.3f)
            lineTo(7f, 15f)
            close()
            moveToRelative(9.5f, -3f)
            quadToRelative(0f, 1.05f, -0.47f, 1.99f)
            reflectiveQuadToRelative(-1.25f, 1.54f)
            quadToRelative(-0.25f, 0.15f, -0.51f, 0.01f)
            reflectiveQuadTo(14f, 15.1f)
            verticalLineTo(8.85f)
            quadToRelative(0f, -0.3f, 0.26f, -0.44f)
            quadToRelative(0.26f, -0.14f, 0.51f, 0.01f)
            quadTo(15.55f, 9.05f, 16.03f, 10f)
            reflectiveQuadToRelative(0.47f, 2f)
            close()
          }
        }
        .build()
    }

}

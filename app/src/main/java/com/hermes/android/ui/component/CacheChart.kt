/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 *
 * Modified for Hermes by traveler3022, 2026-09-29: org.telegram.ui.Components.CacheChart
 * (cache type), AnimatedFloat and CircularProgressDrawable.getSegments ported to Kotlin and
 * Jetpack Compose. The particle icons are the paths of Telegram's res/raw/cache_*.svg.
 */

package com.hermes.android.ui.component

import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Telegram's CubicBezierInterpolator.EASE_OUT_QUINT and EASE_OUT. */
internal val EaseOutQuint: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
internal val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** One slot of the chart: [size] bytes, drawn only while [selected]. */
data class CacheChartSegment(val size: Long, val selected: Boolean)

/** Telegram's statisticChartLine_* defaults, in CacheChart.DEFAULT_COLORS order. */
val CacheChartColors: List<Color> = listOf(
    Color(0xFF58A8ED), // lightblue
    Color(0xFF327FE5), // blue
    Color(0xFF61C752), // green
    Color(0xFF9F79E8), // purple
    Color(0xFF8FCF39), // lightgreen
    Color(0xFFE05356), // red
    Color(0xFFF28C39), // orange
    Color(0xFF40D0CA), // cyan
    Color(0xFF9F79E8), // purple
    Color(0xFFEBA52D), // golden
    Color(0xFFEBA52D), // golden
)

/**
 * A value that eases to whatever it was last [set] to, on the clock — Telegram's AnimatedFloat.
 * The chart redraws every frame anyway, so nothing has to be told to invalidate.
 */
internal class AnimatedFloat(private val duration: Long, private val easing: Easing) {
    private var value = 0f
    private var target = 0f
    private var firstSet = true
    private var transition = false
    private var start = 0L
    private var startValue = 0f

    fun set(mustBe: Float, force: Boolean = false): Float {
        if (force || duration <= 0 || firstSet) {
            value = mustBe
            target = mustBe
            transition = false
            firstSet = false
        } else if (abs(target - mustBe) > 0.0001f) {
            transition = true
            target = mustBe
            startValue = value
            start = SystemClock.elapsedRealtime()
        }
        return get()
    }

    fun get(): Float {
        if (transition) {
            val now = SystemClock.elapsedRealtime()
            val t = ((now - start) / duration.toFloat()).coerceIn(0f, 1f)
            value = lerp(startValue, target, easing.transform(t))
            if (t >= 1f) transition = false
        }
        return value
    }
}

/**
 * Text that fades over to its new value, standing in for the chart's AnimatedTextDrawables
 * (which roll each changed character; the fade keeps the same timing and easing).
 */
internal class AnimatedText(private val duration: Long) {
    var text: String = ""
        private set
    private var old: String? = null
    private var changedAt = 0L

    fun set(value: String, animated: Boolean) {
        if (value == text) return
        old = if (animated && text.isNotEmpty()) text else null
        text = value
        changedAt = SystemClock.elapsedRealtime()
    }

    /** Draws centred on ([x], baseline [y]). */
    fun draw(canvas: android.graphics.Canvas, paint: Paint, x: Float, y: Float, alpha: Float) {
        val t = if (old == null) 1f else EaseOutQuint.transform(((SystemClock.elapsedRealtime() - changedAt) / duration.toFloat()).coerceIn(0f, 1f))
        val base = paint.alpha
        old?.takeIf { t < 1f }?.let {
            paint.alpha = (base * alpha * (1f - t)).toInt()
            canvas.drawText(it, x - paint.measureText(it) / 2f, y, paint)
        }
        paint.alpha = (base * alpha * t).toInt()
        canvas.drawText(text, x - paint.measureText(text) / 2f, y, paint)
        paint.alpha = base
    }
}

private const val SEPARATOR_ANGLE = 2f
private const val SECTIONS_COUNT = 11

/** The chart's model and animation state; hand it new sizes with [setSegments]. */
class CacheChartState {
    internal val sectors = Array(SECTIONS_COUNT) { Sector() }
    internal var loading = true
    internal val loadingFloat = AnimatedFloat(750, EaseOutQuint)
    internal var complete = false
    internal val completeFloat = AnimatedFloat(650, EaseOutQuint)
    internal val topText = AnimatedText(450)
    internal val bottomText = AnimatedText(450)
    private var firstSet = true

    internal class Sector {
        var angleCenter = 0f
        var angleSize = 0f
        val angleCenterAnimated = AnimatedFloat(650, EaseOutQuint)
        val angleSizeAnimated = AnimatedFloat(650, EaseOutQuint)
        var textAlpha = 0f
        val textAlphaAnimated = AnimatedFloat(150, EaseOut)
        var textScale = 1f
        val textScaleAnimated = AnimatedFloat(150, EaseOut)
        val text = AnimatedText(200)
        var particlesAlpha = 0f
        val particlesAlphaAnimated = AnimatedFloat(150, EaseOut)
        var selected = false
        val selectedAnimated = AnimatedFloat(200, EaseOutQuint)
    }

    /**
     * [totalSize] < 0 keeps the loading spinner; 0 shows the green "all clear" tick. Slot 10 is
     * the collapsed "Other" group and is drawn first, as in Telegram.
     */
    fun setSegments(totalSize: Long, segments: List<CacheChartSegment?>, formatSize: (Long) -> Pair<String, String>) {
        val animated = !firstSet
        firstSet = false
        if (totalSize < 0) {
            loading = true
            return
        }
        loading = false
        if (!animated) loadingFloat.set(0f, force = true)
        val slots = List(SECTIONS_COUNT) { segments.getOrNull(it) ?: CacheChartSegment(0, false) }
        val segmentsSum = slots.filter { it.selected }.sumOf { it.size }
        if (segmentsSum <= 0) {
            complete = totalSize <= 0
            if (!animated) completeFloat.set(if (complete) 1f else 0f, force = true)
            topText.set("0", animated)
            bottomText.set("KB", animated)
            sectors.forEach {
                it.textAlpha = 0f
                if (!animated) it.textAlphaAnimated.set(0f, force = true)
            }
            return
        }
        val segmentsCount = slots.count { it.selected && it.size > 0 }
        var underCount = 0
        var minus = 0f
        slots.forEach {
            val progress = if (it.selected) it.size.toFloat() / segmentsSum else 0f
            if (progress > 0 && progress < .02f) {
                underCount++
                minus += progress
            }
        }
        val percents = roundPercents(FloatArray(SECTIONS_COUNT) { if (slots[it].selected) slots[it].size.toFloat() / segmentsSum else 0f })
        // Smallest first, then the "Other" group swapped into the front.
        val order = slots.indices.sortedBy { slots[it].size }.toMutableList()
        java.util.Collections.swap(order, 0, order.indexOf(SECTIONS_COUNT - 1))
        val sum = 360f - SEPARATOR_ANGLE * (if (segmentsCount < 2) 0 else segmentsCount)
        var prev = 0f
        var k = 0
        for (i in order) {
            val segment = slots[i]
            val sector = sectors[i]
            var progress = if (segment.selected) segment.size.toFloat() / segmentsSum else 0f
            sector.textAlpha = if (progress > .05f && progress < 1f) 1f else 0f
            sector.textScale = if (progress < .08f || percents[i] >= 100) .85f else 1f
            sector.particlesAlpha = 1f
            if (!animated) {
                sector.textAlphaAnimated.set(sector.textAlpha, force = true)
                sector.textScaleAnimated.set(sector.textScale, force = true)
                sector.particlesAlphaAnimated.set(sector.particlesAlpha, force = true)
            }
            if (sector.textAlpha > 0) sector.text.set("${percents[i]}%", animated)
            progress = if (progress < .02f && progress > 0) .02f else progress * (1f - (.02f * underCount - minus))
            val angleFrom = prev * sum + k * SEPARATOR_ANGLE
            val angleTo = angleFrom + progress * sum
            sector.angleCenter = (angleFrom + angleTo) / 2
            sector.angleSize = abs(angleTo - angleFrom) / 2
            if (progress <= 0) sector.textAlpha = 0f
            if (!animated) {
                sector.angleCenterAnimated.set(sector.angleCenter, force = true)
                sector.angleSizeAnimated.set(sector.angleSize, force = true)
                sector.textAlphaAnimated.set(sector.textAlpha, force = true)
            }
            if (progress <= 0) continue
            prev += progress
            k++
        }
        val (top, bottom) = formatSize(segmentsSum)
        topText.set(top, animated)
        bottomText.set(bottom, animated)
        complete = false
        if (!animated) completeFloat.set(0f, force = true)
    }

    internal fun sectorAt(angle: Float): Int = sectors.indexOfFirst {
        angle >= it.angleCenter - it.angleSize && angle <= it.angleCenter + it.angleSize
    }

    internal fun select(index: Int) {
        val chosen = if (index >= 0 && sectors[index].angleSize <= 0) -1 else index
        sectors.forEachIndexed { i, sector -> sector.selected = i == chosen }
    }
}

/** AndroidUtilities.roundPercents: whole percents that add up to 100. */
internal fun roundPercents(values: FloatArray): IntArray {
    val sum = values.sum()
    val output = IntArray(values.size)
    if (sum <= 0) return output
    var roundedSum = 0
    values.forEachIndexed { i, v -> output[i] = floor(v / sum * 100).toInt(); roundedSum += output[i] }
    while (roundedSum < 100) {
        var maxError = 0f
        var maxErrorIndex = -1
        values.forEachIndexed { i, v ->
            val error = v / sum - output[i] / 100f
            if (v > 0 && error >= maxError) {
                maxErrorIndex = i
                maxError = error
            }
        }
        if (maxErrorIndex < 0) break
        output[maxErrorIndex]++
        roundedSum++
    }
    return output
}

/** Telegram's cache particle icons (72×72 viewport), per chart slot. */
private val ParticlePaths = listOf(
    // cache_photos
    "M48,12 C54.627417,12 60,17.372583 60,24 L60,48 C60,54.627417 54.627417,60 48,60 L24,60 C17.372583,60 12,54.627417 12,48 L12,24 C12,17.372583 17.372583,12 24,12 L48,12 Z M44.3651844,34.6306685 C43.8183724,33.9010116 42.7247485,33.9010116 42.1779366,34.6036442 L33.7023513,45.3863509 L27.9608258,38.549196 C27.3866733,37.8735878 26.3477306,37.9006121 25.8282592,38.6032446 L19.9804504,47.2510295 C19.2695949,48.1428323 19.8984286,50.4 21.0467337,50.4 L51.9081103,50.4 C52.9904208,50.4 53.639443,48.3210078 53.0676996,47.374937 L44.3651844,34.6306685 Z",
    // cache_videos
    "M18.5095398,18.85 L36.9719416,18.85 C40.5670612,18.85 43.4814815,21.7644203 43.4814815,25.3595398 L43.4814815,45.6404602 C43.4814815,49.2355797 40.5670612,52.15 36.9719416,52.15 L18.5095398,52.15 C14.9144203,52.15 12,49.2355797 12,45.6404602 L12,25.3595398 C12,21.7644203 14.9144203,18.85 18.5095398,18.85 Z M49.5847133,27.1084323 L56.6902242,21.3231792 C58.0841802,20.1882308 60.1342623,20.3981979 61.2692107,21.7921539 C61.741904,22.3727209 62,23.0984963 62,23.8471598 L62,47.6096485 C62,49.4072083 60.5427899,50.8644185 58.7452301,50.8644185 C58.0575374,50.8644185 57.3875158,50.6465978 56.831289,50.2422056 L49.8668429,45.1788626 C48.1820423,43.9539665 47.1851852,41.9967567 47.1851852,39.9137485 L47.1851852,32.1563935 C47.1851852,30.1985078 48.0664292,28.3446077 49.5847133,27.1084323 Z",
    // cache_documents
    "M43.15125,14.3808511 C42.24875,13.4914894 41.0375,13 39.77875,13 L24.7,13 C21.1375,13 18,16.106383 18,19.6170213 L18,50.3829787 C18,53.893617 21.11375,57 24.67625,57 L49.3,57 C52.8625,57 56,53.893617 56,50.3829787 L56,28.9851064 C56,27.7446809 55.50125,26.5510638 54.59875,25.6851064 L43.15125,14.3808511 Z M39.375,27.0425532 L39.375,16.5106383 L52.4375,29.3829787 L41.75,29.3829787 C40.44375,29.3829787 39.375,28.3297872 39.375,27.0425532 Z",
    // cache_music
    "M36,10 C50.352,10 62,21.648 62,36 C62,50.352 50.352,62 36,62 C21.648,62 10,50.352 10,36 C10,21.648 21.648,10 36,10 Z M33.1111111,23.950754 C31.5156218,23.950754 30.2222222,25.2441536 30.2222222,26.8396429 L30.2222222,45.6296296 C30.2222222,46.2546991 30.4249583,46.8629074 30.8,47.362963 C31.7572936,48.6393544 33.568053,48.8980343 34.8444444,47.9407407 L46.5881862,39.1329344 C46.7600033,39.0040716 46.9169145,38.8564504 47.0560109,38.6928077 C48.089327,37.4771416 47.9415032,35.6539814 46.7258372,34.6206653 L34.9820954,24.6384848 C34.4597827,24.194519 33.7966155,23.950754 33.1111111,23.950754 Z",
    // cache_other
    "M31.2684326,15 L17.9882353,15 C15.2447059,15 13.0249412,17.25 13.0249412,20 L13,49.0697674 C13,52.75 15.2447059,55 18.9294118,55 L55.0705882,55 C58.7552941,55 61,52.75 61,49.0697674 L61,25.9302326 C61,22.25 58.7552941,20.5813953 55.0705882,20.5813953 L40.0468122,20.5813953 C38.7070557,20.5813953 37.430813,20.0103436 36.5380314,19.0113998 L34.0754572,16.2559964 C33.361232,15.4568414 32.3402378,15 31.2684326,15 Z",
)

/** Which of [ParticlePaths] each slot floats: photos, videos, documents, audio, then folders. */
private val SlotParticles = intArrayOf(0, 1, 2, 3, 4, 4, 4, 4, 4, 4, 2)

private fun toRad(angle: Float): Float = (angle / 180f * Math.PI).toFloat()

private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

private fun RectF.setCircle(cx: Float, cy: Float, radius: Float) = set(cx - radius, cy - radius, cx + radius, cy + radius)

/** CircularProgressDrawable.getSegments: the spinning arc while sizes are counted. */
private fun spinnerSegments(t: Float, out: FloatArray) {
    out[0] = max(0f, 1520 * t / 5400f - 20)
    out[1] = 1520 * t / 5400f
    for (i in 0 until 4) {
        out[1] += FastOutSlowIn.transform(((t - i * 1350) / 667f).coerceIn(0f, 1f)) * 250
        out[0] += FastOutSlowIn.transform(((t - (667 + i * 1350)) / 667f).coerceIn(0f, 1f)) * 250
    }
}

/** CircularProgressDrawable's FastOutSlowInInterpolator. */
private val FastOutSlowIn: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** Paints and paths of one sector, reused across frames. */
private class SectorPaints(color: Int, density: Float) {
    val path = Path()
    val rect = RectF()
    val roundingRect = RectF()
    val uncut = Paint(Paint.ANTI_ALIAS_FLAG)
    val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    val gradientMatrix = Matrix()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        shader = RadialGradient(
            0f, 0f, 86 * density,
            intArrayOf(ColorUtils.compositeColors(0x30FFFFFF, color), ColorUtils.compositeColors(0x03000000, color)),
            floatArrayOf(.3f, 1f),
            Shader.TileMode.CLAMP,
        ).also { it.setLocalMatrix(gradientMatrix) }
    }
    val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textSize = 15 * density
    }
    private var last = FloatArray(7) { Float.NaN }

    fun setupPath(outer: RectF, inner: RectF, angleCenter: Float, angleSize: Float, roundingIn: Float) {
        var rounding = min(roundingIn, (outer.width() - inner.width()) / 4)
        rounding = min(rounding, (Math.PI * (angleSize / 180f) * (inner.width() / 2f)).toFloat())
        val thickness = (outer.width() - inner.width()) / 2f
        val key = floatArrayOf(angleCenter, angleSize, rounding, thickness, outer.width(), outer.centerX(), outer.centerY())
        if (key.contentEquals(last)) return
        last = key
        val angleFrom = angleCenter - angleSize
        val angleTo = angleCenter + angleSize
        val hasRounding = rounding > 0
        val roundingOuterAngle = rounding / (Math.PI * (outer.width() - rounding * 2)).toFloat() * 360f
        val roundingInnerAngle = rounding / (Math.PI * (inner.width() + rounding * 2)).toFloat() * 360f +
            SEPARATOR_ANGLE / 4f * (if (angleSize > 175f) 0 else 1)
        val outerRadiusMinusRounding = outer.width() / 2 - rounding
        val innerRadiusPlusRounding = inner.width() / 2 + rounding
        path.rewind()
        if (angleTo - angleFrom < SEPARATOR_ANGLE / 4f) return
        if (hasRounding) {
            roundingRect.setCircle(
                outer.centerX() + outerRadiusMinusRounding * cos(toRad(angleFrom + roundingOuterAngle)),
                outer.centerY() + outerRadiusMinusRounding * sin(toRad(angleFrom + roundingOuterAngle)),
                rounding,
            )
            path.arcTo(roundingRect, angleFrom + roundingOuterAngle - 90, 90f)
        }
        path.arcTo(outer, angleFrom + roundingOuterAngle, angleTo - angleFrom - roundingOuterAngle * 2)
        if (hasRounding) {
            roundingRect.setCircle(
                outer.centerX() + outerRadiusMinusRounding * cos(toRad(angleTo - roundingOuterAngle)),
                outer.centerY() + outerRadiusMinusRounding * sin(toRad(angleTo - roundingOuterAngle)),
                rounding,
            )
            path.arcTo(roundingRect, angleTo - roundingOuterAngle, 90f)
            roundingRect.setCircle(
                inner.centerX() + innerRadiusPlusRounding * cos(toRad(angleTo - roundingInnerAngle)),
                inner.centerY() + innerRadiusPlusRounding * sin(toRad(angleTo - roundingInnerAngle)),
                rounding,
            )
            path.arcTo(roundingRect, angleTo - roundingInnerAngle + 90, 90f)
        }
        path.arcTo(inner, angleTo - roundingInnerAngle, -(angleTo - angleFrom - roundingInnerAngle * 2))
        if (hasRounding) {
            roundingRect.setCircle(
                inner.centerX() + innerRadiusPlusRounding * cos(toRad(angleFrom + roundingInnerAngle)),
                inner.centerY() + innerRadiusPlusRounding * sin(toRad(angleFrom + roundingInnerAngle)),
                rounding,
            )
            path.arcTo(roundingRect, angleFrom + roundingInnerAngle + 180, 90f)
        }
        path.close()
    }
}

/** The particle icons scaled to 16dp, white, like SvgHelper.getBitmap. */
private class Particle(pathData: String, density: Float) {
    val size = 16 * density
    val path: Path = PathParser().parsePathString(pathData).toPath().asAndroidPath().also {
        it.transform(Matrix().apply { setScale(size / 72f, size / 72f) })
    }
}

/**
 * The storage donut of Telegram's Storage Usage screen: a sector per slot of [state], counted
 * sizes easing in, particles drifting through each sector, the total in the middle, and a
 * green tick when nothing is left. [onPressed] gets the slot under the finger, -1 once it lifts
 * (CacheChart.onSectionDown, which the Storage page uses to highlight that section's row).
 */
@Composable
fun CacheChart(
    state: CacheChartState,
    colors: List<Color> = CacheChartColors,
    textColor: Color,
    secondaryTextColor: Color,
    loadingColor: Color,
    modifier: Modifier = Modifier,
    onPressed: (Int) -> Unit = {},
) {
    val density = LocalDensity.current.density
    val sectorPaints = remember(colors, density) { colors.map { SectorPaints(it.toArgb(), density) } }
    val particles = remember(density) { ParticlePaths.map { Particle(it, density) } }
    val chartMeasureBounds = remember { RectF() }
    val chartBounds = remember { RectF() }
    val chartInnerBounds = remember { RectF() }
    val segmentsTmp = remember { FloatArray(2) }
    val clock = remember { object { var start = -1L; var loadedStart: Long? = null } }
    val pressed by rememberUpdatedState(onPressed)
    val loadingBackgroundPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
    val topPaint = remember(density) { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 32 * density } }
    val bottomPaint = remember(density) { Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * density } }
    val complete = remember(density) {
        object {
            val gradient = LinearGradient(
                0f, 0f, 0f, 200 * density,
                intArrayOf(0x006ED556, 0xFF6ED556.toInt(), 0xFF41BA71.toInt(), 0x0041BA71),
                floatArrayOf(0f, .07f, .93f, 1f),
                Shader.TileMode.CLAMP,
            )
            val matrix = Matrix()
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = gradient
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
        }
    }

    // CacheChart invalidates itself on every frame while attached (the particles never stop).
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { frame = it } }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(state) {
                awaitEachGesture {
                    fun indexAt(x: Float, y: Float): Int {
                        val r = hypot(x - chartBounds.centerX(), y - chartBounds.centerY())
                        var a = (atan2(y - chartBounds.centerY(), x - chartBounds.centerX()) / Math.PI * 180f).toFloat()
                        if (a < 0) a += 360
                        return if (r > chartInnerBounds.width() / 2 && r < chartBounds.width() / 2f + 14 * density) state.sectorAt(a) else -1
                    }
                    val down = awaitFirstDown()
                    var index = indexAt(down.position.x, down.position.y)
                    state.select(index)
                    pressed(index)
                    if (index >= 0) down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        index = indexAt(change.position.x, change.position.y)
                        pressed(index)
                        state.select(index)
                        if (index != -1) change.consume()
                    }
                    pressed(-1)
                    state.select(-1)
                }
            },
    ) {
        frame // read so the canvas redraws every frame
        val width = size.width
        val height = size.height
        val d = 172 * density
        chartMeasureBounds.set((width - d) / 2f, (height - d) / 2f, (width + d) / 2f, (height + d) / 2f)

        val loading = state.loadingFloat.set(if (state.loading) 1f else 0f)
        val completeT = state.completeFloat.set(if (state.complete) 1f else 0f)
        chartBounds.set(chartMeasureBounds)
        chartInnerBounds.set(chartBounds)
        val thickness = lerp(38 * density, 10 * density, max(loading, completeT))
        chartInnerBounds.inset(thickness, thickness)
        val rounding = lerp(0f, 60 * density, loading)

        val now = System.currentTimeMillis()
        if (clock.start < 0) clock.start = now
        if (!state.loading && clock.loadedStart == null) clock.loadedStart = now
        else if (state.loading && clock.loadedStart != null) clock.loadedStart = null
        val loadingTime = ((clock.loadedStart ?: now) - clock.start) * 0.6f
        spinnerSegments(loadingTime % 5400, segmentsTmp)
        val minAngle = segmentsTmp[0]
        val maxAngle = segmentsTmp[1]

        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            if (loading > 0) {
                loadingBackgroundPaint.strokeWidth = thickness
                loadingBackgroundPaint.color = loadingColor.toArgb()
                loadingBackgroundPaint.alpha = (loadingBackgroundPaint.alpha * loading).toInt()
                canvas.drawCircle(chartBounds.centerX(), chartBounds.centerY(), (chartBounds.width() - thickness) / 2, loadingBackgroundPaint)
            }
            state.sectors.forEachIndexed { i, sector ->
                spinnerSegments((loadingTime + i * 80) % 5400, segmentsTmp)
                val angleFrom = min(max(segmentsTmp[0], minAngle), maxAngle)
                val angleTo = min(max(segmentsTmp[1], minAngle), maxAngle)
                if (loading >= 1 && angleFrom >= angleTo) return@forEachIndexed
                var angleCenter = (angleFrom + angleTo) / 2
                var angleSize = abs(angleTo - angleFrom) / 2
                if (loading <= 0) {
                    angleCenter = sector.angleCenterAnimated.set(sector.angleCenter)
                    angleSize = sector.angleSizeAnimated.set(sector.angleSize)
                } else if (loading < 1) {
                    val angleCenterSector = sector.angleCenterAnimated.set(sector.angleCenter)
                    angleCenter = lerp(angleCenterSector + floor(maxAngle / 360) * 360, angleCenter, loading)
                    angleSize = lerp(sector.angleSizeAnimated.set(sector.angleSize), angleSize, loading)
                }
                drawSector(
                    canvas, sector, sectorPaints[i], particles[SlotParticles[i]],
                    chartBounds, chartInnerBounds, angleCenter, angleSize, rounding,
                    alpha = 1f - completeT, textAlphaIn = 1f - loading, density = density,
                )
            }

            val textAlpha = (1f - loading) * (1f - completeT)
            if (textAlpha > 0) {
                topPaint.color = textColor.toArgb()
                bottomPaint.color = secondaryTextColor.toArgb()
                // AnimatedTextDrawable centres vertically on its bounds; these are its baselines.
                state.topText.draw(canvas, topPaint, chartBounds.centerX(), chartBounds.centerY() - 5 * density + topPaint.textSize * .35f, textAlpha)
                state.bottomText.draw(canvas, bottomPaint, chartBounds.centerX(), chartBounds.centerY() + 22 * density + bottomPaint.textSize * .35f, textAlpha)
            }

            if (completeT > 0) {
                complete.matrix.reset()
                complete.matrix.setTranslate(chartMeasureBounds.left, 0f)
                complete.gradient.setLocalMatrix(complete.matrix)
                complete.stroke.strokeWidth = thickness
                complete.stroke.alpha = (0xFF * completeT).toInt()
                canvas.drawCircle(chartBounds.centerX(), chartBounds.centerY(), (chartBounds.width() - thickness) / 2, complete.stroke)
                complete.path.rewind()
                complete.path.moveTo(chartBounds.width() * .348f, chartBounds.height() * .538f)
                complete.path.lineTo(chartBounds.width() * .447f, chartBounds.height() * .636f)
                complete.path.lineTo(chartBounds.width() * .678f, chartBounds.height() * .402f)
                complete.path.offset(chartBounds.left, chartBounds.top)
                complete.stroke.strokeWidth = 10 * density
                canvas.drawPath(complete.path, complete.stroke)
            }
        }
    }
}

private fun drawSector(
    canvas: android.graphics.Canvas,
    sector: CacheChartState.Sector,
    paints: SectorPaints,
    particle: Particle,
    outerRect: RectF,
    innerRect: RectF,
    angleCenter: Float,
    angleSize: Float,
    rounding: Float,
    alpha: Float,
    textAlphaIn: Float,
    density: Float,
) {
    val selected = sector.selectedAnimated.set(if (sector.selected) 1f else 0f)
    val rectF = paints.rect
    rectF.set(outerRect)
    rectF.inset(selected * -9 * density, selected * -9 * density)
    val x = rectF.centerX() + cos(toRad(angleCenter)) * (rectF.width() + innerRect.width()) / 4
    val y = rectF.centerY() + sin(toRad(angleCenter)) * (rectF.width() + innerRect.width()) / 4
    val loading = textAlphaIn
    val textAlpha = textAlphaIn * alpha * sector.textAlphaAnimated.set(sector.textAlpha)
    val particlesAlpha = sector.particlesAlphaAnimated.set(sector.particlesAlpha)
    paints.paint.alpha = (0xFF * alpha).toInt()
    paints.gradientMatrix.reset()
    paints.gradientMatrix.setTranslate(rectF.centerX(), outerRect.centerY())
    (paints.paint.shader as RadialGradient).setLocalMatrix(paints.gradientMatrix)
    val particleAlpha = max(0f, loading / .75f - .75f) * particlesAlpha
    if (angleSize * 2 >= 359f) {
        canvas.saveLayerAlpha(rectF, 0xFF)
        canvas.drawCircle(rectF.centerX(), rectF.centerY(), rectF.width() / 2, paints.uncut)
        canvas.drawRect(rectF, paints.paint)
        drawParticles(canvas, paints, particle, rectF.centerX(), rectF.centerY(), x, y, 0f, 359f, innerRect.width() / 2f, rectF.width() / 2f, textAlpha, particleAlpha, density)
        canvas.drawCircle(innerRect.centerX(), innerRect.centerY(), innerRect.width() / 2, paints.cut)
        canvas.restore()
    } else {
        paints.setupPath(rectF, innerRect, angleCenter, angleSize, rounding)
        canvas.saveLayerAlpha(rectF, 0xFF)
        canvas.drawPath(paints.path, paints.uncut)
        canvas.drawRect(rectF, paints.paint)
        drawParticles(canvas, paints, particle, rectF.centerX(), rectF.centerY(), x, y, angleCenter - angleSize, angleCenter + angleSize, innerRect.width() / 2f, rectF.width() / 2f, textAlpha, particleAlpha, density)
        canvas.restore()
    }
    if (textAlpha <= 0) return
    val textScale = sector.textScaleAnimated.set(sector.textScale)
    canvas.save()
    if (textScale != 1f) canvas.scale(textScale, textScale, x, y)
    sector.text.draw(canvas, paints.textPaint, x, y + paints.textPaint.textSize * .35f, textAlpha)
    canvas.restore()
}

/** Shared by every sector and chart, as Telegram's static field is. */
private var particlesStart = -1L

private fun drawParticles(
    canvas: android.graphics.Canvas,
    paints: SectorPaints,
    particle: Particle,
    cx: Float,
    cy: Float,
    textX: Float,
    textY: Float,
    angleStartIn: Float,
    angleEndIn: Float,
    innerRadius: Float,
    outerRadius: Float,
    textAlpha: Float,
    alpha: Float,
    density: Float,
) {
    if (alpha <= 0) return
    val now = System.currentTimeMillis()
    val sqrt2 = sqrt(2f)
    if (particlesStart < 0) particlesStart = now
    val time = (now - particlesStart) / 10000f
    val sz = particle.size
    val szs = 15 * density / sz
    val stepAngle = 7f
    val angleStart = angleStartIn % 360
    val angleEnd = angleEndIn % 360
    val fromAngle = floor(angleStart / stepAngle).toInt()
    val toAngle = ceil(angleEnd / stepAngle).toInt()
    for (i in fromAngle..toAngle) {
        val angle = i * stepAngle
        val t = (((time + 100) * (1f + (sin(angle * 2000.0) + 1) * .25f)) % 1).toFloat()
        val r = lerp(innerRadius - sz * sqrt2, outerRadius + sz * sqrt2, t)
        val x = cx + r * cos(toRad(angle))
        val y = cy + r * sin(toRad(angle))
        var particleAlpha = .65f * alpha * (-1.75f * abs(t - .5f) + 1) *
            (.25f * (sin(t * Math.PI).toFloat() - 1) + 1) *
            lerp(1f, min(hypot(x - textX, y - textY) / (64 * density), 1f), textAlpha)
        particleAlpha = particleAlpha.coerceIn(0f, 1f)
        paints.particlePaint.alpha = (0xFF * particleAlpha).toInt()
        val s = szs * (.75f * (.25f * (sin(t * Math.PI).toFloat() - 1) + 1) * (.8f + (sin(angle.toDouble()).toFloat() + 1) * .25f))
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(s, s)
        canvas.translate(-sz / 2, -sz / 2)
        canvas.drawPath(particle.path, paints.particlePaint)
        canvas.restore()
    }
}

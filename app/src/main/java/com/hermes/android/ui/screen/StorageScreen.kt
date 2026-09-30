/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 *
 * Modified for Hermes by traveler3022, 2026-09-29: the storage usage page of
 * org.telegram.ui.CacheControlActivity (chart, CacheChartHeader, the section rows of
 * CheckBoxCell, ClearCacheButton, ClearingCacheView) ported to Jetpack Compose. Its sections are
 * the app's own leftovers (StorageCleaner) instead of Telegram's media cache.
 */

package com.hermes.android.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.data.JunkKind
import com.hermes.android.ui.component.CacheChart
import com.hermes.android.ui.component.CacheChartColors
import com.hermes.android.ui.component.CacheChartSegment
import com.hermes.android.ui.component.CacheChartState
import com.hermes.android.ui.component.EaseOutQuint
import com.hermes.android.ui.component.roundPercents
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.icons.filled.DeleteSweep
import com.hermes.android.ui.icons.filled.ExpandMore
import com.hermes.android.ui.viewmodel.StorageEvent
import com.hermes.android.ui.viewmodel.StorageUiState
import com.hermes.android.ui.viewmodel.StorageViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Telegram shows four sections, then folds the rest into "Other" (MAX_NOT_COLLAPSED). */
private const val MAX_NOT_COLLAPSED = 4
private const val OTHER_SLOT = 10

/** One checkbox row; [kind] null is the folded "Other" group. */
private data class SectionRow(
    val kind: JunkKind?,
    val size: Long,
    val percent: Int,
    val pad: Boolean,
    val divider: Boolean,
) {
    val slot: Int get() = kind?.ordinal ?: OTHER_SLOT
}

/** CacheControlActivity.updateRows: biggest first; past five, the smallest fold into "Other". */
private fun sectionRows(state: StorageUiState): Pair<List<SectionRow>, List<JunkKind>> {
    val sections = JunkKind.entries.filter { state.size(it) > 0 }.sortedByDescending { state.size(it) }
    if (sections.isEmpty()) return emptyList<SectionRow>() to emptyList()
    val percents = roundPercents(FloatArray(JunkKind.entries.size) { state.size(JunkKind.entries[it]).toFloat() })
    fun row(kind: JunkKind, pad: Boolean, last: Boolean) =
        SectionRow(kind, state.size(kind), percents[kind.ordinal], pad, divider = !last)
    if (sections.size <= MAX_NOT_COLLAPSED + 1) {
        return sections.mapIndexed { i, kind -> row(kind, pad = false, last = i == sections.lastIndex) } to emptyList()
    }
    val main = sections.take(MAX_NOT_COLLAPSED)
    val others = sections.drop(MAX_NOT_COLLAPSED)
    val rows = main.map { row(it, pad = false, last = false) } +
        SectionRow(null, others.sumOf { state.size(it) }, others.sumOf { percents[it.ordinal] }, pad = false, divider = !state.collapsed) +
        if (state.collapsed) emptyList() else others.mapIndexed { i, kind -> row(kind, pad = true, last = i == others.lastIndex) }
    return rows to others
}

/** AndroidUtilities.formatFileSize, with Latin digits like the rest of the app. */
internal fun formatFileSize(size: Long, removeZero: Boolean = false, makeShort: Boolean = false): String {
    fun f(format: String, value: Float) = String.format(Locale.US, format, value)
    return when {
        size <= 0 -> "0 KB"
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> {
            val value = size / 1024f
            if (removeZero && (value - value.toInt()) * 10 == 0f) "${value.toInt()} KB" else f("%.1f KB", value)
        }
        size < 1000L * 1024 * 1024 -> {
            val value = size / 1024f / 1024f
            if (removeZero && (value - value.toInt()) * 10 == 0f) "${value.toInt()} MB" else f("%.1f MB", value)
        }
        else -> {
            val value = (size / 1024L / 1024L).toInt() / 1000f
            when {
                removeZero && (value - value.toInt()) * 10 == 0f -> "${value.toInt()} GB"
                makeShort -> f("%.1f GB", value)
                else -> f("%.2f GB", value)
            }
        }
    }
}

/** The number and unit in the middle of the chart (CacheChart.setSegments). */
private fun chartSize(size: Long): Pair<String, String> {
    val parts = formatFileSize(size, removeZero = true, makeShort = true).split(" ")
    var top = parts.getOrElse(0) { "" }
    if (top.length >= 4 && size < 1024L * 1024L * 1024L) top = top.split(".")[0]
    return top to parts.getOrElse(1) { "" }
}

/** CacheControlActivity.formatPercent. */
private fun formatPercent(k: Float): String {
    if (k < 0.001f) return "<0.1%"
    val p = (k * 100f).roundToInt()
    return if (p <= 0) "<1%" else "$p%"
}

@Composable
private fun kindTitle(kind: JunkKind): String = when (kind) {
    JunkKind.Photos -> t("Photos", "عکس‌ها")
    JunkKind.Videos -> t("Videos", "ویدیوها")
    JunkKind.Documents -> t("Documents", "اسناد")
    JunkKind.Audio -> t("Audio", "صدا")
    JunkKind.Temp -> t("Temporary files", "فایل‌های موقت")
    JunkKind.Browser -> t("Browser cache", "کش مرورگر")
    JunkKind.Packages -> t("Package cache", "کش بسته‌ها")
    JunkKind.AppCache -> t("App cache", "کش برنامه")
    JunkKind.Other -> t("Miscellaneous", "متفرقه")
    JunkKind.Logs -> t("Logs", "لاگ‌ها")
}

/**
 * Telegram's Storage Usage page for the whole app: what each kind of leftover takes, a checkbox
 * per kind, and one button that clears the chosen ones.
 */
@Composable
fun StorageScreen(
    onNavigateBack: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val chart = remember { CacheChartState() }
    var confirmClear by remember { mutableStateOf(false) }
    var pressedSlot by remember { mutableStateOf(-1) }

    val freed = t("%s freed on your phone!", "%s از فضای گوشی آزاد شد!")
    val busy = t(
        "Hermes is installing or updating. Clear the storage when it's done.",
        "هرمس در حال نصب یا به‌روزرسانی است. بعد از تمام شدنش پاک کنید.",
    )
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StorageEvent.Cleared -> snackbarHostState.showSnackbar(freed.format(formatFileSize(event.bytes)))
                StorageEvent.Busy -> snackbarHostState.showSnackbar(busy)
            }
        }
    }

    val (rows, otherKinds) = remember(state) { sectionRows(state) }
    val otherSelected = otherKinds.isNotEmpty() && otherKinds.all { it in state.selected }
    val junkBytes = state.scan?.junkBytes ?: 0L
    val hasCache = state.calculating || junkBytes > 0

    // updateChart: every section on screen is a slot; the folded group is slot 10.
    LaunchedEffect(rows, state.selected, state.calculating) {
        val segments = MutableList<CacheChartSegment?>(OTHER_SLOT + 1) { null }
        rows.forEach { row ->
            if (row.kind == null) {
                if (state.collapsed) segments[OTHER_SLOT] = CacheChartSegment(row.size, otherSelected)
            } else {
                segments[row.slot] = CacheChartSegment(row.size, row.kind in state.selected)
            }
        }
        chart.setSegments(if (state.calculating) -1 else junkBytes, segments, ::chartSize)
    }

    // The action bar title only shows once the chart's own title has scrolled away.
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 } }

    HermesScaffold(
        title = if (showTitle) t("Storage Usage", "مصرف فضای ذخیره‌سازی") else "",
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item(key = "chart") {
                CacheChart(
                    state = chart,
                    textColor = MaterialTheme.colorScheme.onBackground,
                    secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    loadingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    onPressed = { pressedSlot = it },
                )
            }
            item(key = "header") {
                val scan = state.scan
                ChartHeader(
                    calculating = state.calculating,
                    hasCache = hasCache,
                    // Telegram's bar: its own share of the device, then the rest in use.
                    percent = scan?.takeIf { it.deviceTotal > 0 }?.let {
                        (if (it.appBytes >= 0) it.appBytes else it.junkBytes).toFloat() / it.deviceTotal
                    },
                    usedPercent = scan?.takeIf { it.deviceTotal > 0 && it.deviceFree >= 0 }?.let {
                        (it.deviceTotal - it.deviceFree).toFloat() / it.deviceTotal
                    },
                )
            }
            if (state.calculating) {
                items(5) { LoadingSectionRow() }
            } else {
                items(rows, key = { it.kind?.name ?: "other" }) { row ->
                    val haptics = LocalHapticFeedback.current
                    val shake = remember { Animatable(0f) }
                    val scope = rememberCoroutineScope()
                    fun refuse() {
                        // Telegram's shakeViewSpring(cell, -3) with an error buzz: one section has to stay.
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        scope.launch {
                            shake.snapTo(-3f)
                            shake.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = Spring.StiffnessHigh))
                        }
                    }
                    fun toggle(kinds: Set<JunkKind>, selected: Boolean) {
                        if (selected && !viewModel.canDeselect(kinds)) refuse() else viewModel.setSelected(kinds, !selected)
                    }
                    val kinds = row.kind?.let { setOf(it) } ?: otherKinds.toSet()
                    val selected = if (row.kind == null) otherSelected else row.kind in state.selected
                    SectionRowItem(
                        title = row.kind?.let { kindTitle(it) } ?: t("Other", "سایر"),
                        percent = row.percent,
                        size = row.size,
                        color = CacheChartColors[row.slot],
                        checked = selected,
                        pad = row.pad,
                        divider = row.divider,
                        collapsed = if (row.kind == null) state.collapsed else null,
                        highlighted = pressedSlot == row.slot,
                        shakeDp = shake.value,
                        onClick = {
                            if (row.kind == null) viewModel.toggleCollapsed() else toggle(kinds, selected)
                        },
                        onCheckClick = { toggle(kinds, selected) },
                    )
                }
            }
            if (hasCache) {
                item(key = "clear") {
                    ClearCacheButton(
                        allSelected = JunkKind.entries.all { it in state.selected },
                        size = if (state.calculating) 0 else state.selectedBytes,
                        onClick = { confirmClear = true },
                    )
                }
                item(key = "info") { InfoText(storageInfo()) }
            }
        }
    }

    if (confirmClear) {
        val buttonText = if (JunkKind.entries.all { it in state.selected }) t("Clear Cache", "پاک کردن همه") else t("Clear Selected", "پاک کردن انتخاب‌شده‌ها")
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(t("Clear Cache", "پاک کردن") + " (" + formatFileSize(state.selectedBytes) + ")") },
            text = { Text(storageInfo()) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clear()
                }) { Text(buttonText, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(t("Cancel", "انصراف")) }
            },
        )
    }

    state.clearing?.let { progress -> ClearingSheet(progress) }
}

@Composable
private fun storageInfo(): String = t(
    "Only files that come back on their own are cleared: caches, temporary files and logs. " +
        "Chats, memories, keys, installed tools and your own files stay. Files used in the last hour are kept.",
    "فقط چیزهایی پاک می‌شوند که خودشان دوباره ساخته می‌شوند: کش‌ها، فایل‌های موقت و لاگ‌ها. " +
        "گفتگوها، حافظه، کلیدها، ابزارهای نصب‌شده و فایل‌های خودتان دست نمی‌خورند. فایل‌هایی که در یک ساعت اخیر استفاده شده‌اند می‌مانند.",
)

/**
 * CacheChartHeader: the title, one of three subtitles, and a bar of the device — this app's
 * share, the rest in use, then free space. The bar hides once nothing is left to clear.
 */
@Composable
private fun ChartHeader(calculating: Boolean, hasCache: Boolean, percent: Float?, usedPercent: Float?) {
    val cleared = !calculating && !hasCache
    val subtitle = when {
        calculating -> t("Calculating the size of leftover files…", "در حال محاسبهٔ حجم فایل‌های اضافه…")
        cleared -> t(
            "Caches come back on their own when Hermes needs them again.",
            "کش‌ها هر وقت هرمس لازمشان داشته باشد خودشان دوباره ساخته می‌شوند.",
        )
        else -> t("Hermes uses %s of your phone's storage.", "هرمس %s از فضای گوشی را گرفته است.").format(formatPercent(percent ?: 0f))
    }
    val barAlpha by animateFloatAsState(if (cleared) 0f else 1f, tween(340, easing = EaseOutQuint), label = "barAlpha")
    val loading by animateFloatAsState(if (calculating || percent == null) 1f else 0f, tween(450, easing = EaseOutQuint), label = "loading")
    val percentA by animateFloatAsState(percent ?: 0f, tween(450, easing = EaseOutQuint), label = "percent")
    val usedA by animateFloatAsState(usedPercent ?: 0f, tween(450, easing = EaseOutQuint), label = "used")
    val accent = MaterialTheme.colorScheme.primary
    val selector = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val usedColor = lerp(accent, selector, .75f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (cleared) t("Storage Cleared", "فضا آزاد شد") else t("Storage Usage", "مصرف فضای ذخیره‌سازی"),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.height(26.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(4.dp)
                .alpha(barAlpha),
        ) {
            val w = size.width.coerceAtMost(174.dp.toPx())
            val left = (size.width - w) / 2
            val right = left + w
            val min4 = 4.dp.toPx()
            val gap = 1.dp.toPx()
            fun roundRect(l: Float, r: Float, leftRadius: Float, rightRadius: Float, color: Color) {
                if (r - l <= 0) return
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = l, top = 0f, right = r, bottom = size.height,
                            topLeftCornerRadius = CornerRadius(leftRadius), bottomLeftCornerRadius = CornerRadius(leftRadius),
                            topRightCornerRadius = CornerRadius(rightRadius), bottomRightCornerRadius = CornerRadius(rightRadius),
                        ),
                    )
                }
                drawPath(path, color)
            }
            val appEnd = left + (1f - loading) * max(min4, percentA * w)
            val usedEnd = left + (1f - loading) * max(min4, usedA * w)
            // Free space (and the whole bar while loading).
            val restStart = max(usedEnd, appEnd) + gap
            if (right - restStart > 3.dp.toPx()) {
                roundRect(restStart, right, lerp(1f, 2f, loading).dp.toPx(), 2.dp.toPx(), selector)
            }
            // The rest of the phone in use.
            if (usedEnd - (appEnd + gap) > 3.dp.toPx()) {
                roundRect(appEnd + gap, usedEnd, 1.dp.toPx(), (if (usedA > .97f) 2 else 1).dp.toPx(), usedColor)
            }
            // This app.
            roundRect(left, appEnd, 2.dp.toPx(), (if (percentA > .97f) 2 else 1).dp.toPx(), accent)
        }
        Spacer(Modifier.height(26.dp))
    }
}

private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

/** CheckBoxCell, TYPE_CHECK_BOX_ROUND with padding 21: a coloured round checkbox, the title with its share, the size. */
@Composable
private fun SectionRowItem(
    title: String,
    percent: Int,
    size: Long,
    color: Color,
    checked: Boolean,
    pad: Boolean,
    divider: Boolean,
    collapsed: Boolean?,
    highlighted: Boolean,
    shakeDp: Float,
    onClick: () -> Unit,
    onCheckClick: () -> Unit,
) {
    val percentText = if (percent <= 0) String.format(Locale.US, "<%.1f%%", 1f) else "$percent%"
    val highlight by animateFloatAsState(if (highlighted) 1f else 0f, tween(200), label = "highlight")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f * highlight))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 21.dp)
                .offset { IntOffset(shakeDp.dp.roundToPx(), 0) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pad) Spacer(Modifier.width(40.dp))
            RoundCheckBox(
                checked = checked,
                color = color,
                modifier = Modifier
                    .size(21.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCheckClick),
            )
            Spacer(Modifier.width(18.dp))
            Text(
                text = buildAnnotatedString {
                    append(title)
                    append("  ")
                    withStyle(SpanStyle(fontSize = (16 * 0.834).sp, fontWeight = FontWeight.Bold)) { append(percentText) }
                },
                fontSize = 16.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (collapsed != null) {
                val rotation by animateFloatAsState(if (collapsed) 0f else 180f, tween(340, easing = EaseOutQuint), label = "arrow")
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = formatFileSize(size),
                fontSize = 16.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (divider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (pad) 100.dp else 60.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

/** CheckBox2 at 21dp, drawn unchecked as a ring: fills with the section colour and ticks when checked. */
@Composable
private fun RoundCheckBox(checked: Boolean, color: Color, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(200), label = "check")
    val ring = MaterialTheme.colorScheme.onSurfaceVariant
    val check = Color.White
    Canvas(modifier) {
        val r = size.minDimension / 2
        val stroke = 2.dp.toPx()
        drawCircle(lerp(ring, color, progress), radius = r - stroke / 2, style = Stroke(stroke))
        if (progress > 0) {
            drawCircle(color, radius = (r - stroke / 2) * progress)
            val cx = center.x
            val cy = center.y
            val tick = Path().apply {
                moveTo(cx - 4.5.dp.toPx(), cy + 0.5.dp.toPx())
                lineTo(cx - 1.5.dp.toPx(), cy + 3.5.dp.toPx())
                lineTo(cx + 4.5.dp.toPx(), cy - 3.dp.toPx())
            }
            drawPath(
                tick,
                check.copy(alpha = progress),
                style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** FlickerLoadingView.CHECKBOX_TYPE while the sizes are counted. */
@Composable
private fun LoadingSectionRow() {
    val pulse by rememberInfiniteTransition(label = "flicker").animateFloat(
        initialValue = 0.05f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "flickerAlpha",
    )
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = pulse)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 21.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(21.dp).background(color, CircleShape))
        Spacer(Modifier.width(18.dp))
        Box(Modifier.width(120.dp).height(10.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(48.dp).height(10.dp).background(color, RoundedCornerShape(4.dp)))
    }
}

/** ClearCacheButton: a 48dp pill, the action and the size it frees, dimmed when there is nothing. */
@Composable
private fun ClearCacheButton(allSelected: Boolean, size: Long, onClick: () -> Unit) {
    val disabled = size <= 0
    val alpha by animateFloatAsState(if (disabled) .65f else 1f, label = "clearAlpha")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "clearScale")
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(48.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = !disabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (allSelected) t("Clear Cache", "پاک کردن همه") else t("Clear Selected", "پاک کردن انتخاب‌شده‌ها"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = onAccent,
            )
            if (!disabled) {
                Text(
                    text = formatFileSize(size),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = onAccent.copy(alpha = .7f),
                )
            }
        }
    }
}

/** TextInfoPrivacyCell. */
@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 21.dp, end = 21.dp, top = 10.dp, bottom = 17.dp),
    )
}

/**
 * ClearingCacheView in its bottom sheet: can't be dismissed, shows the percentage and a bar until
 * the clearing is done.
 */
@Composable
private fun ClearingSheet(progress: Float) {
    val animated by animateFloatAsState(progress, tween(350, easing = androidx.compose.animation.core.EaseOut), label = "clearing")
    val track = MaterialTheme.colorScheme.primary
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().height(350.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    // Telegram plays its Utya animation here.
                    val wobble by rememberInfiniteTransition(label = "broom").animateFloat(
                        initialValue = -8f,
                        targetValue = 8f,
                        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                        label = "broomAngle",
                    )
                    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = track,
                            modifier = Modifier.size(72.dp).rotate(wobble),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${kotlin.math.ceil(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.height(32.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Canvas(Modifier.width(240.dp).height(5.dp)) {
                        val radius = CornerRadius(3.dp.toPx())
                        drawRoundRect(track.copy(alpha = .2f), cornerRadius = radius)
                        drawRoundRect(track, size = Size(size.width * animated, size.height), cornerRadius = radius, topLeft = Offset.Zero)
                    }
                    Spacer(Modifier.height(30.dp))
                    Text(
                        text = t("Clearing cache…", "در حال پاک کردن…"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = t(
                            "Please keep this window open while Hermes is clearing your storage.",
                            "تا تمام شدن پاک‌سازی این صفحه را باز نگه دارید.",
                        ),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(240.dp),
                    )
                }
            }
        }
    }
}

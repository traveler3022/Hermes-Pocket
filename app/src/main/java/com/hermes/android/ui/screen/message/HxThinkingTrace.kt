package com.hermes.android.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import kotlinx.coroutines.delay

// ── Metrics ──────────────────────────────────────────────────────────────────
// The rail is a fixed-width gutter so every row's text starts on the same
// vertical line whether its glyph is a dot or an icon.
private val GlyphColumnWidth = 22.dp
private val GlyphIconSize = 18.dp
private val GlyphDotSize = 8.dp
private val ConnectorWidth = 2.dp

/** Gap between a glyph's bottom edge and the start of the connector below it. */
private val ConnectorTopGap = 9.dp

/** Horizontal gap between the rail and the row's text column. */
private val RailTextGap = 14.dp

/** Vertical breathing room under every row except the last one. */
private val RowBottomGap = 22.dp

private const val ConnectorAlpha = 0.12f

// ── Shimmer ──────────────────────────────────────────────────────────────────
private const val ShimmerTravelMillis = 1800
private const val ShimmerPauseMillis = 1000
private const val ShimmerHalfWidth = 180f

// ── Typewriter ───────────────────────────────────────────────────────────────
// Three characters per frame is fast enough to keep up with a streaming model
// but slow enough that the reveal reads as motion rather than a repaint.
private const val TypewriterCharsPerStep = 3
private const val TypewriterStepMillis = 18L

/** Only scan the tail of the reasoning: it grows by hundreds of tokens per
 *  turn and this re-runs on every buffered flush, so scanning the whole
 *  string would be O(n²) across a turn — enough to visibly stutter on a
 *  phone during a long thinking phase. */
private const val PreviewScanTail = 400

/** Raw reasoning past this length is truncated — a `Text` holding a whole
 *  turn's unstructured reasoning janks the sheet's scroll. */
private const val RawReasoningCap = 12_000

/** One step of the model's reasoning. [title] is blank for text that arrived
 *  before the model emitted any heading. */
internal data class HxReasoningStep(
    val title: String,
    val detail: String,
)

private val MarkdownHeadingRegex = Regex("""^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$""")
private val BoldHeadingRegex = Regex("""^\s*\*\*(.+?)\*\*\s*:?\s*$""")

/**
 * Splits raw reasoning text into timeline steps.
 *
 * Reasoning models usually mark their own sections with a markdown heading
 * (`## Checking the config`) or a bold lead line (`**Checking the config**`).
 * Everything up to the next such line becomes that step's detail. Text that
 * arrives before the first heading is kept as a leading step with no title,
 * so nothing the model said is dropped.
 */
internal fun parseReasoningSteps(raw: String): List<HxReasoningStep> {
    if (raw.isBlank()) return emptyList()

    val steps = mutableListOf<HxReasoningStep>()
    var title = ""
    val detail = StringBuilder()

    fun flush() {
        val body = detail.toString().trim()
        if (title.isNotBlank() || body.isNotBlank()) {
            steps += HxReasoningStep(title = title, detail = body)
        }
        title = ""
        detail.setLength(0)
    }

    raw.lineSequence().forEach { line ->
        val heading = MarkdownHeadingRegex.find(line)?.groupValues?.get(1)
            ?: BoldHeadingRegex.find(line)?.groupValues?.get(1)
        if (heading != null) {
            flush()
            title = heading.trim()
        } else {
            detail.appendLine(line)
        }
    }
    flush()

    return steps
}

/** A timeline is only worth showing when the model actually structured its
 *  reasoning. One untitled blob is just raw text with extra chrome. */
internal fun List<HxReasoningStep>.worthATimeline(): Boolean =
    count { it.title.isNotBlank() } >= 2

/**
 * The agent's reasoning, as a single quiet line in the message flow.
 *
 * Collapsed it is only text — no card, no border, no chevron: while the model
 * is thinking the line shimmers or types itself out, and once it is done the
 * line states how long it took. Tapping anywhere on it opens the full trace in
 * a bottom sheet, so a long reasoning dump never pushes the reply off screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HxThinkingTrace(
    reasoning: String,
    isStreaming: Boolean,
    messageId: String,
    modifier: Modifier = Modifier,
) {
    var sheetVisible by remember(messageId) { mutableStateOf(false) }

    // Thinking duration is measured, never guessed: a message restored from
    // history was never streamed in this process, so both stamps stay 0 and
    // the line falls back to a plain label instead of inventing a number.
    var startedAt by rememberSaveable(messageId) { mutableLongStateOf(0L) }
    var finishedAt by rememberSaveable(messageId) { mutableLongStateOf(0L) }
    LaunchedEffect(messageId, isStreaming) {
        val now = System.currentTimeMillis()
        if (isStreaming) {
            if (startedAt == 0L) startedAt = now
        } else if (startedAt != 0L && finishedAt == 0L) {
            finishedAt = now
        }
    }

    val preview = remember(reasoning) {
        reasoning.takeLast(PreviewScanTail)
            .lineSequence()
            .lastOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
    }

    val elapsedSeconds = remember(startedAt, finishedAt) {
        if (startedAt == 0L || finishedAt == 0L) null
        else ((finishedAt - startedAt) / 1000L).coerceAtLeast(1L)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
            .noRippleClickable { sheetVisible = true },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            !isStreaming -> Text(
                text = elapsedSeconds
                    ?.let { t("Thought for ${it}s", "$it ثانیه فکر کرد") }
                    ?: t("Thoughts", "افکار"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            preview.isNotEmpty() -> HxTypewriterText(text = preview)

            else -> HxShimmerText(text = t("Thinking", "در حال فکر کردن"))
        }
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = { HxSheetGrabber() },
        ) {
            HxThinkingSheetContent(
                reasoning = reasoning,
                isComplete = !isStreaming,
                elapsedSeconds = elapsedSeconds,
            )
        }
    }
}

@Composable
private fun HxSheetGrabber() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 8.dp)
            .width(56.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)),
    )
}

@Composable
private fun HxThinkingSheetContent(
    reasoning: String,
    isComplete: Boolean,
    elapsedSeconds: Long?,
) {
    val steps = remember(reasoning) { parseReasoningSteps(reasoning) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (steps.worthATimeline()) {
            HxReasoningTimeline(
                steps = steps,
                isComplete = isComplete,
                elapsedSeconds = elapsedSeconds,
            )
        } else {
            HxRawReasoningPanel(rawText = reasoning)
        }
    }
}

@Composable
private fun HxReasoningTimeline(
    steps: List<HxReasoningStep>,
    isComplete: Boolean,
    elapsedSeconds: Long?,
) {
    Column {
        steps.forEachIndexed { index, step ->
            // While the model is still thinking the final row is genuinely the
            // end of the rail; once it is done, the check row below owns that
            // position and every step keeps its connector.
            HxTimelineRow(
                title = step.title,
                detail = step.detail,
                isLast = !isComplete && index == steps.lastIndex,
            )
        }
        if (isComplete) {
            HxTimelineRow(
                title = elapsedSeconds
                    ?.let { t("Thought for ${it}s", "$it ثانیه فکر کرد") }
                    ?: t("Thoughts", "افکار"),
                detail = t("Done", "تمام"),
                isLast = true,
                icon = Icons.Rounded.CheckCircle,
            )
        }
    }
}

@Composable
private fun HxTimelineRow(
    title: String,
    detail: String,
    isLast: Boolean,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // IntrinsicSize.Min lets the connector stretch to exactly the
            // height of this row's text — the rail stays unbroken no matter
            // how long a step's detail runs.
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(RailTextGap),
    ) {
        HxTimelineGlyph(icon = icon, isLast = isLast)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else RowBottomGap),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HxTimelineGlyph(
    icon: ImageVector?,
    isLast: Boolean,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .width(GlyphColumnWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (!isLast) {
            Box(
                modifier = Modifier
                    .padding(top = GlyphIconSize + ConnectorTopGap)
                    .width(ConnectorWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(muted.copy(alpha = ConnectorAlpha)),
            )
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = muted,
                modifier = Modifier.size(GlyphIconSize),
            )
        } else {
            Box(
                modifier = Modifier
                    // Nudged down so a dot sits on the same optical line as
                    // the cap height of the title beside it.
                    .padding(top = 5.dp)
                    .size(GlyphDotSize)
                    .clip(CircleShape)
                    .background(muted),
            )
        }
    }
}

@Composable
private fun HxRawReasoningPanel(rawText: String) {
    val waiting = t("Waiting for reasoning…", "در انتظار استدلال…")
    val displayText = remember(rawText, waiting) {
        val text = rawText.ifBlank { waiting }
        if (text.length <= RawReasoningCap) text
        else text.take(RawReasoningCap).trimEnd() + "\n…"
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = t("Raw reasoning", "استدلال خام"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = displayText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The status line, revealed a few characters at a time.
 *
 * The model rewrites this line as it thinks, and each rewrite usually extends
 * the previous one — so the reveal continues from where it was rather than
 * restarting. When the new text is *not* an extension the model changed its
 * mind, and the line retypes from scratch.
 */
@Composable
private fun HxTypewriterText(text: String) {
    var rendered by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        if (text.isBlank()) {
            rendered = ""
            return@LaunchedEffect
        }
        if (!text.startsWith(rendered)) rendered = ""
        while (rendered.length < text.length) {
            rendered = text.substring(
                0,
                (rendered.length + TypewriterCharsPerStep).coerceAtMost(text.length),
            )
            delay(TypewriterStepMillis)
        }
    }
    Text(
        text = rendered.ifBlank { text },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Text with a highlight sweeping across it, then a beat of stillness before
 * the next pass — the "the agent is working" signal when there is no status
 * text to show yet.
 */
@Composable
private fun HxShimmerText(text: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val bright = MaterialTheme.colorScheme.onSurface

    if (rememberReduceMotion()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        return
    }

    // Longer labels need a longer sweep or the highlight never reaches their
    // end; the clamp keeps a very long one from crawling.
    val travel = (280f + text.length * 18f).coerceIn(280f, 760f)
    val total = ShimmerTravelMillis + ShimmerPauseMillis
    val offset by rememberInfiniteTransition(label = "thinking_shimmer")
        .animateFloat(
            initialValue = -travel,
            targetValue = travel,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = total
                    travel at ShimmerTravelMillis using LinearEasing
                    // Hold at the far edge for the pause, so the sweep reads
                    // as a heartbeat instead of a strobe.
                    travel at total
                },
            ),
            label = "thinking_shimmer_offset",
        )

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    muted.copy(alpha = 0.42f),
                    bright.copy(alpha = 0.96f),
                    muted.copy(alpha = 0.42f),
                ),
                start = Offset(offset - ShimmerHalfWidth, 0f),
                end = Offset(offset + ShimmerHalfWidth, 0f),
            ),
        ),
    )
}

/** A tap target with no ripple — the trace line is body text, and a ripple
 *  washing over a paragraph reads as a bug rather than a control. */
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

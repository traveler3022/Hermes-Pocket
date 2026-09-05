package com.hermes.android.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage
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

// ── Status line ──────────────────────────────────────────────────────────────
// The preview line is recomputed on every buffered streaming flush — many
// times a second. Repainting the line that often is what makes small text
// strobe, so the line is *sampled* on a slow fixed cadence instead of
// following the stream, and each new value crossfades in.
private const val StatusSampleMillis = 450L
private const val StatusFadeMillis = 260

/** Only scan the tail of the reasoning: it grows by hundreds of tokens per
 *  turn and this re-runs on every buffered flush, so scanning the whole
 *  string would be O(n²) across a turn — enough to visibly stutter on a
 *  phone during a long thinking phase. */
private const val PreviewScanTail = 400

/** Raw reasoning past this length is truncated — a `Text` holding a whole
 *  turn's unstructured reasoning janks the sheet's scroll. */
private const val RawReasoningCap = 12_000

/** The sheet takes this share of the screen at most, so it opens to the same
 *  proportion on a small phone and a tall one. A fixed dp cap could not: 640dp
 *  is four fifths of one screen and the whole of another. */
private const val SheetScreenFraction = 0.6f

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
    tools: List<ChatMessage.ToolCall> = emptyList(),
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
        val runningTool = tools.lastOrNull { it.isRunning }
        when {
            // The tool cards live in the sheet now, so the line has to say
            // what is running — otherwise a long tool looks like a hang.
            runningTool != null -> HxShimmerText(
                text = t(
                    "Using ${runningTool.toolName}…",
                    "در حال استفاده از ${runningTool.toolName}…",
                ),
            )

            !isStreaming -> Text(
                text = doneLabel(elapsedSeconds, tools.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            preview.isNotEmpty() -> HxStatusLine(text = preview)

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
                tools = tools,
                isComplete = !isStreaming,
                elapsedSeconds = elapsedSeconds,
            )
        }
    }
}

/** What the collapsed line reads once the turn is over: how long it thought,
 *  and how many tools it reached for on the way. */
@Composable
private fun doneLabel(elapsedSeconds: Long?, toolCount: Int): String {
    val thought = elapsedSeconds
        ?.let { t("Thought for ${it}s", "$it ثانیه فکر کرد") }
        ?: t("Thoughts", "افکار")
    if (toolCount == 0) return thought
    val tools = if (toolCount == 1) {
        t("1 tool", "۱ ابزار")
    } else {
        t("$toolCount tools", "$toolCount ابزار")
    }
    return "$thought · $tools"
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
    tools: List<ChatMessage.ToolCall>,
    isComplete: Boolean,
    elapsedSeconds: Long?,
) {
    val steps = remember(reasoning) { parseReasoningSteps(reasoning) }
    val structured = steps.worthATimeline()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * SheetScreenFraction).dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (structured || tools.isNotEmpty()) {
            HxReasoningTimeline(
                // When the model never labelled its own reasoning there are no
                // steps worth rendering as rows — the tools still are, and the
                // raw text follows below rather than being thrown away.
                steps = if (structured) steps else emptyList(),
                tools = tools,
                isComplete = isComplete,
                elapsedSeconds = elapsedSeconds,
            )
        }
        if (!structured) {
            HxRawReasoningPanel(rawText = reasoning)
        }
    }
}

@Composable
private fun HxReasoningTimeline(
    steps: List<HxReasoningStep>,
    tools: List<ChatMessage.ToolCall>,
    isComplete: Boolean,
    elapsedSeconds: Long?,
) {
    // The gateway streams reasoning as one growing string and tool calls as
    // separate events, with no shared ordering key — so steps and tools cannot
    // be truly interleaved the way they were emitted. Steps come first, then
    // the tools in the order they ran.
    val lastRowIsSteps = tools.isEmpty()
    Column {
        steps.forEachIndexed { index, step ->
            HxTimelineRow(
                title = step.title,
                detail = step.detail,
                isLast = !isComplete && lastRowIsSteps && index == steps.lastIndex,
            )
        }
        tools.forEachIndexed { index, tool ->
            HxTimelineToolRow(
                tool = tool,
                isLast = !isComplete && index == tools.lastIndex,
            )
        }
        if (isComplete) {
            HxTimelineRow(
                title = doneLabel(elapsedSeconds, tools.size),
                detail = t("Done", "تمام"),
                isLast = true,
                icon = Icons.Rounded.CheckCircle,
            )
        }
    }
}

/** A tool the turn ran, sitting on the same rail as the reasoning steps. The
 *  row's body is the ordinary tool card, so its arguments and result stay
 *  expandable exactly as they were in the message flow. */
@Composable
private fun HxTimelineToolRow(
    tool: ChatMessage.ToolCall,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(RailTextGap),
    ) {
        HxTimelineGlyph(icon = toolGlyph(tool.toolName), isLast = isLast)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else RowBottomGap),
        ) {
            ToolCallCard(message = tool)
        }
    }
}

private fun toolGlyph(toolName: String): ImageVector = when (toolName.lowercase()) {
    "bash", "shell", "terminal", "run_command" -> Icons.Rounded.Terminal
    "fetch", "fetch_web_url", "web_search", "browse" -> Icons.Rounded.Language
    else -> Icons.Rounded.Build
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
 * The one-line "what the agent is chewing on" preview.
 *
 * The underlying text changes many times a second while the model streams, so
 * it is sampled on a fixed cadence rather than followed: the line updates a
 * couple of times a second at most, and each new value crossfades in. Held to
 * a single line so a long reasoning sentence can never reflow the message and
 * shove the rest of the conversation around.
 */
@Composable
private fun HxStatusLine(text: String) {
    val reduceMotion = rememberReduceMotion()
    val latest by rememberUpdatedState(text)
    var shown by remember { mutableStateOf(text) }
    LaunchedEffect(Unit) {
        while (true) {
            if (latest != shown) shown = latest
            delay(StatusSampleMillis)
        }
    }

    if (reduceMotion) {
        HxStatusText(shown)
        return
    }
    Crossfade(
        targetState = shown,
        animationSpec = tween(StatusFadeMillis),
        label = "thinking_status",
    ) { line -> HxStatusText(line) }
}

@Composable
private fun HxStatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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

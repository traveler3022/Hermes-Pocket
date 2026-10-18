package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringArrayResource
import com.hermes.android.ui.design.HxStatus
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.ui.design.HxIcons
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

// ── Activity line ────────────────────────────────────────────────────────────
// The line says what KIND of work is happening (thinking, web, a command, a
// file), never the reasoning itself — that lives in the sheet. An agent can
// fire a dozen tools in a few seconds, so the shown activity only follows the
// real one under three rules, or the glyph would flicker:

/** A shown activity stays at least this long before the next may replace it. */
private const val ActivityDwellMillis = 800L

/** A tool that finishes sooner than this is never shown at all. */
private const val ActivityMinToolMillis = 250L

/** Thinking between two tools has to last this long before the star returns,
 *  so a quick think between commands does not ping-pong the glyph. */
private const val ActivityThinkReturnMillis = 1000L

/** How often the shown activity is reconciled with the real one. */
private const val ActivitySampleMillis = 100L

/** Raw reasoning past this length is truncated — a `Text` holding a whole
 *  turn's unstructured reasoning janks the sheet's scroll. */
private const val RawReasoningCap = 12_000

/** The sheet takes this share of the screen at most, so it opens to the same
 *  proportion on a small phone and a tall one. A fixed dp cap could not: 640dp
 *  is four fifths of one screen and the whole of another. */
private const val SheetScreenFraction = 0.6f

/**
 * One thing the agent did on the way to its answer, in the order it happened.
 *
 * A turn is not one message: the gateway opens a new assistant message for each
 * stretch of narration between tool calls, so a single question can produce a
 * dozen of them. Collected in list order, they and the tool calls between them
 * finally give the trace a real sequence — which is what the plan called out as
 * missing, because reasoning and tools carry no shared ordering key of their
 * own. List order is that key.
 */
internal sealed interface HxTraceItem {
    /** Something the agent said out loud on the way — not its private
     *  reasoning, but not the answer either. */
    data class Note(val text: String) : HxTraceItem

    /** A block of the model's private reasoning, and the message it streamed
     *  into — tools started during that stream point back at it. */
    data class Reasoning(val text: String, val messageId: String = "") : HxTraceItem

    data class Tool(val call: ChatMessage.ToolCall) : HxTraceItem
}

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
 * Attributes each turn's work to the message whose trace will carry it.
 *
 * The returned map does double duty: its values are what a trace shows, and its
 * keys are which assistant messages stay on the chat surface. A message with no
 * work still gets an entry, because being absent from the map means "folded
 * away", not "has nothing to show".
 *
 * With [foldNarration] on, a turn — the run of messages between two questions —
 * collapses onto the assistant message that ends it, and everything said before
 * that becomes a note in its trace. With it off, every assistant message keeps
 * its place and carries only its own reasoning and the tools that ran after it.
 * Tool calls fold either way: putting them back in the flow is what the chat
 * looked like before, and it is not what this switch is for.
 */
internal fun buildTurnWork(
    messages: List<ChatMessage>,
    foldNarration: Boolean,
): Map<String, List<HxTraceItem>> {
    val byMessage = mutableMapOf<String, List<HxTraceItem>>()

    if (!foldNarration) {
        var owner: String? = null
        val work = mutableListOf<HxTraceItem>()
        fun commit() {
            owner?.let { byMessage[it] = work.toList() }
            work.clear()
        }
        for (msg in messages) {
            when (msg) {
                is ChatMessage.User -> {
                    commit()
                    owner = null
                }

                is ChatMessage.Assistant -> {
                    commit()
                    owner = msg.id
                    msg.reasoning?.takeIf { it.isNotBlank() }
                        ?.let { work.add(HxTraceItem.Reasoning(it, msg.id)) }
                    byMessage[msg.id] = work.toList()
                }

                is ChatMessage.ToolCall -> {
                    work.add(HxTraceItem.Tool(msg))
                    commitTo(byMessage, owner, work)
                }

                else -> Unit
            }
        }
        commit()
        return byMessage
    }

    var span = mutableListOf<ChatMessage>()
    fun closeTurn() {
        val ending = span.filterIsInstance<ChatMessage.Assistant>().lastOrNull()
        if (ending != null) {
            val items = mutableListOf<HxTraceItem>()
            for (msg in span) {
                when (msg) {
                    is ChatMessage.Assistant -> {
                        msg.reasoning?.takeIf { it.isNotBlank() }
                            ?.let { items.add(HxTraceItem.Reasoning(it, msg.id)) }
                        // The turn's last word is the answer and stays on the
                        // chat surface; everything it said before that was work.
                        if (msg.id != ending.id) {
                            msg.text.takeIf { it.isNotBlank() }
                                ?.let { items.add(HxTraceItem.Note(it)) }
                        }
                    }

                    is ChatMessage.ToolCall -> items.add(HxTraceItem.Tool(msg))
                    else -> Unit
                }
            }
            byMessage[ending.id] = items
        }
        span = mutableListOf()
    }
    for (msg in messages) {
        if (msg is ChatMessage.User) closeTurn() else span.add(msg)
    }
    closeTurn()
    return byMessage
}

private fun commitTo(
    target: MutableMap<String, List<HxTraceItem>>,
    owner: String?,
    work: List<HxTraceItem>,
) {
    owner?.let { target[it] = work.toList() }
}

/**
 * [buildTurnWork] that hands back the previous list for every trace whose
 * content did not change. The rebuild runs on every streaming flush, and a
 * fresh-but-equal list still counts as a new parameter, so every visible
 * bubble recomposed each time — the cost that grew with the chat's length.
 */
internal class TurnWorkCache {
    private var last: Map<String, List<HxTraceItem>> = emptyMap()

    fun build(messages: List<ChatMessage>, foldNarration: Boolean): Map<String, List<HxTraceItem>> {
        val previous = last
        return buildTurnWork(messages, foldNarration)
            .mapValues { (id, items) -> previous[id]?.takeIf { it == items } ?: items }
            .also { last = it }
    }
}

/**
 * What stays on the chat surface once [turnWork] has folded a turn's work away:
 * an assistant message only when it owns a trace, a tool card only when no
 * trace carries it. A turn with no assistant message at all — cut off mid-tool,
 * or ended on one — has nowhere to fold into, and hiding every tool card
 * regardless left the question standing alone with no sign anything ran.
 */
internal fun visibleChatMessages(
    messages: List<ChatMessage>,
    turnWork: Map<String, List<HxTraceItem>>,
    searching: Boolean,
): List<ChatMessage> {
    if (searching) return messages
    val foldedTools = turnWork.values.flatMapTo(HashSet()) { items ->
        items.mapNotNull { (it as? HxTraceItem.Tool)?.call?.id }
    }
    return messages.filter { msg ->
        when (msg) {
            is ChatMessage.ToolCall -> msg.id !in foldedTools
            is ChatMessage.Assistant -> msg.id in turnWork
            else -> true
        }
    }
}

/**
 * The agent's reasoning, as a single quiet line in the message flow.
 *
 * Collapsed it is one line with no card or border: a breathing glyph and a
 * shimmering label saying what KIND of work is under way — thinking, the web,
 * a command, a file — never the reasoning itself, which is half-sentences,
 * often in another language, and lives in the sheet. Once the turn is done
 * the glyph stills, the line states how long it took, and a chevron marks it
 * as the way in. Tapping anywhere on it opens the full trace in a bottom
 * sheet, so a long reasoning dump never pushes the reply off screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HxThinkingTrace(
    items: List<HxTraceItem>,
    isStreaming: Boolean,
    messageId: String,
    modifier: Modifier = Modifier,
    /** What the agent says it is doing (thinking.delta); blank when it says nothing. */
    status: String = "",
    /** Settings > "Hermes kaomoji": the agent's own face and verb in place of the star. */
    kaomoji: Boolean = false,
) {
    if (items.isEmpty()) return
    val tools = remember(items) { items.filterIsInstance<HxTraceItem.Tool>().map { it.call } }
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

    val elapsedSeconds = remember(startedAt, finishedAt) {
        if (startedAt == 0L || finishedAt == 0L) null
        else ((finishedAt - startedAt) / 1000L).coerceAtLeast(1L)
    }

    // The agent's kaomoji status ("(◕‿◕) pondering...") picks one face and one
    // verb per turn — Hermes sends a fresh random pair on every API call, and
    // following each one would make the line flicker.
    var turnFace by rememberSaveable(messageId) { mutableStateOf<String?>(null) }
    var turnVerb by rememberSaveable(messageId) { mutableStateOf<String?>(null) }
    val kawaii = remember(status) { parseKawaiiStatus(status) }
    LaunchedEffect(kawaii) {
        if (kawaii != null && turnFace == null) {
            turnFace = kawaii.face
            turnVerb = kawaii.verb
        }
    }
    // Anything else in the status line is a real notice (a slow provider, a
    // retry) and is worth reading, so it takes the label while it lasts.
    val notice = status.takeIf { it.isNotBlank() && kawaii == null }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
            .noRippleClickable { sheetVisible = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!isStreaming) {
            HxActivityGlyph(activity = HxTraceActivity.Think, face = null, done = true)
            Text(
                text = doneLabel(elapsedSeconds, tools.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The one hint that this quiet line opens the full trace.
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        } else {
            val runningTool = tools.lastOrNull { it.isRunning }
            val target = runningTool?.let { HxTraceActivity.of(it.toolName) } ?: HxTraceActivity.Think
            val shown = rememberSettledActivity(target)
            val face = turnFace?.takeIf { kaomoji && shown == HxTraceActivity.Think }
            HxActivityGlyph(activity = shown, face = face, done = false)
            val verb = turnVerb
            val label = when {
                shown != HxTraceActivity.Think -> stringResource(shown.label)
                notice != null -> notice
                kaomoji && verb != null -> translateThinkingVerb(verb)
                else -> stringResource(HxTraceActivity.Think.label)
            }
            HxSwapLabel(label)
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
                items = items,
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
        ?.let { stringResource(R.string.thought_for_s, it) }
        ?: stringResource(R.string.thoughts)
    if (toolCount == 0) return thought
    val tools = if (toolCount == 1) {
        stringResource(R.string.s_1_tool)
    } else {
        stringResource(R.string.tools_2, toolCount)
    }
    // "·" beside a Persian digit reads as a zero ("· ۱" looks like "۱۰"), so the
    // separator is a string: "·" in English, "،" in Persian.
    return thought + stringResource(R.string.trace_summary_separator) + tools
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
    items: List<HxTraceItem>,
    isComplete: Boolean,
    elapsedSeconds: Long?,
) {
    val scroll = rememberScrollState()
    // While the model is still thinking, keep the newest step in view: the
    // timeline runs oldest-to-newest (like the chat itself), so without this
    // the latest thought streams in below the fold where it can't be found.
    // Following stops as soon as the reader drags the sheet, so scrolling up
    // to read an earlier thought isn't yanked back down by the next token;
    // it resumes once they let go at the bottom again.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(scroll) {
        scroll.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) follow = false
        }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { !scroll.isScrollInProgress && scroll.value >= scroll.maxValue }
            .collect { atRestAtBottom -> if (atRestAtBottom) follow = true }
    }
    LaunchedEffect(isComplete, follow, scroll.maxValue) {
        if (!isComplete && follow) scroll.animateScrollTo(scroll.maxValue)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * SheetScreenFraction).dp)
            .verticalScroll(scroll)
            .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HxReasoningTimeline(
            items = items,
            isComplete = isComplete,
            elapsedSeconds = elapsedSeconds,
        )
    }
}

@Composable
private fun HxReasoningTimeline(
    items: List<HxTraceItem>,
    isComplete: Boolean,
    elapsedSeconds: Long?,
) {
    // Flattened first so "is this the last row?" is a question about rows, not
    // about items — one reasoning block can be several rows, or none.
    val rows = remember(items) { buildTimelineRows(items) }
    val toolCount = remember(items) { items.count { it is HxTraceItem.Tool } }

    // Oldest to newest, like the chat itself: the rail grows downward, so the
    // newest step is always the last row — the one carrying the live dot with
    // nothing below it. When the turn is done the Done row below closes the
    // rail instead. (The sheet auto-scrolls to this newest row while thinking.)
    Column {
        rows.forEachIndexed { index, row ->
            val isLast = !isComplete && index == rows.lastIndex
            when (row) {
                is TimelineRow.Text -> HxTimelineRow(
                    title = row.title,
                    detail = row.detail,
                    isLast = isLast,
                    icon = row.icon,
                )

                is TimelineRow.Tool -> HxTimelineToolRow(tool = row.call, isLast = isLast)
            }
        }
        if (isComplete) {
            HxTimelineRow(
                title = doneLabel(elapsedSeconds, toolCount),
                detail = stringResource(R.string.done_2),
                isLast = true,
                icon = HxIcons.CircleCheck,
            )
        }
    }
}

private sealed interface TimelineRow {
    data class Text(
        val title: String,
        val detail: String,
        val icon: ImageVector? = null,
    ) : TimelineRow

    data class Tool(val call: ChatMessage.ToolCall) : TimelineRow
}

/**
 * Turns the work log into rows.
 *
 * Reasoning the model structured itself becomes one row per heading; reasoning
 * it left as a blob becomes a single row rather than being dropped, because the
 * alternative — a separate "raw" panel below the timeline — broke the sequence
 * the timeline exists to show.
 */
private fun buildTimelineRows(items: List<HxTraceItem>): List<TimelineRow> = buildList {
    fun addReasoning(text: String) {
        val steps = parseReasoningSteps(text)
        if (steps.worthATimeline()) {
            steps.forEach { add(TimelineRow.Text(title = it.title, detail = it.detail)) }
        } else {
            val raw = text.trim()
            if (raw.isNotEmpty()) {
                add(
                    TimelineRow.Text(
                        title = "",
                        detail = if (raw.length <= RawReasoningCap) {
                            raw
                        } else {
                            raw.take(RawReasoningCap).trimEnd() + "\n…"
                        },
                    ),
                )
            }
        }
    }

    // A turn's reasoning streams into one message while its tools run, so the
    // block is held open and cut at each tool that marked where it stood —
    // otherwise all of it sits before the first tool instead of interleaving
    // with the tools in the order they actually happened.
    var openBlock: HxTraceItem.Reasoning? = null
    var cut = 0
    fun closeOpen() {
        openBlock?.let { addReasoning(it.text.substring(cut.coerceAtMost(it.text.length))) }
        openBlock = null
        cut = 0
    }

    items.forEach { item ->
        when (item) {
            is HxTraceItem.Note -> {
                val text = item.text.trim()
                if (text.isNotEmpty()) add(TimelineRow.Text(title = "", detail = text))
            }

            is HxTraceItem.Reasoning -> {
                closeOpen()
                openBlock = item
            }

            is HxTraceItem.Tool -> {
                val reasoning = openBlock
                val call = item.call
                if (reasoning != null && call.reasoningOwnerId == reasoning.messageId) {
                    // Clamped: a snapshot after a reconnect can replace the text.
                    val from = cut.coerceAtMost(reasoning.text.length)
                    val mark = call.reasoningMark.coerceIn(from, reasoning.text.length)
                    addReasoning(reasoning.text.substring(from, mark))
                    cut = mark
                }
                add(TimelineRow.Tool(call))
            }
        }
    }
    closeOpen()
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

// The same glyph the collapsed line shows for this tool, so the line reads as
// a preview of the sheet.
private fun toolGlyph(toolName: String): ImageVector = HxTraceActivity.of(toolName).glyph

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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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

// ── Activity line pieces ─────────────────────────────────────────────────────

/** The kinds of work the collapsed line can name. Each has one glyph and one label. */
internal enum class HxTraceActivity(val label: Int) {
    Think(R.string.activity_thinking),
    Web(R.string.activity_web),
    Terminal(R.string.activity_terminal),
    FileRead(R.string.activity_file_read),
    FileWrite(R.string.activity_file_write),
    Other(R.string.activity_other);

    val glyph: ImageVector
        get() = when (this) {
            Think -> HxIcons.Sparkles
            Web -> HxIcons.Globe
            Terminal -> HxIcons.Terminal
            FileRead, FileWrite -> HxIcons.FileText
            Other -> HxIcons.Wrench
        }

    companion object {
        /** Hermes' own tool names (the .py files under tools/), plus the generic ones other gateways use. */
        fun of(toolName: String): HxTraceActivity {
            val n = toolName.lowercase()
            return when {
                n in setOf("terminal", "read_terminal", "close_terminal", "process_manage", "execute_code",
                    "bash", "shell", "run_command") -> Terminal
                n.startsWith("web_") || n.startsWith("browser") || n.startsWith("fetch") ||
                    n == "x_search" || n == "browse" -> Web
                n in setOf("read_file", "search_files", "skill_view") -> FileRead
                n in setOf("write_file", "patch") -> FileWrite
                else -> Other
            }
        }
    }
}

/**
 * The activity to show, following [target] under the dwell rules above: a
 * change has to have held for a moment (longer for a return to thinking) and
 * the current one has to have been on screen long enough. Ten quick commands
 * in a row read as one "running a command", not ten flickers.
 */
@Composable
private fun rememberSettledActivity(target: HxTraceActivity): HxTraceActivity {
    var shown by remember { mutableStateOf(target) }
    val latest by rememberUpdatedState(target)
    LaunchedEffect(Unit) {
        var shownAt = android.os.SystemClock.uptimeMillis()
        var candidate = latest
        var candidateSince = shownAt
        while (true) {
            delay(ActivitySampleMillis)
            val now = android.os.SystemClock.uptimeMillis()
            if (latest != candidate) {
                candidate = latest
                candidateSince = now
            }
            val settle = if (candidate == HxTraceActivity.Think) ActivityThinkReturnMillis else ActivityMinToolMillis
            if (candidate != shown && now - candidateSince >= settle && now - shownAt >= ActivityDwellMillis) {
                shown = candidate
                shownAt = now
            }
        }
    }
    return shown
}

/**
 * The glyph beside the line. While the turn runs it breathes — a slow scale,
 * a small turn and a lift in opacity — which is the "still alive" signal;
 * its SHAPE says what kind of work it is, and a new shape hops in. Once the
 * turn is over it is the same star, still and muted.
 *
 * With kaomoji on, the agent's face for this turn stands in for the star
 * while it thinks (tools keep their own glyphs).
 */
@Composable
private fun HxActivityGlyph(activity: HxTraceActivity, face: String?, done: Boolean) {
    val reduceMotion = rememberReduceMotion()
    val running = HxStatus.running
    val breathing = !done && !reduceMotion
    val transition = rememberInfiniteTransition(label = "activity_breath")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (breathing) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "activity_breath_phase",
    )
    val key: Any = face ?: activity
    Box(
        modifier = Modifier
            .heightIn(min = 20.dp)
            .graphicsLayer {
                if (breathing) {
                    val scale = 0.88f + 0.17f * phase
                    scaleX = scale
                    scaleY = scale
                    rotationZ = 18f * phase
                    alpha = 0.75f + 0.25f * phase
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = key,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(220)) + scaleIn(
                        initialScale = 0.6f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                    )) togetherWith fadeOut(tween(160))
                }
            },
            label = "activity_glyph",
        ) { k ->
            if (k is String) {
                // Isolated left-to-right: a kaomoji is all brackets and symbols,
                // which an RTL line would otherwise mirror and reorder.
                Text(
                    text = "\u2066$k\u2069",
                    style = MaterialTheme.typography.labelMedium,
                    color = running,
                    maxLines = 1,
                )
            } else {
                Icon(
                    imageVector = (k as HxTraceActivity).glyph,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (done) MaterialTheme.colorScheme.onSurfaceVariant else running,
                )
            }
        }
    }
}

/** The label: the new one rises in as the old one lifts away, with the
 *  existing shimmer on whichever is current. */
@Composable
private fun RowScope.HxSwapLabel(text: String) {
    val reduceMotion = rememberReduceMotion()
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutVertically(tween(260)) { -it / 3 } + fadeOut(tween(200)))
            }
        },
        modifier = Modifier.weight(1f, fill = false),
        label = "activity_label",
    ) { line -> HxShimmerText(text = line) }
}

/** The agent's kaomoji status line, split into its face and verb. */
internal data class KawaiiStatus(val face: String, val verb: String)

private val KawaiiStatusRegex = Regex("""^(.+?)\s+(\p{L}[\p{L} ]*?)\.{3}$""")

/**
 * Hermes' wait line is either a kaomoji status — "{face} {verb}...", with the
 * face from KawaiiSpinner.KAWAII_THINKING — or a real notice (a slow provider,
 * a retry). A face never contains ASCII letters or digits; a notice always
 * does, which is what tells them apart.
 */
internal fun parseKawaiiStatus(status: String): KawaiiStatus? {
    val match = KawaiiStatusRegex.matchEntire(status.trim()) ?: return null
    val face = match.groupValues[1]
    if (face.any { it.code < 128 && it.isLetterOrDigit() }) return null
    return KawaiiStatus(face = face, verb = match.groupValues[2].trim())
}

/** Hermes' thinking verbs in the app's language; an unknown verb (a custom
 *  skin, another locale) falls back to the plain "Thinking" label. */
@Composable
private fun translateThinkingVerb(verb: String): String {
    val keys = stringArrayResource(R.array.thinking_verb_keys)
    val labels = stringArrayResource(R.array.thinking_verb_labels)
    val i = keys.indexOf(verb.lowercase())
    return if (i in labels.indices) labels[i] else stringResource(R.string.activity_thinking)
}

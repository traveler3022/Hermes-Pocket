package com.hermes.android.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage


/** One quiet icon in the post-reply action row: 32dp touch target, 16dp
 *  glyph, muted tint — present but never competing with the reply text. */
@Composable
internal fun MessageActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/** Bouncing three-dot "typing" indicator — replaces the generic circular
 *  spinner while the agent is composing a reply, matching the familiar
 *  chat-app convention (WhatsApp/iMessage) instead of a loading spinner. */
@Composable
internal fun TypingDots(
    modifier: Modifier = Modifier,
    dotSize: androidx.compose.ui.unit.Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "typingDots")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val bounce by transition.animateFloat(
                initialValue = 0f,
                targetValue = if (reduceMotion) 0f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150, StartOffsetType.FastForward),
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .offset(y = (-bounce * 4).dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (reduceMotion) 0.7f else 0.4f + bounce * 0.6f)),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    message: ChatMessage,
    grouped: Boolean = false,
    isLastInGroup: Boolean = true,
    searchQuery: String = "",
    isLastAssistant: Boolean = false,
    isSending: Boolean = false,
    onCopyMessage: (String) -> Unit = {},
    onCopyCode: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onRespondToClarify: (requestId: String, picked: List<String>) -> Unit = { _, _ -> },
    onRespondToClarifyBatch: (requestId: String, answers: Map<String, List<String>>) -> Unit = { _, _ -> },
    onRespondToSudo: (requestId: String, password: String) -> Unit = { _, _ -> },
    onRespondToSecret: (requestId: String, value: String) -> Unit = { _, _ -> },
    onImageClick: (String) -> Unit = {},
    resolveUrl: (String) -> String = { it },
    onBranch: () -> Unit = {},
    onDownloadFile: (url: String, name: String) -> Unit = { _, _ -> },
    traceItems: List<HxTraceItem> = emptyList(),
    thinkingStatus: String = "",
    onEditMessage: ((messageId: String) -> Unit)? = null,
    onDeleteMessage: ((messageId: String) -> Unit)? = null,
) {
    when (message) {
        is ChatMessage.User -> {
            // Both rewrite the stored transcript, which the server refuses mid-turn, and
            // a message still waiting for Hermes to boot has nothing stored to rewrite.
            val canRewind = !isSending && !message.queued
            UserMessageBubble(
                message = message,
                searchQuery = searchQuery,
                isLastInGroup = isLastInGroup,
                onCopyMessage = onCopyMessage,
                // An attached image cannot be sent again from here, so an edit would
                // quietly drop it; the text-only edit is offered where nothing is lost.
                onEdit = onEditMessage
                    ?.takeIf { canRewind && message.attachments.none { it.isImage } }
                    ?.let { edit -> { edit(message.id) } },
                onDelete = onDeleteMessage
                    ?.takeIf { canRewind }
                    ?.let { delete -> { delete(message.id) } },
            )
        }

        is ChatMessage.Assistant -> {
            AssistantMessageBubble(
                message = message,
                searchQuery = searchQuery,
                isLastAssistant = isLastAssistant,
                isSending = isSending,
                onCopyMessage = onCopyMessage,
                onCopyCode = onCopyCode,
                onRetry = onRetry,
                onImageClick = onImageClick,
                resolveUrl = resolveUrl,
                onBranch = onBranch,
                onDownloadFile = onDownloadFile,
                traceItems = traceItems,
                thinkingStatus = thinkingStatus,
            )
        }

        is ChatMessage.ToolCall -> {
            ToolCallCard(message = message)
        }

        is ChatMessage.Status -> {
            Text(
                text = message.text,
                style = MaterialTheme.typography.labelSmall,
                color = if (message.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }

        is ChatMessage.InteractiveRequest -> InteractiveRequestCard(
            message = message,
            onRespondToClarify = onRespondToClarify,
            onRespondToClarifyBatch = onRespondToClarifyBatch,
            onRespondToSudo = onRespondToSudo,
            onRespondToSecret = onRespondToSecret,
        )

        is ChatMessage.SubagentCard -> {
            val isLongSubagent = message.text.length > 120
            var subagentExpanded by remember { mutableStateOf(false) }
            val subagentAccent = MaterialTheme.colorScheme.secondary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(subagentAccent.copy(alpha = 0.06f))
                    .border(1.dp, subagentAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .clickable(enabled = isLongSubagent) { subagentExpanded = !subagentExpanded },
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!message.isComplete) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("🤖 Sub-agent", "🤖 زیر ایجنت"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val displayText = if (isLongSubagent && !subagentExpanded) {
                            message.text.take(120) + "…"
                        } else {
                            message.text
                        }
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (isLongSubagent) {
                        Icon(
                            imageVector = if (subagentExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ── Feature #16: Search highlight helper ─────────────────────────────────


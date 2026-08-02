package com.hermes.android.ui.screen.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.design.HxChatTokens
import com.hermes.android.ui.design.hxSoftShadow
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.screen.MessageBubble

/**
 * HxAssistantTurnGroup
 *
 * Renders one contiguous agent "turn" (assistant text + tool calls +
 * interactive requests + subagent cards + status lines) as a single
 * softly-shadowed card. Avatar appears once on the leading edge,
 * matching how ChatGPT / Claude / Aether draw a single assistant turn.
 *
 * Clean-room implementation — not a copy of Aether; uses our own tokens
 * (HxChatTokens) and delegates individual message rendering to the
 * existing MessageBubble composable so behaviour (copy/retry/images/etc.)
 * is preserved.
 */
@Composable
internal fun HxAssistantTurnGroup(
    messages: List<ChatMessage>,
    isSending: Boolean,
    isLastTurn: Boolean,
    searchQuery: String,
    onCopyMessage: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onRetry: () -> Unit,
    onImageClick: (String) -> Unit,
    resolveUrl: (String) -> String,
    onBranch: () -> Unit,
    onDownloadFile: (url: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) return

    // A turn that ends with a streaming Assistant (or no final Assistant yet
    // while the model is generating) should show a pending indicator after
    // the last rendered card.
    val lastMsg = messages.last()
    val isGenerating = isSending && isLastTurn && (
        lastMsg !is ChatMessage.Assistant || lastMsg.isStreaming || lastMsg.text.isBlank()
    )

    // Surface colour softly tinted so consecutive turns visually separate
    // from each other without heavy borders.
    val surface = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // Leading column: avatar once per turn (same approach as
        // AssistantMessageBubble, but attached to the card instead of each
        // bubble so cards stack as a single unit).
        TurnAvatar(
            messages = messages,
            isGenerating = isGenerating,
            modifier = Modifier.padding(top = 4.dp, end = 8.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .hxSoftShadow(radius = 12.dp, shape = shape)
                .clip(shape)
                .background(surface)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            messages.forEachIndexed { idx, msg ->
                val isLastInTurn = idx == messages.lastIndex
                val isCard = msg is ChatMessage.ToolCall ||
                    msg is ChatMessage.SubagentCard ||
                    msg is ChatMessage.InteractiveRequest
                MessageBubble(
                    message = msg,
                    grouped = idx > 0,
                    isLastInGroup = isLastInTurn,
                    searchQuery = searchQuery,
                    isLastAssistant = isLastInTurn && isLastTurn && msg is ChatMessage.Assistant && !msg.isStreaming,
                    isSending = isSending,
                    onCopyMessage = onCopyMessage,
                    onCopyCode = onCopyCode,
                    onRetry = onRetry,
                    onImageClick = onImageClick,
                    resolveUrl = resolveUrl,
                    onBranch = onBranch,
                    onDownloadFile = onDownloadFile,
                )
                // Small divider between text and cards for visual breathing room
                if (!isLastInTurn && isCard && messages[idx + 1] is ChatMessage.Assistant) {
                    Spacer(modifier = Modifier.padding(top = 2.dp))
                }
            }

            if (isGenerating) {
                HxPendingGenerationBlock()
            }
        }
    }
}

/**
 * A tiny avatar dot that re-uses the assistant's initial letter if no
 * URI is available. We keep it intentionally minimal (32dp circle)
 * because the group card already has substantial visual weight.
 */
@Composable
private fun TurnAvatar(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
) {
    val firstAssistant = messages.firstNotNullOfOrNull { it as? ChatMessage.Assistant }
    val streaming = isGenerating || firstAssistant?.isStreaming == true
    val bg = if (streaming) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        // Hermes "H" glyph — the assistant model itself doesn't carry a
        // display name yet; keep it simple and on-brand.
        Text(
            text = "H",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Soft three-dots indicator shown at the tail of an in-progress turn.
 * Generic implementation (no Aether/GPL code); just a row of fading dots.
 */
@Composable
internal fun HxPendingGenerationBlock(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = 0.35f + 0.2f * i)),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "…",
            style = MaterialTheme.typography.bodyMedium,
            color = dotColor,
        )
    }
}

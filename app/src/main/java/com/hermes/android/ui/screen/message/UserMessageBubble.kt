package com.hermes.android.ui.screen

import com.hermes.android.ui.design.HxRadius
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.AttachFile
import com.hermes.android.ui.icons.filled.ContentCopy
import com.hermes.android.ui.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import com.hermes.android.ui.icons.filled.ExpandLess
import com.hermes.android.ui.icons.filled.ExpandMore
import com.hermes.android.ui.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hermes.android.ui.design.hxSoftShadow
import com.hermes.android.ui.design.hxUserBubbleMaxWidth
import com.hermes.android.ui.viewmodel.ChatMessage

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UserMessageBubble(
    message: ChatMessage.User,
    searchQuery: String = "",
    isLastInGroup: Boolean = true,
    onCopyMessage: (String) -> Unit = {},
    /** Null hides the action: a turn is running, or the message never reached Hermes. */
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    reactionsEnabled: Boolean = false,
    /** Null: the message cannot take a reaction (it has not reached Hermes yet). */
    onReact: ((String?) -> Unit)? = null,
) {
    val isLongMessage = message.text.length > 500
    var isExpanded by remember { mutableStateOf(!isLongMessage) }
    var showMenu by remember { mutableStateOf(false) }

    val bubbleShape = if (isLastInGroup) {
        RoundedCornerShape(topStart = HxRadius.md, topEnd = HxRadius.md, bottomStart = HxRadius.md, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(HxRadius.md)
    }
    // A soft primary tint (no hard fill or border) with a gentle shadow — readable
    // in every theme because the tint is translucent and the text stays onSurface.
    // 0.24, not 0.14: on near-black the lighter tint read as no bubble at all
    // (1.19:1 against the chat background); this keeps it a quiet region.
    val bubbleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    val bubbleTextColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = hxUserBubbleMaxWidth())
                    .hxSoftShadow(radius = 12.dp, shape = bubbleShape)
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .combinedClickable(
                        onClick = { if (isLongMessage) isExpanded = !isExpanded },
                        onLongClick = { showMenu = true },
                    ),
            ) {
                CompositionLocalProvider(LocalContentColor provides bubbleTextColor) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .animateContentSize(),
                    ) {
                        if (message.attachments.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                message.attachments.forEach { attachment ->
                                    if (attachment.isImage && attachment.localUri != null) {
                                        AsyncImage(
                                            model = attachment.localUri,
                                            contentDescription = attachment.name,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp)
                                                .clip(RoundedCornerShape(HxRadius.sm)),
                                            contentScale = ContentScale.Fit,
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(HxRadius.sm))
                                                .background(bubbleTextColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.AttachFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = bubbleTextColor,
                                            )
                                            Text(
                                                text = attachment.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                color = bubbleTextColor,
                                            )
                                        }
                                    }
                                }
                            }
                            if (message.text.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        val displayText = if (!isExpanded && isLongMessage) {
                            message.text.take(300) + "\u2026"
                        } else {
                            message.text
                        }
                        if (message.text.isNotBlank()) {
                            if (searchQuery.isNotBlank()) {
                                Text(
                                    text = highlightText(displayText, searchQuery),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            } else {
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = bubbleTextColor,
                                )
                            }
                        }
                        if (isLongMessage) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                    modifier = Modifier.size(18.dp),
                                    tint = bubbleTextColor.copy(alpha = 0.6f),
                                )
                            }
                        }
                        // Sent before Hermes finished booting: say so on the bubble itself, so
                        // the message does not read as delivered-and-ignored while it waits.
                        if (message.queued) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = bubbleTextColor.copy(alpha = 0.6f),
                                )
                                Text(
                                    text = stringResource(R.string.waiting_for_hermes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = bubbleTextColor.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (reactionsEnabled && onReact != null) {
                        ReactionPicker(
                            selected = message.reactions.firstOrNull { it.isMine }?.emoji,
                            onSelect = { showMenu = false; onReact(it) },
                        )
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy)) },
                        onClick = { showMenu = false; onCopyMessage(message.text) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    )
                    onEdit?.let { edit ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = { showMenu = false; edit() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        )
                    }
                    onDelete?.let { delete ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; delete() },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                        )
                    }
                }
            }
        }
        // Below the bubble, in the register of the reply's action row (the desktop's
        // placement; laid over the bubble's corner it read badly).
        ReactionBadge(
            reactions = message.reactions,
            modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            onRetract = onReact?.takeIf { reactionsEnabled }?.let { react -> { react(null) } },
        )
    }
}

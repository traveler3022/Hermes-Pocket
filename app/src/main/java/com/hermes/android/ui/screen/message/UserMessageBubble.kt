package com.hermes.android.ui.screen

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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UserMessageBubble(
    message: ChatMessage.User,
    searchQuery: String = "",
    isLastInGroup: Boolean = true,
    onCopyMessage: (String) -> Unit = {},
) {
    val isLongMessage = message.text.length > 500
    var isExpanded by remember { mutableStateOf(!isLongMessage) }

    // Aether user bubble: a soft lavender fill (primaryContainer =
    // messageBubble in the Aether palette) with onPrimaryContainer text and a
    // gentle shadow — all corners uniformly rounded like Aether, no tail.
    val bubbleShape = RoundedCornerShape(24.dp)
    val bubbleColor = MaterialTheme.colorScheme.primaryContainer
    val bubbleTextColor = MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .hxSoftShadow(radius = 10.dp, shape = bubbleShape)
                .clip(bubbleShape)
                .background(bubbleColor)
                .combinedClickable(
                    onClick = { if (isLongMessage) isExpanded = !isExpanded },
                    onLongClick = { onCopyMessage(message.text) },
                ),
        ) {
            CompositionLocalProvider(LocalContentColor provides bubbleTextColor) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 14.dp)
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
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
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
                                contentDescription = if (isExpanded) t("Collapse", "جمع کردن") else t("Expand", "باز کردن"),
                                modifier = Modifier.size(18.dp),
                                tint = bubbleTextColor.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

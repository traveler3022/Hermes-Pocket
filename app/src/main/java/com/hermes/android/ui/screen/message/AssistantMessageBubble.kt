package com.hermes.android.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.component.ContentBlock
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.component.parseContentBlocks
import com.hermes.android.ui.components.thinking.HxSmartThinkingBlock
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AssistantMessageBubble(
    message: ChatMessage.Assistant,
    searchQuery: String = "",
    isLastAssistant: Boolean = false,
    isSending: Boolean = false,
    onCopyMessage: (String) -> Unit = {},
    onCopyCode: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onImageClick: (String) -> Unit = {},
    resolveUrl: (String) -> String = { it },
    onBranch: () -> Unit = {},
    onDownloadFile: (url: String, name: String) -> Unit = { _, _ -> },
) {
    val isLongResponse = message.text.length > 1500
    var isResponseExpanded by remember { mutableStateOf(true) }
    var isThinkingExpanded by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    val hasThinking = message.reasoning != null && message.reasoning.isNotEmpty()

    val assistantContext = LocalContext.current
    val codeBlocks = remember(message.text) { extractCodeBlocks(message.text) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.widthIn(max = 460.dp)) {
            Box {
                Column(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showContextMenu = true },
                        )
                        .animateContentSize()
                        .padding(vertical = 2.dp),
                ) {
                    if (hasThinking) {
                        HxSmartThinkingBlock(
                            reasoning = message.reasoning ?: "",
                            isStreaming = message.isStreaming,
                            expanded = isThinkingExpanded,
                            onToggle = { isThinkingExpanded = !isThinkingExpanded },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (message.text.isEmpty()) {
                        TypingDots(modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        val displayMd = if (!isResponseExpanded && isLongResponse) {
                            message.text.take(800) + "\n\n\u2026"
                        } else {
                            message.text
                        }
                        val blocks = remember(displayMd) {
                            parseContentBlocks(displayMd).map { block ->
                                when (block) {
                                    is ContentBlock.Image -> block.copy(url = resolveUrl(block.url))
                                    is ContentBlock.Video -> block.copy(url = resolveUrl(block.url))
                                    is ContentBlock.Html -> block.copy(url = resolveUrl(block.url))
                                    is ContentBlock.FileRef -> block.copy(url = resolveUrl(block.url))
                                    else -> block
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            blocks.forEach { block ->
                                when (block) {
                                    is ContentBlock.Text -> SelectionContainer {
                                        if (message.isStreaming) {
                                            Text(
                                                text = block.markdown,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                ),
                                            )
                                        } else {
                                            HermesMarkdown(
                                                markdown = block.markdown,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                ),
                                            )
                                        }
                                    }
                                    is ContentBlock.Image -> InlineImageBlock(
                                        alt = block.alt, url = block.url,
                                        onImageClick = onImageClick,
                                        onSave = { onDownloadFile(block.url, block.alt) },
                                    )
                                    is ContentBlock.Code -> CodeBlockCard(
                                        language = block.language, code = block.code,
                                        onCopyCode = onCopyCode,
                                    )
                                    is ContentBlock.Mermaid -> MermaidBlockCard(
                                        code = block.code, onCopyCode = onCopyCode,
                                    )
                                    is ContentBlock.Html -> HtmlBlockCard(
                                        url = block.url, name = block.name,
                                        onOpenExternal = { openUrlExternally(assistantContext, block.url) },
                                    )
                                    is ContentBlock.Video -> ArtifactCard(
                                        emoji = "\uD83C\uDFAC", name = block.name,
                                        actionLabel = t("Play", "پخش"),
                                        onAction = { openUrlExternally(assistantContext, block.url) },
                                        onDownload = { onDownloadFile(block.url, block.name) },
                                    )
                                    is ContentBlock.FileRef -> ArtifactCard(
                                        emoji = "\uD83D\uDCC4", name = block.name,
                                        actionLabel = t("Download", "دانلود"),
                                        onAction = { onDownloadFile(block.url, block.name) },
                                        onDownload = null,
                                    )
                                }
                            }
                        }
                        if (isLongResponse) {
                            TextButton(
                                onClick = { isResponseExpanded = !isResponseExpanded },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Icon(
                                    imageVector = if (isResponseExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isResponseExpanded) t("Collapse", "جمع کردن") else t("Show more", "ادامه..."),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    if (message.isStreaming) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TypingDots(dotSize = 4.dp)
                    }
                }
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(t("Copy text", "کپی متن")) },
                        onClick = { onCopyMessage(message.text); showContextMenu = false },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    )
                    val firstCode = codeBlocks.firstOrNull()
                    if (firstCode != null) {
                        DropdownMenuItem(
                            text = { Text(t("Copy code", "کپی کد")) },
                            onClick = { onCopyCode(firstCode); showContextMenu = false },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(t("Share", "اشتراک\u200Cگذاری")) },
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, message.text)
                                type = "text/plain"
                            }
                            assistantContext.startActivity(Intent.createChooser(sendIntent, null))
                            showContextMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(t("Branch conversation", "شاخه\u200Cزدن گفتگو")) },
                        onClick = { onBranch(); showContextMenu = false },
                        leadingIcon = { Icon(Icons.Default.CallSplit, contentDescription = null) },
                    )
                }
            }

            if (!message.isStreaming && message.text.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MessageActionIcon(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = t("Copy text", "کپی متن"),
                        onClick = { onCopyMessage(message.text) },
                    )
                    MessageActionIcon(
                        icon = Icons.Default.Share,
                        contentDescription = t("Share", "اشتراک\u200Cگذاری"),
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, message.text)
                                type = "text/plain"
                            }
                            assistantContext.startActivity(Intent.createChooser(sendIntent, null))
                        },
                    )
                    if (isLastAssistant && !isSending) {
                        MessageActionIcon(
                            icon = Icons.Default.Refresh,
                            contentDescription = t("Retry", "تلاش دوباره"),
                            onClick = onRetry,
                        )
                    }
                }
            }
        }
    }
}

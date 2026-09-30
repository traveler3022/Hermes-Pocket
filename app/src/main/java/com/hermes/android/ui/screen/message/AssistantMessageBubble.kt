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
import com.hermes.android.ui.icons.filled.ContentCopy
import com.hermes.android.ui.icons.filled.ExpandLess
import com.hermes.android.ui.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.design.hxAssistantMaxWidth
import com.hermes.android.ui.component.ContentBlock
import com.hermes.android.ui.component.FileKind
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.component.parseContentBlocks
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewer.FileViewerActivity
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
    /** Text of an HTML file for a `::preview` (null: unreadable), and a page's hidden prompt. */
    readPreview: suspend (file: String) -> String? = { null },
    resolvePreviewUrl: (file: String) -> String = { it },
    previewShareUri: suspend (file: String) -> android.net.Uri? = { null },
    onPreviewSend: (String) -> Unit = {},
    traceItems: List<HxTraceItem> = emptyList(),
    /** The agent's live status line; only the streaming reply is given one. */
    thinkingStatus: String = "",
    reactionsEnabled: Boolean = false,
    /** Null: the reply cannot take a reaction yet (still streaming). */
    onReact: ((String?) -> Unit)? = null,
) {
    val isLongResponse = message.text.length > 1500
    var isResponseExpanded by remember { mutableStateOf(true) }
    var showContextMenu by remember { mutableStateOf(false) }
    // The trace covers everything the turn did on the way here — its own
    // reasoning, what it said between tool calls, and the tools themselves.
    val hasTrace = traceItems.isNotEmpty()

    val assistantContext = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.widthIn(max = hxAssistantMaxWidth())) {
            Box {
                Column(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showContextMenu = true },
                        )
                        // Only a finished reply animates its size (Show more /
                        // Collapse). While streaming the text grows every 80ms
                        // flush, and each growth restarted a size animation:
                        // the bubble never stopped animating and relaid out
                        // the list under it on every frame.
                        .then(if (message.isStreaming) Modifier else Modifier.animateContentSize())
                        .padding(vertical = 2.dp),
                ) {
                    if (hasTrace) {
                        HxThinkingTrace(
                            items = traceItems,
                            isStreaming = message.isStreaming,
                            messageId = message.id,
                            status = thinkingStatus,
                        )
                    }
                    // A turn can end on a tool rather than a sentence, leaving
                    // this message with no text at all. Only a turn still in
                    // flight is waiting for words.
                    if (message.text.isEmpty()) {
                        if (message.isStreaming) {
                            TypingDots(modifier = Modifier.padding(vertical = 4.dp))
                        }
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
                                    is ContentBlock.Audio -> block.copy(url = resolveUrl(block.url))
                                    is ContentBlock.Html -> block.copy(url = resolveUrl(block.url))
                                    is ContentBlock.FileRef -> block.copy(url = resolveUrl(block.url))
                                    else -> block
                                }
                            }
                        }
                        // One selection over the whole reply, so a selection runs from prose
                        // into code and on past it (each text run used to have its own).
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                blocks.forEach { block ->
                                    when (block) {
                                        // Rendered as markdown while streaming too.
                                        // The plain-Text fallback here dated from
                                        // when this renderer was a TextView behind
                                        // AndroidView and re-laid out the whole view
                                        // per token; it is pure Compose now, and the
                                        // fallback's only remaining effect was
                                        // showing raw ** and ## until the turn ended.
                                        is ContentBlock.Text -> HermesMarkdown(
                                            markdown = block.markdown,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                            ),
                                        )
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
                                            onOpen = { FileViewerActivity.open(assistantContext, block.url, block.name) },
                                        )
                                        is ContentBlock.Preview -> InlinePreviewFrame(
                                            file = block.file,
                                            initialHeight = block.height,
                                            streaming = message.isStreaming,
                                            readPreview = readPreview,
                                            onSend = onPreviewSend,
                                            shareUri = { previewShareUri(block.file) },
                                            onOpen = {
                                                FileViewerActivity.open(
                                                    assistantContext, resolvePreviewUrl(block.file), block.file.substringAfterLast('/'),
                                                )
                                            },
                                        )
                                        is ContentBlock.Video -> ArtifactCard(
                                            emoji = "\uD83C\uDFAC", name = block.name,
                                            actionLabel = t("Play", "پخش"),
                                            onAction = { FileViewerActivity.open(assistantContext, block.url, block.name) },
                                            onDownload = { onDownloadFile(block.url, block.name) },
                                        )
                                        is ContentBlock.Audio -> ArtifactCard(
                                            emoji = "\uD83C\uDFB5", name = block.name,
                                            actionLabel = t("Play", "پخش"),
                                            onAction = { FileViewerActivity.open(assistantContext, block.url, block.name) },
                                            onDownload = { onDownloadFile(block.url, block.name) },
                                        )
                                        is ContentBlock.FileRef -> if (block.kind == FileKind.OTHER) {
                                            ArtifactCard(
                                                emoji = "\uD83D\uDCC4", name = block.name,
                                                actionLabel = t("Download", "دانلود"),
                                                onAction = { onDownloadFile(block.url, block.name) },
                                                onDownload = null,
                                            )
                                        } else {
                                            ArtifactCard(
                                                emoji = "\uD83D\uDCC4", name = block.name,
                                                actionLabel = t("Open", "باز کردن"),
                                                onAction = { FileViewerActivity.open(assistantContext, block.url, block.name) },
                                                onDownload = { onDownloadFile(block.url, block.name) },
                                            )
                                        }
                                    }
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
                    // An empty streaming reply already shows its dots above.
                    if (message.isStreaming && message.text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TypingDots(dotSize = 4.dp)
                    }
                }
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                ) {
                    if (reactionsEnabled && onReact != null) {
                        ReactionPicker(
                            selected = message.reactions.firstOrNull { it.isMine }?.emoji,
                            onSelect = { showContextMenu = false; onReact(it) },
                        )
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        text = { Text(t("Copy text", "کپی متن")) },
                        onClick = { onCopyMessage(message.text); showContextMenu = false },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    )
                    // Scanned when the menu opens, not on every streamed flush.
                    val firstCode = remember(message.text) { extractCodeBlocks(message.text).firstOrNull() }
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
                        leadingIcon = { Icon(HxIcons.GitBranch, contentDescription = null) },
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
                    if (onReact != null && (reactionsEnabled || message.reactions.isNotEmpty())) {
                        ReactionSlot(reactions = message.reactions, enabled = reactionsEnabled, onReact = onReact)
                    }
                }
            }
        }
    }
}

package com.hermes.android.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.ContentCopy
import com.hermes.android.ui.icons.filled.ExpandLess
import com.hermes.android.ui.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
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
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.design.hxAssistantMaxWidth
import com.hermes.android.ui.component.ContentBlock
import com.hermes.android.ui.component.FileKind
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.component.parseContentBlocks
import com.hermes.android.ui.component.preprocessAssistantMarkdown
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
    /** Text of a gateway HTML file for the inline HTML card; null keeps loading it by URL. */
    readServerHtml: (suspend (url: String) -> String?)? = null,
    resolvePreviewUrl: (file: String) -> String = { it },
    previewShareUri: suspend (file: String) -> android.net.Uri? = { null },
    onPreviewSend: (String) -> Unit = {},
    traceItems: List<HxTraceItem> = emptyList(),
    /** The agent's live status line; only the streaming reply is given one. */
    thinkingStatus: String = "",
    reactionsEnabled: Boolean = false,
    /** Null: the reply cannot take a reaction yet (still streaming). */
    onReact: ((String?) -> Unit)? = null,
    /** Settings > Hermes kaomoji: the agent's face and verb on the thinking line. */
    kaomoji: Boolean = false,
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
                        // No long-press menu here: it opened together with the text
                        // selection's own Copy / Select all. A long press now only
                        // selects text; the reply's actions live behind the ⋮ below.
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
                            kaomoji = kaomoji,
                        )
                    }
                    // Hermes is drawing an image: hold its place until the reply shows it.
                    if (message.isStreaming) {
                        runningImageGeneration(traceItems)?.let { ImageGenerationPlaceholder(it) }
                    }
                    // A turn can end on a tool rather than a sentence, leaving
                    // this message with no text at all. Only a turn still in
                    // flight is waiting for words; once the thinking line is up
                    // it is the live indicator, so the dots go.
                    if (message.text.isEmpty()) {
                        if (message.isStreaming && !hasTrace) {
                            TypingDots(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    } else {
                        // Reasoning blocks, stray backticks, prose wrapped in fences and
                        // unmatched citation markers are cleaned up before parsing.
                        val cleanMd = remember(message.text) { preprocessAssistantMarkdown(message.text) }
                        val displayMd = if (!isResponseExpanded && isLongResponse) {
                            cleanMd.take(800) + "\n\n\u2026"
                        } else {
                            cleanMd
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
                                            readServerHtml = readServerHtml,
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
                                        is ContentBlock.Video -> InlineVideoPlayer(
                                            url = block.url, name = block.name,
                                            onDownload = { onDownloadFile(block.url, block.name) },
                                        )
                                        is ContentBlock.Audio -> InlineAudioPlayer(
                                            url = block.url, name = block.name,
                                            onDownload = { onDownloadFile(block.url, block.name) },
                                        )
                                        is ContentBlock.FileRef -> if (block.kind == FileKind.OTHER) {
                                            ArtifactCard(
                                                icon = HxIcons.FileText,name = block.name,
                                                actionLabel = stringResource(R.string.download),
                                                onAction = { onDownloadFile(block.url, block.name) },
                                                onDownload = null,
                                            )
                                        } else {
                                            ArtifactCard(
                                                icon = HxIcons.FileText,name = block.name,
                                                actionLabel = stringResource(R.string.open),
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
                                    text = if (isResponseExpanded) stringResource(R.string.collapse) else stringResource(R.string.show_more),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }

            if (!message.isStreaming && message.text.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLastAssistant && !isSending) {
                        MessageActionIcon(
                            icon = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.retry),
                            onClick = onRetry,
                        )
                    }
                    if (onReact != null && (reactionsEnabled || message.reactions.isNotEmpty())) {
                        ReactionSlot(reactions = message.reactions, enabled = reactionsEnabled, onReact = onReact)
                    }
                    // Copy and share used to sit in this row and again in the
                    // long-press menu; they now live only here, behind the ⋮.
                    Box {
                        MessageActionIcon(
                            icon = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            onClick = { showContextMenu = true },
                        )
                        DropdownMenu(
                            expanded = showContextMenu,
                            onDismissRequest = { showContextMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_text)) },
                                onClick = { onCopyMessage(message.text); showContextMenu = false },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            )
                            // Scanned when the menu opens, not on every streamed flush.
                            val firstCode = remember(message.text) { extractCodeBlocks(message.text).firstOrNull() }
                            if (firstCode != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_code)) },
                                    onClick = { onCopyCode(firstCode); showContextMenu = false },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
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
                                text = { Text(stringResource(R.string.branch_conversation)) },
                                onClick = { onBranch(); showContextMenu = false },
                                leadingIcon = { Icon(HxIcons.GitBranch, contentDescription = null) },
                            )
                        }
                    }
                }
            }
        }
    }
}

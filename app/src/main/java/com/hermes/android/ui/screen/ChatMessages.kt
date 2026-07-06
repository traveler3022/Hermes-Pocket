package com.hermes.android.ui.screen

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.component.ContentBlock
import com.hermes.android.ui.component.parseContentBlocks
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.InteractiveKind

@Composable
internal fun ThinkingBlock(
    reasoning: String,
    isStreaming: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "thinking")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val barAlpha = if (isStreaming) pulse else 0.4f

    // Emotive markers the model emits inside its reasoning (😌 🤔 😅 …) become
    // a big "sticker" beside the thinking state — the agent's mood, live.
    val emojiRe = remember { Regex("[\\uD83C-\\uDBFF][\\uDC00-\\uDFFF]|[\\u2600-\\u27BF\\u2B00-\\u2BFF]") }
    val sticker = remember(reasoning) { emojiRe.findAll(reasoning).map { it.value }.lastOrNull() }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor.copy(alpha = barAlpha)),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isStreaming) t("Thinking…", "در حال فکر…") else t("Thoughts", "افکار"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }

        // Mood sticker: the latest emotive emoji from the reasoning, shown big,
        // popping in when it changes.
        AnimatedVisibility(visible = sticker != null) {
            Text(
                text = sticker ?: "",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 4.dp),
            )
        }

        // Live preview: latest reasoning line, fading with the pulse, while collapsed.
        if (isStreaming && !expanded) {
            val preview = remember(reasoning) {
                reasoning.trim().lines().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
            }
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulse * 0.8f),
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 11.dp, bottom = 2.dp),
                )
            }
        }

        // Full reasoning: fades/expands in when opened (the "fade from bottom").
        AnimatedVisibility(visible = expanded) {
            HermesMarkdown(
                markdown = reasoning,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 11.dp, bottom = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    message: ChatMessage,
    grouped: Boolean = false,
    searchQuery: String = "",
    isLastAssistant: Boolean = false,
    isSending: Boolean = false,
    onCopyMessage: (String) -> Unit = {},
    onCopyCode: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onRespondToClarify: (requestId: String, answer: String) -> Unit = { _, _ -> },
    onRespondToSudo: (requestId: String, password: String) -> Unit = { _, _ -> },
    onRespondToSecret: (requestId: String, value: String) -> Unit = { _, _ -> },
    onImageClick: (String) -> Unit = {},
    resolveUrl: (String) -> String = { it },
    onBranch: () -> Unit = {},
) {
    when (message) {
        is ChatMessage.User -> {
            val isLongMessage = message.text.length > 500
            var isExpanded by remember { mutableStateOf(!isLongMessage) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .combinedClickable(
                            onClick = { if (isLongMessage) isExpanded = !isExpanded },
                            onLongClick = { onCopyMessage(message.text) },
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .animateContentSize(),
                    ) {
                        // Attachments render as their own elements — an image
                        // thumbnail or a file chip — never merged into the text.
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
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.AttachFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                            Text(
                                                text = attachment.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                            message.text.take(300) + "…"
                        } else {
                            message.text
                        }
                        if (message.text.isNotBlank()) if (searchQuery.isNotBlank()) {
                            Text(
                                text = highlightText(displayText, searchQuery),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
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
                                    contentDescription = if (isExpanded) "جمع کردن" else "باز کردن",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }
        }

        is ChatMessage.Assistant -> {
            val isLongResponse = message.text.length > 1500
            var isResponseExpanded by remember { mutableStateOf(true) }
            var isThinkingExpanded by remember { mutableStateOf(false) }
            var showContextMenu by remember { mutableStateOf(false) }
            val hasThinking = message.reasoning != null && message.reasoning.isNotEmpty()

            val assistantContext = LocalContext.current
            val codeBlocks = remember(message.text) { extractCodeBlocks(message.text) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                // Agent avatar (Telegram-style): shown once per group. Grouped
                // messages reserve the same width so bubbles stay aligned.
                if (grouped) {
                    Spacer(modifier = Modifier.width(34.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⚕",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Column(modifier = Modifier.widthIn(max = 420.dp)) {
                    Box {
                        Card(
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { showContextMenu = true },
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                        ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .animateContentSize(),
                        ) {
                            if (hasThinking) {
                                ThinkingBlock(
                                    reasoning = message.reasoning ?: "",
                                    isStreaming = message.isStreaming,
                                    expanded = isThinkingExpanded,
                                    onToggle = { isThinkingExpanded = !isThinkingExpanded },
                                )
                            }
                            if (message.text.isEmpty()) {
                                Text(
                                    text = "...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                val displayMd = if (!isResponseExpanded && isLongResponse) {
                                    message.text.take(800) + "\n\n…"
                                } else {
                                    message.text
                                }
                                // Multi-format agent output: split into typed
                                // blocks (text/image/code/mermaid/html/video/
                                // file) and give each its own native renderer.
                                // Gateway-local paths are mapped through the
                                // loopback download endpoint at parse time.
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
                                                HermesMarkdown(markdown = block.markdown)
                                            }
                                            is ContentBlock.Image -> InlineImageBlock(
                                                alt = block.alt,
                                                url = block.url,
                                                onImageClick = onImageClick,
                                                onSave = { saveImageToDownloads(assistantContext, block.url, block.alt) },
                                            )
                                            is ContentBlock.Code -> CodeBlockCard(
                                                language = block.language,
                                                code = block.code,
                                                onCopyCode = onCopyCode,
                                            )
                                            is ContentBlock.Mermaid -> MermaidBlockCard(
                                                code = block.code,
                                                onCopyCode = onCopyCode,
                                            )
                                            is ContentBlock.Html -> HtmlBlockCard(
                                                url = block.url,
                                                name = block.name,
                                                onOpenExternal = { openUrlExternally(assistantContext, block.url) },
                                            )
                                            is ContentBlock.Video -> ArtifactCard(
                                                emoji = "🎬",
                                                name = block.name,
                                                actionLabel = t("Play", "پخش"),
                                                onAction = { openUrlExternally(assistantContext, block.url) },
                                                onDownload = { saveImageToDownloads(assistantContext, block.url, block.name) },
                                            )
                                            is ContentBlock.FileRef -> ArtifactCard(
                                                emoji = "📄",
                                                name = block.name,
                                                actionLabel = t("Download", "دانلود"),
                                                onAction = { saveImageToDownloads(assistantContext, block.url, block.name) },
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
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                        }
                        // Feature 9: long-press context menu
                        DropdownMenu(
                            expanded = showContextMenu,
                            onDismissRequest = { showContextMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(t("Copy text", "کپی متن")) },
                                onClick = {
                                    onCopyMessage(message.text)
                                    showContextMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                },
                            )
                            val firstCode = codeBlocks.firstOrNull()
                            if (firstCode != null) {
                                DropdownMenuItem(
                                    text = { Text(t("Copy code", "کپی کد")) },
                                    onClick = {
                                        onCopyCode(firstCode)
                                        showContextMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(t("Share", "اشتراک‌گذاری")) },
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, message.text)
                                        type = "text/plain"
                                    }
                                    assistantContext.startActivity(
                                        Intent.createChooser(sendIntent, null),
                                    )
                                    showContextMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(t("Branch conversation", "شاخه‌زدن گفتگو")) },
                                onClick = {
                                    onBranch()
                                    showContextMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CallSplit, contentDescription = null)
                                },
                            )
                        }
                    }

                    // Feature #3: "Copy Code" button for messages with code blocks
                    if (codeBlocks.isNotEmpty()) {
                        codeBlocks.forEachIndexed { index, code ->
                            Row(
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { onCopyCode(code) },
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (codeBlocks.size > 1) {
                                            t("Copy Code ${index + 1}", "کپی کد ${index + 1}")
                                        } else {
                                            t("Copy Code", "کپی کد")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }

                    // Feature #5: Retry button on last assistant message (when not streaming/sending)
                    if (isLastAssistant && !isSending) {
                        Row(
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onRetry) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    t("Retry", "تلاش دوباره"),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }

        }

        is ChatMessage.ToolCall -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (message.isRunning) "◌" else "✓",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (message.isRunning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = message.toolName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (message.isRunning) {
                            Text(
                                text = t("Running...", "در حال اجرا..."),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        message.durationS?.let {
                            Text(
                                text = "${"%.1f".format(it)}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    // Feature 4.2: collapsible args
                    message.argsText?.takeIf { it.isNotBlank() }?.let { args ->
                        var argsExpanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { argsExpanded = !argsExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = t("Arguments", "آرگومان‌ها"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (argsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AnimatedVisibility(visible = argsExpanded) {
                            Text(
                                text = args,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Feature 4.1/4.3: collapsible result
                    message.resultText?.takeIf { it.isNotBlank() }?.let { result ->
                        val isLongResult = result.length > 300
                        var resultExpanded by remember { mutableStateOf(false) }
                        val displayResult = if (isLongResult && !resultExpanded) {
                            result.take(300) + "…"
                        } else {
                            result
                        }
                        HermesMarkdown(
                            markdown = displayResult,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (message.isRunning) MaterialTheme.colorScheme.outline
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        if (isLongResult) {
                            TextButton(
                                onClick = { resultExpanded = !resultExpanded },
                                modifier = Modifier.padding(top = 0.dp),
                            ) {
                                Icon(
                                    imageVector = if (resultExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (resultExpanded) t("Collapse", "جمع کردن") else t("Show more", "بیشتر"),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    message.error?.takeIf { it.isNotBlank() }?.let { err ->
                        Text(
                            text = "❌ ${err.take(220)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
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

        is ChatMessage.InteractiveRequest -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "❓ ${message.question}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (message.answered) {
                        Text(
                            text = t("Answered", "پاسخ داده شد"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    } else when (message.kind) {
                        InteractiveKind.CLARIFY -> if (message.choices != null) {
                            message.choices.forEach { choice ->
                                OutlinedButton(
                                    onClick = { onRespondToClarify(message.requestId, choice) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                ) { Text(choice) }
                            }
                        } else {
                            var answer by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = answer,
                                onValueChange = { answer = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(t("Type answer...", "جواب بنویسید...")) },
                            )
                            Button(
                                onClick = { onRespondToClarify(message.requestId, answer) },
                                enabled = answer.isNotBlank(),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 4.dp),
                            ) { Text(t("Send", "ارسال")) }
                        }
                        InteractiveKind.SUDO -> {
                            var password by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(t("Enter sudo password...", "رمز sudo بنویسید...")) },
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            Button(
                                onClick = { onRespondToSudo(message.requestId, password) },
                                enabled = password.isNotBlank(),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 4.dp),
                            ) { Text(t("Send", "ارسال")) }
                        }
                        InteractiveKind.SECRET -> {
                            var secretValue by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = secretValue,
                                onValueChange = { secretValue = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(message.question) },
                                placeholder = { Text(t("Enter value...", "مقدار را وارد کنید...")) },
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            Button(
                                onClick = { onRespondToSecret(message.requestId, secretValue) },
                                enabled = secretValue.isNotBlank(),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 4.dp),
                            ) { Text(t("Send", "ارسال")) }
                        }
                    }
                }
            }
        }

        is ChatMessage.SubagentCard -> {
            val isLongSubagent = message.text.length > 120
            var subagentExpanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isLongSubagent) { subagentExpanded = !subagentExpanded },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shape = RoundedCornerShape(12.dp),
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
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                        val displayText = if (isLongSubagent && !subagentExpanded) {
                            message.text.take(120) + "…"
                        } else {
                            message.text
                        }
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (isLongSubagent) {
                        Icon(
                            imageVector = if (subagentExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

// ── Feature #16: Search highlight helper ─────────────────────────────────


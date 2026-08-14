package com.hermes.android.ui.screen

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hermes.android.ui.component.ContentBlock
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.component.parseContentBlocks
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.screen.message.ToolCallGroup
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ChatViewModel
import com.hermes.android.ui.viewmodel.DrawerRenameState
import com.hermes.android.ui.viewmodel.InteractiveKind
import com.hermes.android.ui.viewmodel.PendingAttachment
import com.hermes.android.ui.viewmodel.SessionItem
import com.hermes.android.ui.viewmodel.SlashCommandSuggestion
import com.hermes.android.ui.viewmodel.TodoItemUi
import com.hermes.android.ui.viewmodel.TodoStatus
import kotlinx.coroutines.launch

@Composable
internal fun ThinkingBlock(
    reasoning: String,
    isStreaming: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val reduceMotion = rememberReduceMotion()

    // A full-amplitude, fast pulse sustained for the whole length of a long
    // agent turn is fatiguing to stare at — ease to a slower, shallower
    // pulse once thinking has been running a while instead of holding full
    // intensity indefinitely.
    var streamingSeconds by remember(isStreaming) { mutableStateOf(0) }
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                streamingSeconds++
            }
        }
    }
    val isLongTurn = streamingSeconds > 20

    val transition = rememberInfiniteTransition(label = "thinking")
    val pulse by transition.animateFloat(
        initialValue = if (isLongTurn) 0.55f else 0.35f,
        targetValue = if (isLongTurn) 0.85f else 1f,
        animationSpec = infiniteRepeatable(
            tween(if (isLongTurn) 1600 else 900),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val barAlpha = when {
        !isStreaming -> 0.4f
        reduceMotion -> 0.7f
        else -> pulse
    }

    // Emotive markers the model emits inside its reasoning (😌 🤔 😅 …) become
    // a big "sticker" beside the thinking state — the agent's mood, live.
    // Scan only a bounded tail: reasoning grows by hundreds of tokens per
    // turn and this re-runs on every buffered flush, so a full-string
    // findAll was O(n²) across the turn — enough to visibly stutter long
    // thinking phases on a phone.
    val emojiRe = remember { Regex("[\\uD83C-\\uDBFF][\\uDC00-\\uDFFF]|[\\u2600-\\u27BF\\u2B00-\\u2BFF]") }
    val sticker = remember(reasoning) {
        emojiRe.findAll(reasoning.takeLast(400)).map { it.value }.lastOrNull()
    }

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
                text = if (isStreaming) t("‌", "‌") else t("Thoughts", "افکار"),
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
                // Bounded tail for the same O(n²) reason as the sticker scan.
                reasoning.takeLast(400).trim().lines().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
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
    avatarUri: String? = null,
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
    onDownloadFile: (url: String, name: String) -> Unit = { _, _ -> },
    onToggleToolGroup: (String) -> Unit = {},
    toolGroups: List<ToolCallGroup> = emptyList(),
    onToggleTool: (String) -> Unit = {},
) {
    when (message) {
        is ChatMessage.User -> {
            UserMessageBubble(
                message = message,
                searchQuery = searchQuery,
                isLastInGroup = isLastInGroup,
                onCopyMessage = onCopyMessage,
            )
        }

        is ChatMessage.Assistant -> {
            AssistantMessageBubble(
                message = message,
                searchQuery = searchQuery,
                isLastAssistant = isLastAssistant,
                isSending = isSending,
                toolGroups = toolGroups,
                onCopyMessage = onCopyMessage,
                onCopyCode = onCopyCode,
                onRetry = onRetry,
                onImageClick = onImageClick,
                resolveUrl = resolveUrl,
                onBranch = onBranch,
                onDownloadFile = onDownloadFile,
            )
        }

        is ChatMessage.ToolCall -> {
            // Tool calls are shown in the assistant's combined timeline;
            // skip separate rendering to avoid duplication.
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
            // Needs the user's action, so a stronger tint than the passive
            // ToolCall status card — but still tint+border, not a solid
            // fill, to stay consistent with the rest of the document-style
            // chat.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
                    .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "❓ ${message.question}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (message.answered) {
                        Text(
                            text = t("Answered", "پاسخ داده شد"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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


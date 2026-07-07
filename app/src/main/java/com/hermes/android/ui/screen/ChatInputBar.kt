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
import androidx.compose.material.icons.filled.Psychology
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
internal fun InputBar(
    text: String,
    isSending: Boolean,
    isAttaching: Boolean = false,
    pendingAttachments: List<PendingAttachment> = emptyList(),
    slashCommands: List<SlashCommandSuggestion>,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onSteer: () -> Unit = {},
    onAttachFile: (Uri) -> Unit = {},
    onRemoveAttachment: (PendingAttachment) -> Unit = {},
    reasoningLevel: String = "medium",
    onReasoningLevelChange: (String) -> Unit = {},
) {
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(onAttachFile) }
    // Feature 5.2: slash command suggestions — from the gateway catalog
    // (commands.catalog); falls back to a minimal built-in list if empty.
    val fallbackCommands = remember {
        listOf("/help", "/clear", "/config", "/model", "/session")
            .map { SlashCommandSuggestion(it, "") }
    }
    val commandList = slashCommands.ifEmpty { fallbackCommands }
    val showSuggestions = text.startsWith("/") && !isSending
    val suggestions = remember(text, commandList) {
        if (text == "/") commandList
        else commandList.filter { it.command.startsWith(text) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        if (showSuggestions && suggestions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestions) { cmd ->
                    SuggestionChip(
                        onClick = { onTextChange(cmd.command) },
                        label = { Text(cmd.command) },
                    )
                }
            }
        }
        // Staged attachments — already uploaded to the gateway, sent with the
        // next prompt. Tap a chip to remove it.
        if (pendingAttachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pendingAttachments) { attachment ->
                    SuggestionChip(
                        onClick = { onRemoveAttachment(attachment) },
                        icon = {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = { Text("${attachment.name}  ✕", maxLines = 1) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Reasoning-effort quick switch (agent.reasoning_effort) — same
            // control ChatGPT/Gemini put next to the composer, instead of
            // burying it in Settings only.
            var reasoningMenuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { reasoningMenuOpen = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = t("Reasoning effort", "سطح استدلال"),
                        tint = if (reasoningLevel != "none" && reasoningLevel != "medium") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                DropdownMenu(
                    expanded = reasoningMenuOpen,
                    onDismissRequest = { reasoningMenuOpen = false },
                ) {
                    reasoningLevels.forEach { level ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    reasoningLevelLabel(level),
                                    fontWeight = if (level == reasoningLevel) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                onReasoningLevelChange(level)
                                reasoningMenuOpen = false
                            },
                        )
                    }
                }
            }
            // Feature 5.1: attachment button — picks a file and uploads it to
            // the gateway session over the loopback WebSocket.
            IconButton(
                onClick = { if (!isAttaching) filePicker.launch("*/*") },
                enabled = !isAttaching,
                modifier = Modifier.size(48.dp),
            ) {
                if (isAttaching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = t("Attach", "پیوست"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(t("Type a message...", "پیام بنویس...")) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
            )
            if (isSending) {
                // Mid-turn the agent is running. Two DIFFERENT things can be
                // meant by "send while it's replying", and neither replaces
                // the other:
                //  - Steer (session.steer): folds a note into the CURRENT
                //    turn without interrupting it — but verified against
                //    Hermes' own docs, the text only actually lands "after
                //    the next tool call" (appended to a tool result). For a
                //    turn with no tool calls (a plain text answer), it just
                //    queues and never gets delivered — steer alone can look
                //    completely broken for ordinary chatty replies.
                //  - A normal Send: submits as a new prompt. The gateway's
                //    prompt.submit handler explicitly does NOT reject this
                //    mid-turn — it queues it and interrupts the live turn
                //    (_handle_busy_submit), i.e. exactly "send a message
                //    that cuts in", which is what most users expect from a
                //    chat app. This used to be unreachable: the button was
                //    swapped out entirely while isSending.
                // Stop (full interrupt, no follow-up) stays available too.
                if (text.isNotBlank()) {
                    IconButton(
                        onClick = onSteer,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.CallSplit,
                            contentDescription = t("Steer the agent", "هدایت عامل"),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = t("Send now (interrupts current reply)", "ارسال الان (پاسخ فعلی رو قطع می‌کنه)"),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = t("Stop", "توقف"),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    // An attachment-only message (no typed text) is valid —
                    // sendMessage() already handles it — so the button must
                    // not be gated on text alone, or a picked file/image can
                    // never actually be sent.
                    enabled = text.isNotBlank() || pendingAttachments.isNotEmpty(),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = t("Send", "ارسال"),
                    )
                }
            }
        }
    }
}

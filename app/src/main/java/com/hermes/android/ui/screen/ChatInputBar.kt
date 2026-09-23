package com.hermes.android.ui.screen

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.speech.RecognizerIntent
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
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
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.component.ContentBlock
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.component.parseContentBlocks
import com.hermes.android.ui.design.hxSoftShadow
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ChatViewModel
import com.hermes.android.ui.viewmodel.DrawerRenameState
import com.hermes.android.ui.viewmodel.InteractiveKind
import com.hermes.android.ui.viewmodel.PendingAttachment
import com.hermes.android.ui.viewmodel.SessionItem
import com.hermes.android.ui.viewmodel.ModelOption
import com.hermes.android.ui.viewmodel.SlashCommandSuggestion
import com.hermes.android.ui.i18n.SlashCommandDescriptions
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
    activeModel: String? = null,
    onModelClick: () -> Unit = {},
) {
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(onAttachFile) }
    // Voice input: Android's own recognizer (the system's speech dialog), so no
    // microphone permission here. No language is asked for: the recognizer uses the
    // one the user set on the phone. What was said lands in the box to check before sending.
    val context = LocalContext.current
    val speechPrompt = t("Speak", "صحبت کن")
    val noSpeech = t("Speech recognition isn't available on this phone", "تشخیص گفتار روی این گوشی در دسترس نیست")
    val voiceInput = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrEmpty()) {
            onTextChange(if (text.isBlank()) spoken else text.trimEnd() + " " + spoken)
        }
    }
    val startVoiceInput = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, speechPrompt)
        try {
            voiceInput.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, noSpeech, Toast.LENGTH_LONG).show()
        }
    }
    // `/` list: the gateway catalog (commands.catalog) minus the commands the app has
    // a button for; a short built-in list until the catalog arrives.
    val fallbackCommands = remember {
        listOf(
            "/help" to "Show available commands",
            "/btw" to "Ask a side question without interrupting",
            "/bg" to "Run a prompt in a separate background session",
            "/goal" to "Set a standing goal Hermes works on across turns",
            "/status" to "Show session, model, token, and context info",
        ).map { (command, description) -> SlashCommandSuggestion(command, description) }
    }
    val commandList = slashCommands.ifEmpty { fallbackCommands }
    // Hermes describes its commands in English only; Persian comes from the app.
    val persianDescriptions = t("en", "fa") == "fa"
    // Only while the command name is being typed: past the first space it is the argument.
    val showSuggestions = text.startsWith("/") && text.none { it == ' ' || it == '\n' } && !isSending
    val suggestions = remember(text, commandList) {
        commandList.distinctBy { it.command }.filter { it.command.startsWith(text, ignoreCase = true) }
    }

    // Scaffold's own content padding (in ChatScreen.kt) already reserves
    // the navigation-bar inset — adding navigationBarsPadding() again here
    // double-counted it, leaving a dead gap between the pill and the true
    // bottom edge. imePadding() stays: that inset is NOT part of Scaffold's
    // systemBars content window insets.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        if (showSuggestions && suggestions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                items(suggestions, key = { it.command }) { cmd ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTextChange(cmd.command + " ") }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            cmd.command,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (cmd.description.isNotBlank()) {
                            Text(
                                SlashCommandDescriptions.describe(cmd.command, cmd.description, persianDescriptions),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
        // Claude/ChatGPT-style composer: one rounded surface, the text on top
        // and an action row under it — attach and the model button on the
        // start side, send/steer/stop on the end. Model and effort live in the
        // model button's sheet; the button stays quiet so the conversation,
        // not the controls, carries the visual weight.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .hxSoftShadow(radius = 10.dp, shape = RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(t("Ask Hermes", "از هرمس بپرس")) },
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { filePicker.launch("*/*") },
                    enabled = !isAttaching,
                    modifier = Modifier.size(40.dp),
                ) {
                    if (isAttaching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = t("Attach file", "پیوست فایل"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ComposerModelButton(activeModel = activeModel, onClick = onModelClick)
                Spacer(Modifier.weight(1f))
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
                                HxIcons.GitBranch,
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
                        onClick = startVoiceInput,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = t("Voice input", "ورودی صوتی"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
}

package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.SlashCommandSuggestion

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
                // Mid-turn the agent is running. Typing a message and tapping
                // the steer button redirects it without interrupting
                // (session.steer) — the desktop/TUI "course-correct" control.
                // Stop (full interrupt) stays available alongside it.
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

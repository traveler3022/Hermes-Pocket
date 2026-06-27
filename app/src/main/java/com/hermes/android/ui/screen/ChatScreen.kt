package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ChatViewModel

/**
 * Main Chat screen.
 *
 * Depends ONLY on [ChatViewModel] — never on gateway or runtime packages
 * (Phase 1.5 Rule 1: Strict Layer Dependency).
 *
 * Features (Step 4):
 * - Message list with user/assistant/tool messages
 * - Streaming text appearance
 * - Tool call cards
 * - Slash command input
 * - Stop button (interrupt)
 * - Session drawer
 * - New conversation button
 *
 * Reference: migration-spec-v1.0, docs/06-migration-order/01-roadmap.md Step 4
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSessions: () -> Unit = {},
    onNavigateToRuntime: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Show error snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable { onNavigateToRuntime() }
                    ) {
                        Text("Hermes")
                        ConnectionIndicator(uiState.connectionState)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.toggleSessionDrawer() }) {
                        Icon(Icons.Default.Menu, contentDescription = "Sessions")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSessions) {
                        Icon(Icons.Default.History, contentDescription = "Sessions")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { viewModel.newConversation() }) {
                        Icon(Icons.Default.Add, contentDescription = "New conversation")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }

            // Sending progress bar
            if (uiState.isSending) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                )
            }

            // Input bar
            InputBar(
                text = uiState.inputText,
                isSending = uiState.isSending,
                onTextChange = viewModel::updateInputText,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopGeneration,
            )
        }
    }
}

@Composable
private fun ConnectionIndicator(state: ChatConnectionState) {
    val (color, label) = when (state) {
        ChatConnectionState.Connected -> MaterialTheme.colorScheme.primary to "● Connected"
        ChatConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary to "◌ Connecting…"
        ChatConnectionState.Reconnecting -> MaterialTheme.colorScheme.tertiary to "↻ Reconnecting…"
        ChatConnectionState.Disconnected -> MaterialTheme.colorScheme.outline to "○ Tap to Connect"
        ChatConnectionState.Failed -> MaterialTheme.colorScheme.error to "✕ Termux Error"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    when (message) {
        is ChatMessage.User -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 320.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        is ChatMessage.Assistant -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 320.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (message.reasoning != null && message.reasoning.isNotEmpty()) {
                            Text(
                                text = "💭 ${message.reasoning}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Text(
                            text = message.text.ifEmpty { "…" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // Fix S4F01: Render assistant messages as Markdown
                        if (message.text.isNotEmpty()) {
                            dev.jeziellago.compose.markdowntext.MarkdownText(
                                markdown = message.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
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
            }
        }

        is ChatMessage.ToolCall -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "🔧 ${message.toolName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        if (message.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        message.durationS?.let {
                            Text(
                                text = "${"%.1f".format(it)}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    message.argsText?.let { args ->
                        if (args.isNotBlank()) {
                            Text(
                                text = args,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    message.resultText?.let { result ->
                        if (result.isNotBlank()) {
                            Text(
                                text = result.take(500) + if (result.length > 500) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    message.error?.let { err ->
                        if (err.isNotBlank()) {
                            Text(
                                text = "❌ $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
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
    }
}

@Composable
private fun InputBar(
    text: String,
    isSending: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a message…") },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
        )

        if (isSending) {
            IconButton(
                onClick = onStop,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank(),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                )
            }
        }
    }
}

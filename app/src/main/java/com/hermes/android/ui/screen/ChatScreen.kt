package com.hermes.android.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ChatViewModel
import com.hermes.android.ui.viewmodel.InteractiveKind
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.TextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.component.ContentBlock
import com.hermes.android.ui.component.parseContentBlocks
import com.hermes.android.ui.viewmodel.DrawerRenameState
import com.hermes.android.ui.viewmodel.PendingAttachment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import com.hermes.android.ui.viewmodel.SessionItem
import com.hermes.android.ui.viewmodel.SlashCommandSuggestion
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

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
 * - Copy message (long-press) [#2]
 * - Code block copy button [#3]
 * - Scroll-to-bottom FAB [#4]
 * - Retry / Regenerate [#5]
 * - Better connection error retry [#7]
 * - Quick model switch from chat [#8]
 * - Search in current chat [#16]
 * - Save draft message [#23]
 * - Better session drawer with relative time [#26]
 * - Loading skeleton / shimmer [#32]
 *
 * Reference: migration-spec-v1.0, docs/06-migration-order/01-roadmap.md Step 4
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSessions: () -> Unit = {},
    onNavigateToRuntime: () -> Unit = {},
    sharedText: String? = null,
    resumeSessionId: String? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val notification by viewModel.notification.collectAsStateWithLifecycle()
    val slashCommands by viewModel.slashCommands.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    // Feature #4: Detect if user has scrolled away from bottom
    val showScrollToBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.totalItemsCount == 0) false
            else {
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                lastVisibleItem == null || lastVisibleItem.index < layoutInfo.totalItemsCount - 1
            }
        }
    }

    // Feature #16: Filter messages based on search query
    val filteredMessages = remember(uiState.messages, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.messages
        } else {
            val query = uiState.searchQuery.lowercase()
            uiState.messages.filter { msg ->
                when (msg) {
                    is ChatMessage.User -> msg.text.lowercase().contains(query)
                    is ChatMessage.Assistant -> msg.text.lowercase().contains(query)
                    is ChatMessage.ToolCall -> (msg.toolName.lowercase().contains(query) ||
                            msg.argsText?.lowercase()?.contains(query) == true ||
                            msg.resultText?.lowercase()?.contains(query) == true)
                    is ChatMessage.Status -> msg.text.lowercase().contains(query)
                    is ChatMessage.InteractiveRequest -> msg.question.lowercase().contains(query)
                    is ChatMessage.SubagentCard -> msg.text.lowercase().contains(query)
                }
            }
        }
    }

    // Keep drawer state in sync with ViewModel state.
    LaunchedEffect(uiState.showSessionDrawer) {
        if (uiState.showSessionDrawer) drawerState.open() else drawerState.close()
    }

    // Auto-scroll to bottom when new messages arrive — only if user hasn't scrolled up
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty() && !showScrollToBottom) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Jump to last message whenever a session is loaded/resumed
    LaunchedEffect(uiState.sessionLoadedAt) {
        if (uiState.sessionLoadedAt > 0L && uiState.messages.isNotEmpty()) {
            listState.scrollToItem(uiState.messages.size - 1)
        }
    }

    // Show error snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Feature #23: Save draft when input changes (debounced via LaunchedEffect)
    LaunchedEffect(uiState.inputText) {
        if (uiState.inputText.isNotEmpty()) {
            kotlinx.coroutines.delay(500L)
            viewModel.saveDraft()
        }
    }

    // Pre-fill input from share intent
    LaunchedEffect(resumeSessionId) {
        if (!resumeSessionId.isNullOrBlank()) {
            viewModel.resumeSession(resumeSessionId)
        }
    }

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            viewModel.updateInputText(sharedText)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                // ── Header ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = t("Hermes", "هرمس"),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = { scope.launch { drawerState.close() } }) {
                        Icon(Icons.Default.Close, contentDescription = t("Close", "بستن"))
                    }
                }
                Button(
                    onClick = {
                        viewModel.newConversation()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(t("New conversation", "گفتگوی جدید"))
                }
                // ── Drawer search + sort bar ───────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.drawerSearchQuery,
                        onValueChange = { viewModel.updateDrawerSearch(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                t("Search chats…", "جستجو در گفتگوها…"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            if (uiState.drawerSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateDrawerSearch("") }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = t("Clear", "پاک کردن"),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    IconButton(onClick = { viewModel.toggleDrawerSort() }) {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = if (uiState.drawerSortNewest)
                                t("Newest first", "جدیدترین اول")
                            else
                                t("Oldest first", "قدیمی‌ترین اول"),
                            tint = if (!uiState.drawerSortNewest)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ── Session list (fills remaining space) ───────────────────
                val drawerSessions = remember(
                    uiState.sessions,
                    uiState.drawerSearchQuery,
                    uiState.drawerSortNewest,
                    uiState.drawerPinnedIds,
                ) {
                    var list = uiState.sessions
                    val q = uiState.drawerSearchQuery.trim().lowercase()
                    if (q.isNotEmpty()) {
                        list = list.filter { it.title.lowercase().contains(q) }
                    }
                    list = if (uiState.drawerSortNewest) {
                        list.sortedByDescending { it.updatedAt }
                    } else {
                        list.sortedBy { it.updatedAt }
                    }
                    // pinned items float to top
                    val pinned = list.filter { it.id in uiState.drawerPinnedIds }
                    val unpinned = list.filter { it.id !in uiState.drawerPinnedIds }
                    pinned + unpinned
                }

                if (drawerSessions.isEmpty()) {
                    Text(
                        text = if (uiState.drawerSearchQuery.isNotEmpty())
                            t("No results", "نتیجه‌ای یافت نشد")
                        else
                            t("No saved sessions yet", "هنوز گفتگویی ذخیره نشده"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(drawerSessions, key = { it.id }) { session ->
                            SessionDrawerRow(
                                session = session,
                                isActive = session.id == uiState.activeSessionId,
                                isPinned = session.id in uiState.drawerPinnedIds,
                                onClick = {
                                    viewModel.resumeSession(session.id)
                                    scope.launch { drawerState.close() }
                                },
                                onLongClick = {
                                    viewModel.drawerShowRename(session.id, session.title)
                                },
                                onPin = { viewModel.drawerTogglePin(session.id) },
                                onRename = { viewModel.drawerShowRename(session.id, session.title) },
                                onDelete = { viewModel.drawerShowDelete(session.id) },
                            )
                        }
                    }
                }

                // ── Rename dialog ──────────────────────────────────────────
                uiState.drawerRenameTarget?.let { rename ->
                    AlertDialog(
                        onDismissRequest = { viewModel.drawerHideRename() },
                        title = { Text(t("Rename chat", "تغییر نام گفتگو")) },
                        text = {
                            OutlinedTextField(
                                value = rename.inputText,
                                onValueChange = { viewModel.drawerUpdateRenameText(it) },
                                singleLine = true,
                                placeholder = { Text(rename.currentTitle) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            Button(onClick = { viewModel.drawerConfirmRename() }) {
                                Text(t("Save", "ذخیره"))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.drawerHideRename() }) {
                                Text(t("Cancel", "لغو"))
                            }
                        },
                    )
                }

                // ── Delete confirm dialog ──────────────────────────────────
                if (uiState.drawerDeleteTarget != null) {
                    val targetSession = uiState.sessions.find { it.id == uiState.drawerDeleteTarget }
                    AlertDialog(
                        onDismissRequest = { viewModel.drawerHideDelete() },
                        title = { Text(t("Delete chat?", "حذف گفتگو؟")) },
                        text = {
                            Text(
                                t(
                                    "\"${targetSession?.title ?: "This chat"}\" will be permanently deleted.",
                                    "گفتگوی \"${targetSession?.title ?: "این گفتگو"}\" برای همیشه حذف می‌شود.",
                                )
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.drawerConfirmDelete() },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text(t("Delete", "حذف"))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.drawerHideDelete() }) {
                                Text(t("Cancel", "لغو"))
                            }
                        },
                    )
                }

                // ── Footer: Settings ───────────────────────────────────────
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { drawerState.close() }
                            onNavigateToSettings()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = t("Settings", "تنظیمات"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.clickable { onNavigateToRuntime() }
                            ) {
                                Text(t("Hermes", "هرمس"))
                                ConnectionIndicator(uiState.connectionState)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.toggleSessionDrawer() }) {
                                Icon(Icons.Default.Menu, contentDescription = t("Sessions", "گفتگوها"))
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.toggleSearch() }) {
                                Icon(
                                    if (uiState.showSearch) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = t("Search", "جستجو"),
                                )
                            }
                        },
                    )
                    // Feature #16: Search bar (below TopAppBar)
                    AnimatedVisibility(
                        visible = uiState.showSearch,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut(),
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            placeholder = { Text(t("Search messages...", "جستجو در پیام‌ها...")) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = t("Clear", "پاک کردن"))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // Feature #4: Scroll-to-bottom FAB
            floatingActionButton = {
                AnimatedVisibility(
                    visible = showScrollToBottom,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (uiState.messages.isNotEmpty()) {
                                    listState.animateScrollToItem(uiState.messages.size - 1)
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = t("Scroll to bottom", "رفتن به انتها"),
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                // Feature #7: Connection error retry banner
                if (uiState.connectionState == ChatConnectionState.Failed ||
                    uiState.connectionState == ChatConnectionState.Disconnected
                ) {
                    ConnectionRetryBanner(
                        state = uiState.connectionState,
                        onRetry = { viewModel.retryConnection() },
                    )
                }

                // Feature #32: Shimmer skeleton when connecting
                if (uiState.connectionState == ChatConnectionState.Connecting && uiState.messages.isEmpty()) {
                    ShimmerSkeleton()
                } else {
                    // Notification banner
                    notification?.let { notif ->
                        Surface(
                            color = when (notif.level) {
                                "error" -> MaterialTheme.colorScheme.errorContainer
                                "warn" -> MaterialTheme.colorScheme.tertiaryContainer
                                "success" -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = notif.text ?: "",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    // Message list
                    val copiedToast = t("Copied", "کپی شد")
                    val codeCopiedToast = t("Code copied", "کد کپی شد")
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                    ) {
                        items(filteredMessages, key = { it.id }) { message ->
                            val isLastAssistant = message is ChatMessage.Assistant &&
                                    !message.isStreaming &&
                                    filteredMessages.lastOrNull { it is ChatMessage.Assistant } == message

                            MessageBubble(
                                message = message,
                                searchQuery = uiState.searchQuery,
                                isLastAssistant = isLastAssistant,
                                isSending = uiState.isSending,
                                onCopyMessage = { text ->
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                                },
                                onCopyCode = { code ->
                                    clipboardManager.setText(AnnotatedString(code))
                                    Toast.makeText(context, codeCopiedToast, Toast.LENGTH_SHORT).show()
                                },
                                onRetry = { viewModel.retryLastMessage() },
                                onRespondToClarify = viewModel::respondToClarify,
                                onRespondToSudo = viewModel::respondToSudo,
                                onRespondToSecret = viewModel::respondToSecret,
                                onImageClick = { url -> fullscreenImageUrl = url },
                                resolveUrl = viewModel::resolveMediaUrl,
                            )
                        }
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
                    isAttaching = uiState.isAttaching,
                    pendingAttachments = uiState.pendingAttachments,
                    slashCommands = slashCommands,
                    onTextChange = viewModel::updateInputText,
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::stopGeneration,
                    onAttachFile = viewModel::attachFromUri,
                    onRemoveAttachment = viewModel::removeAttachment,
                )
            }
        }
    }

    // Fullscreen image viewer — rendered at ChatScreen level (outside LazyColumn) to avoid BadTokenException
    fullscreenImageUrl?.let { imageUrl ->
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f))
                    .clickable { fullscreenImageUrl = null },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
                IconButton(
                    onClick = { fullscreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = t("Close", "بستن"),
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                }
                IconButton(
                    onClick = { saveImageToDownloads(context, imageUrl, "") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = t("Save image", "ذخیره تصویر"),
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
        }
    }
}

// ── Feature #26: Relative time formatting ────────────────────────────────

/**
 * Formats a timestamp as relative time, with bilingual support.
 */
@Composable
private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    val diffSeconds = diffMs / 1000
    val diffMinutes = diffSeconds / 60
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMs < 0 || diffMinutes < 1 -> t("Just now", "همین الان")
        diffMinutes < 60 -> {
            val m = diffMinutes.toInt()
            t("$m min ago", "$m دقیقه پیش")
        }
        diffHours < 24 -> {
            val h = diffHours.toInt()
            if (h == 1) t("1 hour ago", "۱ ساعت پیش")
            else t("$h hours ago", "$h ساعت پیش")
        }
        diffDays < 2 -> t("Yesterday", "دیروز")
        diffDays < 7 -> {
            val d = diffDays.toInt()
            t("$d days ago", "$d روز پیش")
        }
        diffDays < 30 -> {
            val w = (diffDays / 7).toInt()
            if (w == 1) t("1 week ago", "۱ هفته پیش")
            else t("$w weeks ago", "$w هفته پیش")
        }
        else -> {
            val months = (diffDays / 30).toInt()
            if (months == 1) t("1 month ago", "۱ ماه پیش")
            else t("$months months ago", "$months ماه پیش")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDrawerRow(
    session: SessionItem,
    isActive: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val relativeTime = formatRelativeTime(session.updatedAt)
    val messageCountText = session.messageCount?.let { count ->
        t("$count messages", "$count پیام")
    }
    val subtitle = buildString {
        if (isPinned) append("📌 ")
        if (messageCountText != null) {
            append(messageCountText)
            append(" · ")
        }
        append(relativeTime)
    }

    var showMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                session.lastMessagePreview?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it.take(80),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (isPinned) t("Unpin", "برداشتن سنجاق")
                        else t("Pin", "سنجاق کردن")
                    )
                },
                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onPin()
                },
            )
            DropdownMenuItem(
                text = { Text(t("Rename", "تغییر نام")) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        t("Delete", "حذف"),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun ConnectionIndicator(state: ChatConnectionState) {
    val (color, label) = when (state) {
        ChatConnectionState.Connected -> MaterialTheme.colorScheme.primary to t("● Connected", "● متصل")
        ChatConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary to t("◌ Connecting...", "◌ در حال اتصال...")
        ChatConnectionState.Reconnecting -> MaterialTheme.colorScheme.tertiary to t("↻ Reconnecting...", "↻ اتصال دوباره...")
        ChatConnectionState.Disconnected -> MaterialTheme.colorScheme.outline to t("○ Tap to Connect", "○ برای اتصال لمس کنید")
        ChatConnectionState.Failed -> MaterialTheme.colorScheme.error to t("✕ Termux Error", "✕ خطای ترموکس")
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

// ── Feature #7: Connection retry banner ──────────────────────────────────

@Composable
private fun ConnectionRetryBanner(
    state: ChatConnectionState,
    onRetry: () -> Unit,
) {
    val (title, subtitle) = when (state) {
        ChatConnectionState.Failed -> t("Connection Failed", "اتصال ناموفق") to
                t("Could not connect to Hermes gateway", "اتصال به گیت‌وی هرمس ممکن نیست")
        ChatConnectionState.Disconnected -> t("Disconnected", "قطع شده") to
                t("Not connected to Hermes gateway", "به گیت‌وی هرمس متصل نیست")
        else -> return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(t("Reconnect", "اتصال دوباره"))
            }
        }
    }
}

// ── Feature #32: Shimmer skeleton ────────────────────────────────────────

@Composable
private fun ShimmerSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_progress",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        start = Offset(shimmerProgress - 300f, 0f),
        end = Offset(shimmerProgress, 0f),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Fake user message (right-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .height(48.dp)
                    .background(shimmerBrush, RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)),
            )
        }
        // Fake assistant message (left-aligned, tall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .height(80.dp)
                    .background(shimmerBrush, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)),
            )
        }
        // Fake user message
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .height(40.dp)
                    .background(shimmerBrush, RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)),
            )
        }
        // Fake assistant message (left-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 350.dp)
                    .height(100.dp)
                    .background(shimmerBrush, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)),
            )
        }
    }
}

// ── Regex for code block detection ───────────────────────────────────────

private val codeBlockRegex = Regex("```[\\s\\S]*?```", RegexOption.MULTILINE)

/** Save a remote file URL to the Downloads/Hermes folder via DownloadManager. */
private fun saveImageToDownloads(context: Context, url: String, alt: String) {
    val filename = alt.ifBlank { url.substringAfterLast('/').substringBefore('?') }
        .ifBlank { "hermes_image.jpg" }
        .let { if (!it.contains('.')) "$it.jpg" else it }
    val request = DownloadManager.Request(Uri.parse(url))
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Hermes/$filename")
        .setTitle(filename)
        .setDescription("در حال دانلود از هرمس")
        .setAllowedOverMetered(true)
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
}

/** Open a URL in whatever external app handles it (browser, video player…). */
private fun openUrlExternally(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

// ── Content block renderers ──────────────────────────────────────────────

@Composable
private fun InlineImageBlock(
    alt: String,
    url: String,
    onImageClick: (String) -> Unit,
    onSave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
    ) {
        AsyncImage(
            model = url,
            contentDescription = alt.ifBlank { "تصویر" },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onImageClick(url) },
            contentScale = ContentScale.Fit,
        )
        IconButton(
            onClick = onSave,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    CircleShape,
                ),
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = t("Save image", "ذخیره تصویر"),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    onCopyCode: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onCopyCode(code) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = t("Copy code", "کپی کد"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun MermaidBlockCard(
    code: String,
    onCopyCode: (String) -> Unit,
) {
    // Rendered in a WebView with mermaid.js (CDN). Falls back visually to the
    // code card header so the user can still copy the diagram source.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "mermaid",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onCopyCode(code) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = t("Copy code", "کپی کد"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val html = remember(code) {
                val escaped = code
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
                <style>body{margin:0;background:transparent;display:flex;justify-content:center}</style>
                </head><body><pre class="mermaid">$escaped</pre>
                <script>mermaid.initialize({startOnLoad:true,securityLevel:'loose'});</script>
                </body></html>"""
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "utf-8", null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
        }
    }
}

@Composable
private fun HtmlBlockCard(
    url: String,
    name: String,
    onOpenExternal: () -> Unit,
) {
    // HTML is renderable in-app: show it inline in a WebView, with a button to
    // pop out to a full browser for interaction-heavy pages.
    var expanded by remember { mutableStateOf(true) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌐", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = t("Toggle preview", "نمایش/بستن"),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onOpenExternal, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = t("Open in browser", "باز کردن در مرورگر"),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { it.loadUrl(url) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}

@Composable
private fun ArtifactCard(
    emoji: String,
    name: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDownload: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = emoji, style = MaterialTheme.typography.titleMedium)
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAction) { Text(actionLabel) }
            if (onDownload != null) {
                IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = t("Download", "دانلود"),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Extract all code block contents from a markdown text.
 */
private fun extractCodeBlocks(text: String): List<String> {
    return codeBlockRegex.findAll(text).map { match ->
        // Strip the ``` markers and optional language tag
        val raw = match.value
        val lines = raw.lines()
        if (lines.size <= 2) {
            // Only opening/closing ``` with no content
            ""
        } else {
            // Drop first line (```lang) and last line (```)
            lines.drop(1).dropLast(1).joinToString("\n")
        }
    }.filter { it.isNotBlank() }.toList()
}

// ── Message Bubble ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
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
            val emotionRegex = remember { Regex("[\\p{So}\\p{Sk}]|[\uD83C-\uDBFF][\uDC00-\uDFFF]|\\([^()]{1,12}\\)") }
            val emotions = remember(message.reasoning) {
                message.reasoning?.let { text ->
                    emotionRegex.findAll(text).map { m -> m.value }.filter { v ->
                        v.length == 1 || v.any { c -> !c.isLetterOrDigit() && c != '(' && c != ')' && c != ' ' }
                    }.toList()
                } ?: emptyList()
            }

            // Feature 7.1: animated dots only while streaming with reasoning
            val dotStr = if (message.isStreaming && hasThinking) thinkingDotStr() else ""

            val assistantContext = LocalContext.current
            val codeBlocks = remember(message.text) { extractCodeBlocks(message.text) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isThinkingExpanded = !isThinkingExpanded }
                                        .padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "💭",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (emotions.isNotEmpty() && !isThinkingExpanded) {
                                        Text(
                                            text = emotions.distinct().take(5).joinToString(" "),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Text(
                                        text = when {
                                            message.isStreaming && hasThinking && !isThinkingExpanded ->
                                                "💭$dotStr"
                                            isThinkingExpanded -> t("Thinking ▲", "فکر کردن ▲")
                                            else -> t("Thinking ▼", "فکر کردن ▼")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                                AnimatedVisibility(visible = isThinkingExpanded) {
                                    // Feature 7.2: HermesMarkdown for reasoning
                                    HermesMarkdown(
                                        markdown = message.reasoning ?: "",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                    )
                                }
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

@Composable
private fun highlightText(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val highlightBg = MaterialTheme.colorScheme.tertiaryContainer
    return buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var start = 0
        var matchIndex = lowerText.indexOf(lowerQuery, start)
        while (matchIndex >= 0) {
            // Append text before match
            append(text.substring(start, matchIndex))
            // Append highlighted match
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold, background = highlightBg)) {
                append(text.substring(matchIndex, matchIndex + query.length))
            }
            start = matchIndex + query.length
            matchIndex = lowerText.indexOf(lowerQuery, start)
        }
        // Append remaining text
        if (start < text.length) {
            append(text.substring(start))
        }
    }
}

@Composable
private fun thinkingDotStr(): String {
    val transition = rememberInfiniteTransition(label = "thinking_dots")
    // InfiniteTransition exposes animateFloat (not animateInt); animate 0f..4f
    // and floor to an int step.
    val rawStep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dots",
    )
    return when (rawStep.toInt() % 4) { 0 -> ""; 1 -> "."; 2 -> ".."; else -> "..." }
}

@Composable
private fun InputBar(
    text: String,
    isSending: Boolean,
    isAttaching: Boolean = false,
    pendingAttachments: List<PendingAttachment> = emptyList(),
    slashCommands: List<SlashCommandSuggestion>,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
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
                    enabled = text.isNotBlank(),
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

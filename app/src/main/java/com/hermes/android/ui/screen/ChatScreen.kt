package com.hermes.android.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ChatViewModel
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

// Extracted composables (same package, no import needed):
// - ChatConnection.kt: ConnectionIndicator, ConnectionRetryBanner, ShimmerSkeleton
// - ChatContentBlocks.kt: InlineImageBlock, CodeBlockCard, MermaidBlockCard, HtmlBlockCard, ArtifactCard, extractCodeBlocks
// - ChatSessionDrawer.kt: SessionDrawerRow, AgentTodoCard
// - ChatMessages.kt: ThinkingBlock, MessageBubble
// - ChatInputBar.kt: InputBar
// - ChatUtils.kt: formatRelativeTime, highlightText, thinkingDotStr


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
    var showRenameAssistantDialog by remember { mutableStateOf(false) }

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
        // Last-resort safety net: the LazyColumn below is keyed by message id
        // (required for correct animation/scroll behavior), and Compose treats
        // a repeated key as fatal — it crashes the whole screen rather than
        // just misrendering. A duplicate id should never reach here (event
        // handlers update in place instead of appending when an id already
        // exists), but distinctBy costs nothing on a chat-length list and
        // means a future event-handling bug degrades to "a message is
        // missing" instead of a hard crash.
        val deduped = uiState.messages.distinctBy { it.id }
        if (uiState.searchQuery.isBlank()) {
            deduped
        } else {
            val query = uiState.searchQuery.lowercase()
            deduped.filter { msg ->
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

    // The avatar is customized from Settings (a separate ViewModel writing
    // the same prefs key) — re-read it every time this screen re-enters
    // composition so a change made there shows up on return.
    LaunchedEffect(Unit) {
        viewModel.loadAssistantAvatar()
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

    // Show error snackbar with auto-dismiss based on severity
    LaunchedEffect(uiState.errorEvent) {
        uiState.errorEvent?.let { event ->
            snackbarHostState.showSnackbar(
                message = event.message,
                duration = if (event.autoDismissMs > 0) SnackbarDuration.Short else SnackbarDuration.Indefinite,
            )
            // Auto-dismiss after specified duration (0 = manual only)
            if (event.autoDismissMs > 0) {
                delay(event.autoDismissMs)
            }
            viewModel.clearErrorEvent()
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { showRenameAssistantDialog = true },
                    ) {
                        Text(
                            text = uiState.assistantName,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = t("Rename", "تغییر نام"),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                            ) {
                                // The name itself opens rename (matches the
                                // drawer header affordance); the status dot
                                // stays the entry point to Runtime, so both
                                // remain reachable from the top bar.
                                Text(
                                    text = uiState.assistantName,
                                    modifier = Modifier.clickable { showRenameAssistantDialog = true },
                                )
                                Box(modifier = Modifier.clickable { onNavigateToRuntime() }) {
                                    ConnectionIndicator(uiState.connectionState)
                                }
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
                        // Tight gap by default; itemsIndexed adds extra top
                        // padding when a message starts a new group (turn),
                        // so the eye reads turn boundaries instead of a flat
                        // evenly-spaced list.
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                    ) {
                        if (filteredMessages.isEmpty() &&
                            uiState.connectionState == ChatConnectionState.Connected
                        ) {
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (uiState.searchQuery.isNotBlank()) {
                                            t("No matching messages", "پیامی پیدا نشد")
                                        } else {
                                            t("Ask Hermes anything", "هر چیزی از هرمس بپرس")
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        itemsIndexed(filteredMessages, key = { _, m -> m.id }) { index, message ->
                            val isLastAssistant = message is ChatMessage.Assistant &&
                                    !message.isStreaming &&
                                    filteredMessages.lastOrNull { it is ChatMessage.Assistant } == message
                            // Grouped == previous message is from the same side
                            // (user vs agent). Used to show the agent avatar only
                            // once per run and tighten consecutive bubbles.
                            val prev = filteredMessages.getOrNull(index - 1)
                            val next = filteredMessages.getOrNull(index + 1)
                            val grouped = prev != null &&
                                    (prev is ChatMessage.User) == (message is ChatMessage.User)
                            val isLastInGroup = next == null ||
                                    (next is ChatMessage.User) != (message is ChatMessage.User)

                            // Tool/subagent/request cards should stand apart
                            // from the surrounding prose instead of being glued
                            // to it (they're "objects", not continuous text).
                            val isCard = message is ChatMessage.ToolCall ||
                                    message is ChatMessage.SubagentCard ||
                                    message is ChatMessage.InteractiveRequest
                            val prevIsCard = prev is ChatMessage.ToolCall ||
                                    prev is ChatMessage.SubagentCard ||
                                    prev is ChatMessage.InteractiveRequest
                            val topPad = when {
                                prev == null -> 0.dp
                                !grouped -> 18.dp                 // user <-> agent turn boundary
                                isCard != prevIsCard -> 16.dp     // text <-> card: give the card air
                                isCard && prevIsCard -> 8.dp      // stacked cards: a little gap between them
                                else -> 0.dp                      // continuous prose from one speaker
                            }

                            Box(
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = tween(220),
                                        placementSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            visibilityThreshold = IntOffset.VisibilityThreshold,
                                        ),
                                    )
                                    .padding(top = topPad),
                            ) {
                            MessageBubble(
                                message = message,
                                grouped = grouped,
                                isLastInGroup = isLastInGroup,
                                avatarUri = uiState.assistantAvatarPath,
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
                                onBranch = { viewModel.branchSession() },
                                onDownloadFile = { url, name -> viewModel.downloadFile(url, name) },
                            )
                            }
                        }
                    }
                }

                // Agent's live task list for the current turn (todos from
                // tool.start / tool.complete), pinned above the input bar
                if (uiState.activeTodos.isNotEmpty()) {
                    AgentTodoCard(todos = uiState.activeTodos)
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
                    onSteer = viewModel::steerAgent,
                    onAttachFile = viewModel::attachFromUri,
                    onRemoveAttachment = viewModel::removeAttachment,
                    reasoningLevel = uiState.reasoningLevel,
                    onReasoningLevelChange = viewModel::setReasoningLevel,
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
                    onClick = { viewModel.downloadFile(imageUrl, "") },
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

    // Rename dialog — client-side display name only (top bar / drawer header).
    if (showRenameAssistantDialog) {
        var nameInput by remember(uiState.assistantName) { mutableStateOf(uiState.assistantName) }
        AlertDialog(
            onDismissRequest = { showRenameAssistantDialog = false },
            title = { Text(t("Rename assistant", "تغییر نام دستیار")) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    placeholder = { Text("Hermes") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAssistantName(nameInput)
                    showRenameAssistantDialog = false
                }) {
                    Text(t("Save", "ذخیره"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameAssistantDialog = false }) {
                    Text(t("Cancel", "انصراف"))
                }
            },
        )
    }
}


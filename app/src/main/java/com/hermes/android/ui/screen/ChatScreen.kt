package com.hermes.android.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.viewmodel.ConfigViewModel
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ChatViewModel
import com.hermes.android.ui.viewmodel.SessionItem
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.TextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import coil.compose.AsyncImage
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.HxHeaderCircleButton
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.design.hxSoftShadow
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
    onNavigateToTasks: () -> Unit = {},
    onNavigateToRuntime: () -> Unit = {},
    onNavigateToCron: () -> Unit = {},
    sharedText: String? = null,
    resumeSessionId: String? = null,
    themeModeState: com.hermes.android.ui.theme.ThemeModeState? = null,
    viewModel: ChatViewModel = hiltViewModel(),
    // The model catalogue and the switch itself already live in ConfigViewModel
    // — including the part that is easy to get wrong, which is telling the live
    // session about the change rather than only the next one. Reaching for it
    // here keeps one implementation of that rather than a second copy.
    modelPicker: com.hermes.android.ui.viewmodel.ModelPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modelPickerState by modelPicker.uiState.collectAsStateWithLifecycle()
    val notification by viewModel.notification.collectAsStateWithLifecycle()
    val slashCommands by viewModel.slashCommands.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showRenameAssistantDialog by remember { mutableStateOf(false) }
    var showChanges by remember { mutableStateOf(false) }
    var showContext by remember { mutableStateOf(false) }
    var drawerMenuTarget by remember { mutableStateOf<SessionItem?>(null) }
    var deleteFromMessageId by remember { mutableStateOf<String?>(null) }
    var showModelSheet by remember { mutableStateOf(false) }

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
    // Long chats render a window — the latest turns, with older ones added as
    // the list is scrolled to the top (ChatHistoryWindow). Search spans all.
    val windowedMessages = rememberChatHistoryWindow(
        messages = uiState.messages,
        listState = listState,
        resetKey = uiState.sessionLoadedAt,
        enabled = uiState.searchQuery.isBlank(),
    )
    val filteredMessages = remember(windowedMessages, uiState.searchQuery) {
        // Last-resort safety net: the LazyColumn below is keyed by message id
        // (required for correct animation/scroll behavior), and Compose treats
        // a repeated key as fatal — it crashes the whole screen rather than
        // just misrendering. A duplicate id should never reach here (event
        // handlers update in place instead of appending when an id already
        // exists), but distinctBy costs nothing on a chat-length list and
        // means a future event-handling bug degrades to "a message is
        // missing" instead of a hard crash.
        val deduped = windowedMessages.distinctBy { it.id }
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

    // An agent turn is not one message. The gateway opens a new assistant
    // message for every stretch of narration between tool calls, so a single
    // question can produce a dozen — and left alone, the chat fills up with the
    // agent talking to itself on the way to an answer.
    //
    // Whatever a turn did on the way is collected in list order and shown in a
    // trace rather than as loose cards and fragments. List order is also what
    // gives that trace a true sequence: reasoning and tool events carry no
    // shared ordering key, but the order they arrived in is one.
    //
    // Two independent choices shape what folds:
    //
    //  • Narration — off by default, so only the message that ends a turn stays
    //    on the chat surface. A reader who wants the running commentary turns it
    //    back on in Settings, and then every fragment keeps its place in the
    //    flow with its own reasoning and its own tools beneath it.
    //  • Search — while a query is active nothing folds at all, tool cards
    //    included, because a hit must never be hidden inside a closed sheet.
    val searching = uiState.searchQuery.isNotBlank()
    val foldNarration = !searching && themeModeState?.showInlineNarration != true
    //
    // The cache hands back the previous list for every trace whose content is
    // unchanged: a fresh list per rebuild (every 80ms while streaming) made
    // every visible bubble recompose, and long chats crawled.
    val turnWorkCache = remember { TurnWorkCache() }
    val turnWork: Map<String, List<HxTraceItem>> =
        remember(filteredMessages, foldNarration, searching) {
            if (searching) {
                emptyMap()
            } else {
                turnWorkCache.build(filteredMessages, foldNarration)
            }
        }

    // What folded into a trace leaves the flow: every tool card a trace
    // carries, and — when narration is folded — every assistant message but
    // the one ending its turn.
    val visibleMessages = remember(filteredMessages, turnWork, searching) {
        visibleChatMessages(filteredMessages, turnWork, searching)
    }

    // What the agent is doing right now (null = idle). Derived, not stored —
    // the running tool cards / streaming flags already carry the state.
    // Shown in the connection-status slot of the top bar while a turn runs
    // (user decision: working state replaces the connection chip, no extra
    // chrome), and reused to gate the live plan (todo) strip so it only
    // appears while the plan is actually being executed.
    // Removed: agent activity text was distracting - ConnectionIndicator handles it
    val agentActivity: String? = null

    // Keep drawer state in sync with ViewModel state.
    LaunchedEffect(uiState.showSessionDrawer) {
        if (uiState.showSessionDrawer) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    // ...and the ViewModel in sync with the drawer. The sheet closes by paths the
    // ViewModel never hears about — the scrim, a swipe, the back gesture, Settings
    // and Tasks — after which showSessionDrawer was still true, so the next tap on
    // the hamburger toggled it to false and the drawer simply did not open. Every
    // second tap did nothing.
    //
    // Hiding the keyboard lives here rather than in the effect above so it also
    // covers the drawer being swiped open: otherwise the keyboard stays up and
    // covers the buttons at the foot of the sheet.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            keyboardController?.hide()
            focusManager.clearFocus()
            // A swipe opens the drawer without the hamburger's toggle, which was the only
            // thing that fetched the chat list; a fresh screen then showed an empty drawer.
            viewModel.onSessionDrawerOpened()
        } else if (uiState.showSessionDrawer) {
            viewModel.closeSessionDrawer()
        }
    }

    // The avatar is customized from Settings (a separate ViewModel writing
    // the same prefs key) — re-read it every time this screen re-enters
    // composition so a change made there shows up on return.
    LaunchedEffect(Unit) {
    }

    // When a new message arrives, scroll the latest USER message to the TOP
    // of the viewport. This mirrors the Gemini / ChatGPT mobile pattern: the
    // user's question stays pinned at the top of the visible area, leaving
    // room below for the AI's reply to stream in. The eye stays at the top,
    // no constant scroll-chasing, no flicker.
    //
    // We deliberately do NOT auto-scroll during streaming. Earlier attempts
    // keyed an effect off the streaming content length and ran a scroll on
    // every token — that caused an infinite render loop (scrollToItem flips
    // isScrollInProgress, which re-collects, which re-launches the effect,
    // which scrolls again, every second).
    //
    // Keying on messages.size (as this used to) has the same problem one
    // level up: a single agent turn appends more than one item — a tool-call
    // card per tool it runs, a status line, a sub-agent card, the assistant's
    // own placeholder message before text starts arriving — so the size
    // changes repeatedly through one turn, and every change re-ran the
    // scroll and snapped back to the top of the (unchanged) user message.
    // That's what "keeps jumping to the top while it's still writing, can't
    // read it" was: not a per-token loop, but the same re-trigger one layer
    // up. Key on the last user message's *id* instead — it only changes when
    // a genuinely new user turn starts, so this now fires once per turn,
    // not once per item the agent happens to add while answering it.
    //
    // scrollToItem (instant) is used instead of animateScrollToItem so there
    // is no animation in flight to be cancelled/restarted by the next event.
    //
    // The index must come from visibleMessages — the list the LazyColumn
    // renders. uiState.messages still holds the tool-call cards it hides, so
    // its index pointed further down and pushed the message above the screen.
    val lastUserMessageId = visibleMessages.lastOrNull { it is ChatMessage.User }?.id

    // Diagnostic: a turn running with no reply drawn. Says in the connection journal
    // whether the reply is missing from the state or only from what the list draws.
    val streamingInState = uiState.messages.lastOrNull { it is ChatMessage.Assistant && it.isStreaming }?.id
    val replyDrawn = streamingInState != null && visibleMessages.any { it.id == streamingInState }
    LaunchedEffect(uiState.isSending, streamingInState, replyDrawn, uiState.activeSessionId) {
        if (!uiState.isSending || replyDrawn) return@LaunchedEffect
        kotlinx.coroutines.delay(4_000)
        timber.log.Timber.w(
            "[Render] turn running, no reply drawn: session=${uiState.activeSessionId} " +
                "inState=${streamingInState != null} messages=${uiState.messages.size} " +
                "windowed=${windowedMessages.size} visible=${visibleMessages.size}",
        )
    }
    LaunchedEffect(lastUserMessageId) {
        val lastUserIndex = visibleMessages.indexOfLast { it is ChatMessage.User }
        if (lastUserIndex >= 0) {
            listState.scrollToItem(lastUserIndex)
        }
    }

    // Jump to last message whenever a session is loaded/resumed
    LaunchedEffect(uiState.sessionLoadedAt) {
        if (uiState.sessionLoadedAt > 0L && visibleMessages.isNotEmpty()) {
            listState.scrollToItem(visibleMessages.lastIndex)
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
                modifier = Modifier.fillMaxHeight().width(322.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                drawerShape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
            ) {
                // Pinned chats first, each group by last update in the chosen order.
                val drawerSessions = remember(uiState.sessions, uiState.drawerPinnedIds, uiState.drawerSortNewest) {
                    val byTime = if (uiState.drawerSortNewest) {
                        uiState.sessions.sortedByDescending { it.updatedAt }
                    } else {
                        uiState.sessions.sortedBy { it.updatedAt }
                    }
                    byTime.sortedByDescending { it.id in uiState.drawerPinnedIds }
                }
                val closeDrawerThen: (() -> Unit) -> Unit = { action ->
                    scope.launch { drawerState.close() }
                    action()
                }
                WorkspaceDrawerSheet(
                    sessions = drawerSessions,
                    activeSessionId = uiState.activeSessionId,
                    pulseOf = { session ->
                        when {
                            uiState.pendingApproval?.sessionId == session.id -> SessionPulse.Waiting
                            uiState.sessionActivity[session.id]?.isRunning == true -> SessionPulse.Running
                            uiState.sessionActivity[session.id]?.failed == true -> SessionPulse.Failed
                            else -> SessionPulse.None
                        }
                    },
                    unreadOf = { session -> uiState.sessionActivity[session.id]?.unreadReplies ?: 0 },
                    destinations = rememberWorkspaceDestinations(
                        waitingCount = if (uiState.pendingApproval != null) 1 else 0,
                        onWorkbench = { closeDrawerThen(onNavigateToTasks) },
                        onAgent = { closeDrawerThen(onNavigateToSettings) },
                        onScheduled = { closeDrawerThen(onNavigateToCron) },
                        onServer = { closeDrawerThen(onNavigateToRuntime) },
                    ),
                    onSearch = { closeDrawerThen(onNavigateToSessions) },
                    onNewChat = { closeDrawerThen { viewModel.newConversation() } },
                    onSessionClick = { session -> closeDrawerThen { viewModel.resumeSession(session.id) } },
                    onSessionLongClick = { session -> drawerMenuTarget = session },
                    onAccount = { closeDrawerThen(onNavigateToSettings) },
                )

                // ── Long-press menu: rename / pin / delete ─────────────────
                drawerMenuTarget?.let { target ->
                    val pinned = target.id in uiState.drawerPinnedIds
                    AlertDialog(
                        onDismissRequest = { drawerMenuTarget = null },
                        title = {
                            Text(target.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        text = {
                            Column {
                                TextButton(onClick = {
                                    drawerMenuTarget = null
                                    viewModel.drawerShowRename(target.id, target.title)
                                }) { Text(t("Rename", "تغییر نام")) }
                                TextButton(onClick = {
                                    drawerMenuTarget = null
                                    viewModel.drawerTogglePin(target.id)
                                }) { Text(if (pinned) t("Unpin", "برداشتن سنجاق") else t("Pin", "سنجاق")) }
                                TextButton(onClick = {
                                    drawerMenuTarget = null
                                    viewModel.drawerShowDelete(target.id)
                                }) {
                                    Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { drawerMenuTarget = null }) { Text(t("Cancel", "لغو")) }
                        },
                    )
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
            }
        },
    ) {
        Scaffold(
            topBar = {
                // The activity is edge-to-edge, and a plain Column does not
                // consume the status bar inset the way the Material TopAppBar
                // this replaced did — without this the chrome draws under the
                // status bar and off the top of the screen.
                Column(modifier = Modifier.statusBarsPadding()) {
                    // Floating chrome instead of a flat Material app bar: two
                    // shadowed circles either side of the status pill, same
                    // language as the composer's own floating controls, so
                    // the top and bottom of the screen read as one design
                    // instead of "system bar" + "custom bar".
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HxHeaderCircleButton(
                            icon = HxIcons.MenuShort,
                            contentDescription = t("Sessions", "گفتگوها"),
                            onClick = { viewModel.toggleSessionDrawer() },
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                                .clickable { onNavigateToRuntime() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (agentActivity != null) {
                                    AgentWorkingIndicator(agentActivity)
                                } else {
                                    ConnectionIndicator(
                                        state = uiState.connectionState,
                                        connectingSince = uiState.connectingSince,
                                        bootEstimateMs = uiState.bootEstimateMs,
                                    )
                                }
                            }
                        }
                        HxHeaderCircleButton(
                            icon = HxIcons.NewChat,
                            contentDescription = t("New chat", "گفتگوی جدید"),
                            onClick = { viewModel.newConversation() },
                            iconSize = 25.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        // Context and search moved behind ⋮ so the bar keeps
                        // only new chat; nothing the old buttons did is gone.
                        Box {
                            var showOverflow by remember { mutableStateOf(false) }
                            HxHeaderCircleButton(
                                icon = if (uiState.showSearch) Icons.Default.Close else Icons.Default.MoreVert,
                                contentDescription = if (uiState.showSearch) t("Close search", "بستن جستجو") else t("More", "بیشتر"),
                                onClick = {
                                    if (uiState.showSearch) viewModel.toggleSearch() else showOverflow = true
                                },
                            )
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(t("Search messages", "جستجو در پیام‌ها")) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.toggleSearch()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(t("Stats", "آمار")) },
                                    leadingIcon = { Icon(Icons.Default.DataUsage, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        showContext = true
                                    },
                                )
                            }
                        }
                    }
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
                                if (visibleMessages.isNotEmpty()) {
                                    listState.animateScrollToItem(visibleMessages.lastIndex)
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
                            // HxSpace.screen, the same inset every other screen
                            // uses. The chat had a hand-written 12dp, which is
                            // why its text ran to the edges while the rest of
                            // the app breathed — and a long reply with no margin
                            // reads as a wall rather than as a message.
                            .padding(horizontal = HxSpace.screen),
                        // Tight gap by default; itemsIndexed adds extra top
                        // padding when a message starts a new group (turn),
                        // so the eye reads turn boundaries instead of a flat
                        // evenly-spaced list.
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        // More at the top than the bottom: the first message
                        // sits directly under the top bar and needs clearing
                        // from it, while the composer already brings its own
                        // padding to the bottom edge.
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = HxSpace.xl,
                            bottom = HxSpace.md,
                        ),
                    ) {
                        if (visibleMessages.isEmpty() &&
                            uiState.connectionState == ChatConnectionState.Connected
                        ) {
                            item {
                                if (uiState.searchQuery.isNotBlank()) {
                                    Box(
                                        modifier = Modifier.fillParentMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = t("No matching messages", "پیامی پیدا نشد"),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    // A new chat opens empty: no greeting, no suggestions.
                                    EmptyChatBody(Modifier.fillParentMaxSize())
                                }
                            }
                        }
                        val lastAssistantId = visibleMessages.lastOrNull { it is ChatMessage.Assistant }?.id
                        itemsIndexed(visibleMessages, key = { _, m -> m.id }) { index, message ->
                            val isLastAssistant = message is ChatMessage.Assistant &&
                                    !message.isStreaming &&
                                    message.id == lastAssistantId
                            // Grouped == previous message is from the same side
                            // (user vs agent). Used to show the agent avatar only
                            // once per run and tighten consecutive bubbles.
                            val prev = visibleMessages.getOrNull(index - 1)
                            val next = visibleMessages.getOrNull(index + 1)
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
                                    // New messages fade in; nothing glides. A
                                    // placement spring made every message drift
                                    // to its new spot whenever something above
                                    // it changed height (a thinking line
                                    // settling, a trace folding, older turns
                                    // loading), so the text never felt fixed
                                    // to the page.
                                    .animateItem(
                                        fadeInSpec = tween(220),
                                        placementSpec = null,
                                    )
                                    .padding(top = topPad),
                            ) {
                            MessageBubble(
                                message = message,
                                grouped = grouped,
                                isLastInGroup = isLastInGroup,
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
                                traceItems = turnWork[message.id].orEmpty(),
                                onRetry = { viewModel.retryLastMessage() },
                                onRespondToClarify = viewModel::respondToClarify,
                                onRespondToClarifyBatch = viewModel::respondToClarifyBatch,
                                onRespondToSudo = viewModel::respondToSudo,
                                onRespondToSecret = viewModel::respondToSecret,
                                onImageClick = { url -> fullscreenImageUrl = url },
                                resolveUrl = viewModel::resolveMediaUrl,
                                onBranch = { viewModel.branchSession() },
                                onDownloadFile = { url, name -> viewModel.downloadFile(url, name) },
                                onEditMessage = viewModel::startEditing,
                                onDeleteMessage = { id -> deleteFromMessageId = id },
                            )
                            }
                        }
                        // Trailing spacer so the last user message can be
                        // scrolled to the TOP of the viewport — but ONLY while
                        // the AI is still composing its reply (isSending or
                        // the last message is an Assistant that isStreaming).
                        // Once the reply is done, the spacer collapses to zero
                        // so the user can scroll normally and there's no large
                        // empty gap at the bottom of the conversation.
                        //
                        // Without this spacer (while streaming), the last user
                        // message can't be scrolled to the top — Compose pins
                        // it to the bottom because there's nothing below it to
                        // fill the visible area. With it, the user's question
                        // stays pinned at the top while the AI's reply streams
                        // in below, mirroring the Gemini / ChatGPT mobile UX.
                        val lastMsg = filteredMessages.lastOrNull()
                        val isAwaitingReply = uiState.isSending ||
                            (lastMsg is ChatMessage.Assistant && lastMsg.isStreaming)
                        // Always-present item animating between 600dp and 0 —
                        // conditionally removing it made the whole tail of the
                        // list snap upward the instant a reply finished. Kept
                        // in composition with a stable key so the collapse is
                        // a smooth ease-out instead of a jump cut.
                        item(key = "streaming-tail-spacer") {
                            // One viewport tall — just enough to lift the user
                            // message to the top (a fixed 600dp overshot once the
                            // composer grew). Sized by fillParentMaxHeight at
                            // layout time: reading listState.layoutInfo here made
                            // every scroll frame touch this item and scrolling lag.
                            val spacerFraction by animateFloatAsState(
                                targetValue = if (isAwaitingReply) 1f else 0f,
                                animationSpec = tween(durationMillis = 450),
                                label = "streamingTailSpacer",
                            )
                            Spacer(modifier = Modifier.fillParentMaxHeight(spacerFraction))
                        }
                    }
                }

                // Agent's live task list for the current turn (todos from
                // tool.start / tool.complete), pinned above the input bar.
                // Only while the turn is actually running (agentActivity !=
                // null) — user decision: the plan is execution feedback, not
                // permanent furniture, and the chat must stay uncluttered.
                if (uiState.activeTodos.isNotEmpty() && agentActivity != null) {
                    AgentTodoCard(todos = uiState.activeTodos)
                }

                // Editing a sent message: say so above the composer, with the way out.
                if (uiState.editingMessageId != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = HxSpace.screen, end = 4.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = t(
                                "Editing message — sending replaces it and everything after it",
                                "ویرایش پیام — با ارسال، این پیام و هرچه بعدش آمده جایگزین می‌شود",
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = viewModel::cancelEditing) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = t("Cancel edit", "لغو ویرایش"),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
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
                    activeModel = modelPickerState.activeModel,
                    onModelClick = { showModelSheet = true },
                )
            }
        }
    }

    deleteFromMessageId?.let { messageId ->
        AlertDialog(
            onDismissRequest = { deleteFromMessageId = null },
            title = { Text(t("Delete message?", "حذف پیام؟")) },
            text = {
                Text(
                    t(
                        "This message and everything after it will be removed from the chat, for Hermes too. This cannot be undone.",
                        "این پیام و همهٔ پیام‌های بعد از آن از گفتگو حذف می‌شوند، برای Hermes هم. قابل برگشت نیست.",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteFromMessageId = null
                    viewModel.deleteFromMessage(messageId)
                }) { Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFromMessageId = null }) { Text(t("Cancel", "لغو")) }
            },
        )
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

    // Tool-approval modal sheet (approval.request). Rendered at ChatScreen
    // level so it overlays the whole screen; see ChatApprovalSheet.kt for
    // why it can't be swiped away.
    uiState.pendingApproval?.let { approval ->
        ApprovalSheet(
            approval = approval,
            onRespond = viewModel::respondToApproval,
        )
    }

    if (showChanges) {
        uiState.activeSessionId?.let { sid ->
            ChangesSheet(
                sessionId = sid,
                snackbarHostState = snackbarHostState,
                onDismiss = { showChanges = false },
                onTranscriptChanged = viewModel::reloadTranscript,
            )
        }
    }

    if (showContext) {
        uiState.activeSessionId?.let { sid ->
            ContextSheet(
                sessionId = sid,
                snackbarHostState = snackbarHostState,
                onDismiss = { showContext = false },
            )
        }
    }

    ChatModelPicker(
        viewModel = modelPicker,
        chat = uiState,
        showSheet = showModelSheet,
        snackbarHostState = snackbarHostState,
        onReasoningLevelChange = viewModel::setReasoningLevel,
        onDismissSheet = { showModelSheet = false },
    )

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

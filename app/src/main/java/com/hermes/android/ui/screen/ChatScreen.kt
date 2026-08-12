package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.HxHeaderCircleButton
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val HermesAetherMotion = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private val HermesBackground = Color(0xFFF7F5FC)
private val HermesBackgroundMid = Color(0xFFF2F4FB)
private val HermesBackgroundBottom = Color(0xFFEDEFF8)
private val ComposerShape = RoundedCornerShape(26.dp)

private fun hermesChatBackground(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to HermesBackground,
        0.48f to HermesBackgroundMid,
        1.0f to HermesBackgroundBottom,
    ),
)

private fun hermesTopFade(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to HermesBackground.copy(alpha = 0.98f),
        0.28f to HermesBackground.copy(alpha = 0.92f),
        0.62f to HermesBackground.copy(alpha = 0.48f),
        1.0f to Color.Transparent,
    ),
)

private fun hermesTopTailFade(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to HermesBackground.copy(alpha = 0.12f),
        1.0f to Color.Transparent,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSessions: () -> Unit = {},
    onNavigateToTasks: () -> Unit = {},
    onNavigateToRuntime: () -> Unit = {},
    sharedText: String? = null,
    resumeSessionId: String? = null,
    themeModeState: com.hermes.android.ui.theme.ThemeModeState? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val slashCommands by viewModel.slashCommands.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    var composerHeightPx by remember { mutableIntStateOf(0) }
    var shouldAutoFollow by remember { mutableStateOf(true) }
    var showRenameAssistantDialog by remember { mutableStateOf(false) }

    val topOverlayHeight = with(density) {
        if (topOverlayHeightPx == 0) 86.dp else topOverlayHeightPx.toDp()
    }
    val composerHeight = with(density) {
        if (composerHeightPx == 0) 104.dp else composerHeightPx.toDp()
    }
    val imeBottom = with(density) {
        WindowInsets.ime.getBottom(this).toDp()
    }
    val animatedImeBottom by animateDpAsState(
        targetValue = imeBottom,
        animationSpec = tween(260, easing = HermesAetherMotion),
        label = "hermes-ime-bottom",
    )

    val messages = remember(uiState.messages, uiState.searchQuery) {
        val unique = uiState.messages.distinctBy { it.id }
        if (uiState.searchQuery.isBlank()) unique else unique.filter { message ->
            val query = uiState.searchQuery.lowercase()
            when (message) {
                is ChatMessage.User -> message.text.lowercase().contains(query)
                is ChatMessage.Assistant -> message.text.lowercase().contains(query)
                is ChatMessage.ToolCall -> message.toolName.lowercase().contains(query)
                is ChatMessage.Status -> message.text.lowercase().contains(query)
                is ChatMessage.InteractiveRequest -> message.question.lowercase().contains(query)
                is ChatMessage.SubagentCard -> message.text.lowercase().contains(query)
            }
        }
    }

    val atListEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount == 0 ||
                (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 1
        }
    }

    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y < 0f) shouldAutoFollow = false
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                shouldAutoFollow = atListEnd
                return Velocity.Zero
            }
        }
    }

    val autoFollowContentKey = remember(messages, uiState.isSending) {
        buildString {
            messages.takeLast(12).forEach { message ->
                append(message.id).append(':')
                when (message) {
                    is ChatMessage.User -> append(message.text)
                    is ChatMessage.Assistant -> append(message.text).append(message.isStreaming)
                    is ChatMessage.ToolCall -> append(message.resultText).append(message.isRunning)
                    is ChatMessage.Status -> append(message.text)
                    is ChatMessage.InteractiveRequest -> append(message.question).append(message.answered)
                    is ChatMessage.SubagentCard -> append(message.text).append(message.isComplete)
                }
                append('|')
            }
            append(uiState.isSending)
        }.hashCode()
    }

    LaunchedEffect(uiState.showSessionDrawer) {
        if (uiState.showSessionDrawer) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) viewModel.updateInputText(sharedText)
    }
    LaunchedEffect(resumeSessionId) {
        if (!resumeSessionId.isNullOrBlank()) viewModel.resumeSession(resumeSessionId)
    }
    LaunchedEffect(uiState.errorEvent) {
        uiState.errorEvent?.let { event ->
            snackbarHostState.showSnackbar(
                message = event.message,
                duration = if (event.autoDismissMs == 0L) SnackbarDuration.Indefinite else SnackbarDuration.Short,
            )
            viewModel.clearErrorEvent()
        }
    }
    LaunchedEffect(uiState.inputText) {
        if (uiState.inputText.isNotEmpty()) {
            kotlinx.coroutines.delay(500)
            viewModel.saveDraft()
        }
    }
    LaunchedEffect(uiState.sessionLoadedAt) {
        if (uiState.sessionLoadedAt > 0 && messages.isNotEmpty()) {
            shouldAutoFollow = true
            listState.scrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(autoFollowContentKey) {
        if (shouldAutoFollow && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().width(322.dp),
                drawerShape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
            ) {
                HermesDrawerContent(
                    assistantName = uiState.assistantName,
                    sessions = uiState.sessions,
                    activeSessionId = uiState.activeSessionId,
                    drawerSearchQuery = uiState.drawerSearchQuery,
                    drawerSortNewest = uiState.drawerSortNewest,
                    drawerPinnedIds = uiState.drawerPinnedIds,
                    onSearchQueryChange = viewModel::updateDrawerSearch,
                    onToggleSort = viewModel::toggleDrawerSort,
                    onSessionClick = { id ->
                        viewModel.resumeSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onRenameAssistant = { showRenameAssistantDialog = true },
                    onTasks = {
                        scope.launch { drawerState.close() }
                        onNavigateToTasks()
                    },
                    onRenameSession = viewModel::drawerShowRename,
                    onTogglePin = viewModel::drawerTogglePin,
                    onDeleteSession = viewModel::drawerShowDelete,
                    onNewChat = {
                        viewModel.newConversation()
                        scope.launch { drawerState.close() }
                    },
                    onSettings = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                )
                uiState.drawerRenameTarget?.let { rename ->
                    AlertDialog(
                        onDismissRequest = viewModel::drawerHideRename,
                        title = { Text(t("Rename chat", "تغییر نام گفتگو")) },
                        text = {
                            OutlinedTextField(
                                value = rename.inputText,
                                onValueChange = viewModel::drawerUpdateRenameText,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            Button(onClick = viewModel::drawerConfirmRename) { Text(t("Save", "ذخیره")) }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::drawerHideRename) { Text(t("Cancel", "لغو")) }
                        },
                    )
                }
                uiState.drawerDeleteTarget?.let {
                    AlertDialog(
                        onDismissRequest = viewModel::drawerHideDelete,
                        title = { Text(t("Delete chat?", "حذف گفتگو؟")) },
                        text = { Text(t("This chat will be permanently deleted.", "این گفتگو برای همیشه حذف می‌شود.")) },
                        confirmButton = {
                            Button(
                                onClick = viewModel::drawerConfirmDelete,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) { Text(t("Delete", "حذف")) }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::drawerHideDelete) { Text(t("Cancel", "لغو")) }
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
        ) { scaffoldPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(hermesChatBackground())
                    .padding(scaffoldPadding),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollConnection)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(
                        top = topOverlayHeight + 10.dp,
                        bottom = composerHeight + animatedImeBottom + 28.dp,
                    ),
                ) {
                    if (messages.isEmpty()) {
                        item(key = "empty-chat") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(260.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (uiState.searchQuery.isBlank()) {
                                        t("Start a conversation with ${uiState.assistantName}", "گفتگو با ${uiState.assistantName} را شروع کنید")
                                    } else {
                                        t("No matching messages", "پیامی پیدا نشد")
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        HermesMessagePlaceholder(message)
                    }
                }

                ConversationTopOverlay(
                    connectionState = uiState.connectionState,
                    searchOpen = uiState.showSearch,
                    searchQuery = uiState.searchQuery,
                    onMenu = viewModel::toggleSessionDrawer,
                    onSearch = viewModel::toggleSearch,
                    onRuntime = onNavigateToRuntime,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onMeasured = { topOverlayHeightPx = it },
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { composerHeightPx = it.height }
                        .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp)
                        .shadow(14.dp, ComposerShape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000)),
                ) {
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
    }
}

@Composable
private fun ConversationTopOverlay(
    connectionState: ChatConnectionState,
    searchOpen: Boolean,
    searchQuery: String,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onRuntime: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged(onMeasured),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(hermesTopFade())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HxHeaderCircleButton(
                    icon = Icons.Default.Menu,
                    contentDescription = t("Sessions", "گفتگوها"),
                    onClick = onMenu,
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Surface(
                        onClick = onRuntime,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    ) {
                        Text(
                            text = connectionLabel(connectionState),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                HxHeaderCircleButton(
                    icon = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = t("Search", "جست‌وجو"),
                    onClick = onSearch,
                )
            }
            AnimatedVisibility(visible = searchOpen, enter = fadeIn(), exit = fadeOut()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                    placeholder = { Text(t("Search messages...", "جستجو در پیام‌ها...")) },
                    shape = RoundedCornerShape(24.dp),
                )
            }
        }
        Spacer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(42.dp)
                .background(hermesTopTailFade()),
        )
    }
}

private fun connectionLabel(state: ChatConnectionState): String = when (state) {
    ChatConnectionState.Connected -> t("Connected", "متصل")
    ChatConnectionState.Connecting -> t("Connecting…", "در حال اتصال…")
    ChatConnectionState.Reconnecting -> t("Reconnecting…", "اتصال مجدد…")
    ChatConnectionState.Failed -> t("Connection failed", "اتصال ناموفق")
    ChatConnectionState.Disconnected -> t("Disconnected", "قطع‌شده")
}

@Composable
private fun HermesMessagePlaceholder(message: ChatMessage) {
    val (label, text, isUser) = when (message) {
        is ChatMessage.User -> Triple(t("You", "شما"), message.text, true)
        is ChatMessage.Assistant -> Triple(t("Hermes", "هرمس"), message.text, false)
        is ChatMessage.ToolCall -> Triple(t("Tool", "ابزار"), message.toolName, false)
        is ChatMessage.Status -> Triple(t("Status", "وضعیت"), message.text, false)
        is ChatMessage.InteractiveRequest -> Triple(t("Question", "پرسش"), message.question, false)
        is ChatMessage.SubagentCard -> Triple(t("Sub-agent", "عامل فرعی"), message.text, false)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = if (isUser) MaterialTheme.colorScheme.surface.copy(alpha = 0.88f) else Color.Transparent,
            tonalElevation = if (isUser) 1.dp else 0.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = text.ifBlank { t("Message placeholder", "جای‌گذاری پیام") },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                )
            }
        }
    }
}

package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hermes.android.R
import com.hermes.android.domain.groups.GroupRooms
import com.hermes.android.ui.component.ContentBlock
import com.hermes.android.ui.component.FileKind
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.component.ImageGallery
import com.hermes.android.ui.component.parseContentBlocks
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.hxUserBubbleMaxWidth
import com.hermes.android.ui.viewmodel.GroupChatViewModel
import com.hermes.android.ui.viewmodel.GroupMentions
import com.hermes.android.ui.viewmodel.GroupNote
import com.hermes.android.ui.viewmodel.GroupRow
import com.hermes.android.ui.viewer.FileViewerActivity

/**
 * One Group Chat. Everything here is read from the room log on the gateway, so what
 * the members said while the app was closed is here when it opens.
 */
@Composable
fun GroupChatScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: GroupChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    // Read the room only while the screen is in front: the log is durable, so
    // whatever happens meanwhile is there on the next read.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.watch() }
    }
    LaunchedEffect(state.gone) { if (state.gone) onNavigateBack() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }
    LaunchedEffect(state.rows.size) {
        if (state.rows.isNotEmpty()) listState.animateScrollToItem(state.rows.lastIndex)
    }

    val members = state.room?.members.orEmpty()
    HermesScaffold(
        title = state.room?.name ?: stringResource(R.string.group_chat),
        subtitle = members.takeIf { it.isNotEmpty() }?.joinToString("، ") { it.title },
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename_group)) },
                        onClick = {
                            menuOpen = false
                            renaming = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_group), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = HxSpace.screen, vertical = HxSpace.md),
                verticalArrangement = Arrangement.spacedBy(HxSpace.md),
            ) {
                if (state.isLoading && state.rows.isEmpty()) {
                    item { CircularProgressIndicator(Modifier.padding(HxSpace.lg)) }
                } else if (state.rows.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.write_to_the_group_everyone_answers_or),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.rows, key = { it.key }) { row ->
                    when (row) {
                        is GroupRow.User -> UserRow(row.text)
                        is GroupRow.Member -> MemberRow(
                            name = row.name,
                            text = row.text,
                            resolveUrl = viewModel::resolveMediaUrl,
                            onImageClick = { fullscreenImageUrl = it },
                            onDownloadFile = viewModel::downloadFile,
                            onCopyCode = { clipboardManager.setText(AnnotatedString(it)) },
                        )
                        is GroupRow.Note -> NoteRow(row.note)
                    }
                }
            }

            state.approvals.forEach { approval ->
                ApprovalCard(
                    approval = approval,
                    memberName = members.firstOrNull { it.memberId == approval.memberId }?.title ?: approval.memberId,
                    onAnswer = { allow -> viewModel.answer(approval, allow) },
                )
            }
            state.retryTaskIds.firstOrNull()?.let { taskId ->
                ActionStrip(
                    text = stringResource(R.string.a_reply_was_cut_off_before_it_finished),
                    action = stringResource(R.string.try_again),
                    onAction = { viewModel.retry(taskId) },
                )
            }
            if (state.discussing) {
                ActionStrip(
                    text = stringResource(R.string.the_group_is_discussing),
                    action = stringResource(R.string.stop),
                    progress = true,
                    onAction = viewModel::stop,
                )
            }

            val query = GroupMentions.query(state.input)
            if (query != null) {
                val handles = GroupMentions.suggestions(query, members)
                if (handles.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = HxSpace.screen),
                        horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
                    ) {
                        handles.forEach { handle ->
                            AssistChip(
                                onClick = { viewModel.updateInput(GroupMentions.complete(state.input, handle)) },
                                label = { Text("@$handle") },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = HxSpace.md, vertical = HxSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::updateInput,
                    placeholder = { Text(stringResource(R.string.message_the_group)) },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = viewModel::send,
                    enabled = state.input.isNotBlank() && !state.isSending && state.room != null,
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                    }
                }
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(state.room?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename_group)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = {
                        renaming = false
                        viewModel.rename(name)
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_this_group)) },
            text = {
                Text(
                    stringResource(R.string.its_conversation_is_deleted_from_hermes)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    fullscreenImageUrl?.let { imageUrl ->
        // Every image the members sent, in order, so the viewer can page through them.
        val images = remember(imageUrl) {
            state.rows.filterIsInstance<GroupRow.Member>().flatMap { row ->
                parseContentBlocks(row.text).filterIsInstance<ContentBlock.Image>()
                    .map { viewModel.resolveMediaUrl(it.url) }
            }.distinct().let { if (imageUrl in it) it else listOf(imageUrl) }
        }
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ImageGallery(
                images = images,
                initialPage = images.indexOf(imageUrl),
                onDismiss = { fullscreenImageUrl = null },
                onSave = { viewModel.downloadFile(it, "") },
            )
        }
    }
}

@Composable
private fun UserRow(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = hxUserBubbleMaxWidth())
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .padding(12.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MemberRow(
    name: String,
    text: String,
    resolveUrl: (String) -> String,
    onImageClick: (String) -> Unit,
    onDownloadFile: (url: String, name: String) -> Unit,
    onCopyCode: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
        ProfileAvatar(name, size = 30)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MemberContent(text, resolveUrl, onImageClick, onDownloadFile, onCopyCode)
        }
    }
}

/**
 * A member's reply split into blocks the way a chat reply is, so images, code and the
 * files a Bot sends (`MEDIA:` paths) show as they do in a chat instead of raw text.
 */
@Composable
private fun MemberContent(
    text: String,
    resolveUrl: (String) -> String,
    onImageClick: (String) -> Unit,
    onDownloadFile: (url: String, name: String) -> Unit,
    onCopyCode: (String) -> Unit,
) {
    val context = LocalContext.current
    val blocks = remember(text) {
        parseContentBlocks(text).map { block ->
            when (block) {
                is ContentBlock.Image -> block.copy(url = resolveUrl(block.url))
                is ContentBlock.Video -> block.copy(url = resolveUrl(block.url))
                is ContentBlock.Audio -> block.copy(url = resolveUrl(block.url))
                is ContentBlock.Html -> block.copy(url = resolveUrl(block.url))
                is ContentBlock.FileRef -> block.copy(url = resolveUrl(block.url))
                else -> block
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.Text -> HermesMarkdown(markdown = block.markdown)
                is ContentBlock.Image -> InlineImageBlock(
                    alt = block.alt, url = block.url,
                    onImageClick = onImageClick,
                    onSave = { onDownloadFile(block.url, block.alt) },
                )
                is ContentBlock.Code -> CodeBlockCard(
                    language = block.language, code = block.code, onCopyCode = onCopyCode,
                )
                is ContentBlock.Mermaid -> MermaidBlockCard(code = block.code, onCopyCode = onCopyCode)
                is ContentBlock.Html -> HtmlBlockCard(
                    url = block.url, name = block.name,
                    onOpen = { FileViewerActivity.open(context, block.url, block.name) },
                )
                // A room has no chat folder to read a `::preview` page from: open the file instead.
                is ContentBlock.Preview -> {
                    val url = resolveUrl(block.file)
                    val name = block.file.substringAfterLast('/')
                    ArtifactCard(
                        emoji = "\uD83D\uDCC4", name = name,
                        actionLabel = stringResource(R.string.open),
                        onAction = { FileViewerActivity.open(context, url, name) },
                        onDownload = { onDownloadFile(url, name) },
                    )
                }
                is ContentBlock.Video -> InlineVideoPlayer(
                    url = block.url, name = block.name,
                    onDownload = { onDownloadFile(block.url, block.name) },
                )
                is ContentBlock.Audio -> InlineAudioPlayer(
                    url = block.url, name = block.name,
                    onDownload = { onDownloadFile(block.url, block.name) },
                )
                is ContentBlock.FileRef -> if (block.kind == FileKind.OTHER) {
                    ArtifactCard(
                        emoji = "\uD83D\uDCC4", name = block.name,
                        actionLabel = stringResource(R.string.download),
                        onAction = { onDownloadFile(block.url, block.name) },
                        onDownload = null,
                    )
                } else {
                    ArtifactCard(
                        emoji = "\uD83D\uDCC4", name = block.name,
                        actionLabel = stringResource(R.string.open),
                        onAction = { FileViewerActivity.open(context, block.url, block.name) },
                        onDownload = { onDownloadFile(block.url, block.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: GroupNote) {
    val text = when (note) {
        is GroupNote.Failed -> stringResource(R.string.couldnt_reply, note.member, note.error)
        is GroupNote.Waiting -> stringResource(R.string.s_turn_is_on_hold, note.member)
        GroupNote.Stopped -> stringResource(R.string.stopped)
        GroupNote.Capped -> stringResource(R.string.that_message_reached_the_groups_reply)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (note is GroupNote.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ActionStrip(text: String, action: String, onAction: () -> Unit, progress: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HxSpace.screen, vertical = HxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        if (progress) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

/** A member wants to run a command; it waits until the user answers here. */
@Composable
private fun ApprovalCard(
    approval: GroupRooms.Approval,
    memberName: String,
    onAnswer: (allow: Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HxSpace.screen, vertical = HxSpace.xs),
    ) {
        Column(Modifier.padding(HxSpace.md), verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
            Text(
                stringResource(R.string.wants_to_run, memberName),
                style = MaterialTheme.typography.labelLarge,
            )
            if (approval.command.isNotBlank()) {
                Text(
                    approval.command,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (approval.description.isNotBlank()) {
                Text(
                    approval.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                OutlinedButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.deny)) }
                Button(onClick = { onAnswer(true) }) { Text(stringResource(R.string.allow_once_2)) }
            }
        }
    }
}

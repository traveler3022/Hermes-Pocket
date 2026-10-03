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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hermes.android.data.GroupsRepository
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.hxUserBubbleMaxWidth
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.GroupChatViewModel
import com.hermes.android.ui.viewmodel.GroupMentions
import com.hermes.android.ui.viewmodel.GroupNote
import com.hermes.android.ui.viewmodel.GroupRow

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
    LaunchedEffect(state.rows.size) {
        if (state.rows.isNotEmpty()) listState.animateScrollToItem(state.rows.lastIndex)
    }

    val members = state.room?.members.orEmpty()
    HermesScaffold(
        title = state.room?.name ?: t("Group chat", "گفتگوی گروهی"),
        subtitle = members.takeIf { it.isNotEmpty() }?.joinToString("، ") { it.title },
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = t("More", "بیشتر"))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(t("Rename group", "تغییر نام گروه")) },
                        onClick = {
                            menuOpen = false
                            renaming = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(t("Delete group", "حذف گروه"), color = MaterialTheme.colorScheme.error) },
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
                            t(
                                "Write to the group. Everyone answers, or only the members you @mention.",
                                "به گروه پیام بده. همه جواب می‌دهند، یا فقط عضوهایی که با @ صدا بزنی.",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.rows, key = { it.key }) { row ->
                    when (row) {
                        is GroupRow.User -> UserRow(row.text)
                        is GroupRow.Member -> MemberRow(row.name, row.text)
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
                    text = t(
                        "A reply was cut off before it finished. Run that turn again?",
                        "یک جواب نیمه‌کاره ماند. آن نوبت دوباره اجرا شود؟",
                    ),
                    action = t("Try again", "دوباره"),
                    onAction = { viewModel.retry(taskId) },
                )
            }
            if (state.discussing) {
                ActionStrip(
                    text = t("The group is discussing…", "گروه در حال گفت‌وگو است…"),
                    action = t("Stop", "توقف"),
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
                    placeholder = { Text(t("Message the group", "پیام به گروه")) },
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
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = t("Send", "ارسال"))
                    }
                }
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(state.room?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(t("Rename group", "تغییر نام گروه")) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = {
                        renaming = false
                        viewModel.rename(name)
                    },
                    enabled = name.isNotBlank(),
                ) { Text(t("Save", "ذخیره")) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(t("Cancel", "لغو")) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Delete this group?", "این گروه حذف شود؟")) },
            text = {
                Text(
                    t(
                        "Its conversation is deleted from Hermes. The profiles themselves stay.",
                        "گفتگوی آن از هرمس پاک می‌شود. خود پروفایل‌ها می‌مانند.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Cancel", "لغو")) } },
        )
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
private fun MemberRow(name: String, text: String) {
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
            HermesMarkdown(markdown = text)
        }
    }
}

@Composable
private fun NoteRow(note: GroupNote) {
    val text = when (note) {
        is GroupNote.Failed -> t(
            "${note.member} couldn't reply: ${note.error}",
            "${note.member} نتوانست جواب بدهد: ${note.error}",
        )
        is GroupNote.Waiting -> t("${note.member}'s turn is on hold.", "نوبت ${note.member} معطل مانده.")
        GroupNote.Stopped -> t("Stopped.", "متوقف شد.")
        GroupNote.Capped -> t(
            "That message reached the group's reply limit. Send another to continue.",
            "این پیام به سقف جواب‌های گروه رسید. برای ادامه یک پیام دیگر بفرست.",
        )
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
    approval: GroupsRepository.Approval,
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
                t("$memberName wants to run:", "$memberName می‌خواهد این را اجرا کند:"),
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
                OutlinedButton(onClick = { onAnswer(false) }) { Text(t("Deny", "رد")) }
                Button(onClick = { onAnswer(true) }) { Text(t("Allow once", "یک بار اجازه")) }
            }
        }
    }
}

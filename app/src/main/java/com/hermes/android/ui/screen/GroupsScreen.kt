package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.data.GroupsRepository
import com.hermes.android.data.ProfilesRepository
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.icons.filled.Forum
import com.hermes.android.ui.viewmodel.GroupsViewModel

/**
 * Group Chats: rooms where several profiles answer together. Hermes runs each room
 * on the gateway, so a discussion carries on while the app is closed.
 */
@Composable
fun GroupsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenGroup: (roomId: String) -> Unit = {},
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.error) {
        // Errors of the create sheet show inside it.
        if (!creating) {
            state.error?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    HermesScaffold(
        title = t("Group chats", "گفتگوهای گروهی"),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (state.available == true) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.loadProfiles()
                        creating = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(t("New group", "گروه جدید")) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading && state.rooms.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }

                state.available == false -> HermesEmptyState(
                    icon = Icons.Default.Forum,
                    title = t("Group chat is off on this Hermes", "گفتگوی گروهی در این هرمس خاموش است"),
                    caption = t(
                        "This gateway does not run group rooms. Update Hermes (hermes update) and restart it.",
                        "این گیت‌وی اتاق گروهی اجرا نمی‌کند. هرمس را به‌روز کن (hermes update) و دوباره راه بینداز.",
                    ),
                )

                state.rooms.isEmpty() -> HermesEmptyState(
                    icon = Icons.Default.Forum,
                    title = t("No group chats yet", "هنوز گفتگوی گروهی نداری"),
                    caption = t(
                        "Put 2 to 6 profiles in one room: they answer your message together and pull each other in with @name.",
                        "۲ تا ۶ پروفایل را در یک اتاق بگذار: با هم به پیامت جواب می‌دهند و با @نام همدیگر را صدا می‌زنند.",
                    ),
                    actionLabel = if (state.available == true) t("New group", "گروه جدید") else null,
                    onAction = {
                        viewModel.loadProfiles()
                        creating = true
                    },
                )

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(state.rooms, key = { it.roomId }) { room ->
                        GroupRowItem(room) { onOpenGroup(room.roomId) }
                    }
                }
            }
        }
    }

    if (creating) {
        NewGroupSheet(
            profiles = state.profiles,
            isCreating = state.isCreating,
            error = state.error,
            onCreate = { name, members ->
                viewModel.create(name, members) { roomId ->
                    creating = false
                    onOpenGroup(roomId)
                }
            },
            onDismiss = {
                viewModel.clearError()
                creating = false
            },
        )
    }
}

@Composable
private fun GroupRowItem(room: GroupsRepository.Room, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { ProfileAvatar(room.name) },
        headlineContent = { Text(room.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                room.members.joinToString("، ") { it.title },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewGroupSheet(
    profiles: List<ProfilesRepository.Profile>,
    isCreating: Boolean,
    error: String?,
    onCreate: (name: String, members: List<ProfilesRepository.Profile>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val members = profiles.filter { it.name in picked }
    val sizeOk = members.size in GroupsRepository.MIN_MEMBERS..GroupsRepository.MAX_MEMBERS

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.xs),
        ) {
            item { SheetHeader(t("New group", "گروه جدید"), onBack = null) }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("Group name", "نام گروه")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SheetSection(
                    t("Members (${members.size} of 2–6)", "اعضا (${members.size} از ۲ تا ۶)")
                )
            }
            if (profiles.isEmpty()) {
                item { CircularProgressIndicator(Modifier.padding(HxSpace.sm)) }
            } else if (profiles.size < GroupsRepository.MIN_MEMBERS) {
                item {
                    SheetHint(
                        t(
                            "A group needs at least two profiles. Make another one from the drawer's title.",
                            "گروه دست‌کم دو پروفایل لازم دارد. از عنوان کشوی کناری یک پروفایل دیگر بساز.",
                        )
                    )
                }
            }
            items(profiles, key = { it.name }) { profile ->
                val checked = profile.name in picked
                // A seventh pick is refused rather than silently dropping one of the six.
                val enabled = checked || picked.size < GroupsRepository.MAX_MEMBERS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) {
                            picked = if (checked) picked - profile.name else picked + profile.name
                        }
                        .padding(vertical = HxSpace.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                    ProfileAvatar(profile.title, size = 32)
                    Column(Modifier.weight(1f)) {
                        Text(profile.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "@" + GroupsRepository.handleFor(profile),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            error?.let {
                item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                Button(
                    onClick = { onCreate(name.trim(), members) },
                    enabled = name.isNotBlank() && sizeOk && !isCreating,
                    modifier = Modifier.fillMaxWidth().padding(top = HxSpace.sm),
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(t("Create group", "ساخت گروه"))
                    }
                }
            }
        }
    }
}

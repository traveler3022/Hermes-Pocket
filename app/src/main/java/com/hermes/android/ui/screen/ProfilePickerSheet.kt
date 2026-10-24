package com.hermes.android.ui.screen

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.domain.profiles.HermesProfiles
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.viewmodel.ProfilesViewModel

/**
 * Which agent the app talks to, opened from the drawer's title. Picking one
 * starts a new chat in it; the drawer then lists that profile's chats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilePickerSheet(
    state: ProfilesViewModel.UiState,
    active: String?,
    onSelect: (HermesProfiles.Profile) -> Unit,
    onCreate: (name: String, description: String, copyCurrent: Boolean, copyKeys: Boolean) -> Unit,
    onEdit: (HermesProfiles.Profile) -> Unit,
    onSave: (name: String, newName: String, description: String, soul: String, onDone: () -> Unit) -> Unit,
    onDelete: (name: String, onDone: () -> Unit) -> Unit,
    onSetAvatar: (name: String, image: Uri?) -> Unit,
    onCloseEditor: () -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HermesProfiles.Profile?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (creating) {
            NewProfileForm(
                state = state,
                onCreate = onCreate,
                onBack = {
                    onClearError()
                    creating = false
                },
            )
            return@ModalBottomSheet
        }
        editing?.let { profile ->
            ProfileEditor(
                profile = profile,
                state = state,
                isActive = if (active == null) profile.isDefault else profile.name == active,
                // Back to the list once the server has it.
                onSave = { name, newName, description, soul ->
                    onSave(name, newName, description, soul) { editing = null }
                },
                onDelete = { name -> onDelete(name) { editing = null } },
                onSetAvatar = onSetAvatar,
                onBack = {
                    onCloseEditor()
                    editing = null
                },
            )
            return@ModalBottomSheet
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
        ) {
            item { SheetHeader(stringResource(R.string.profiles_2), onBack = null) }
            item {
                SheetHint(
                    stringResource(R.string.each_profile_is_a_separate_agent_with)
                )
            }
            when {
                state.profiles.isEmpty() && state.isLoading -> item {
                    CircularProgressIndicator(modifier = Modifier.padding(HxSpace.sm))
                }
                state.profiles.isEmpty() && state.error != null -> item { SheetHint(state.error) }
                else -> items(state.profiles, key = { it.name }) { profile ->
                    val selected = if (active == null) profile.isDefault else profile.name == active
                    ProfileRow(
                        profile = profile,
                        avatar = state.avatars[profile.name],
                        selected = selected,
                        onClick = { onSelect(profile) },
                        onEdit = {
                            editing = profile
                            onEdit(profile)
                        },
                    )
                }
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { creating = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.new_profile)) },
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: HermesProfiles.Profile,
    avatar: ByteArray?,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { ProfileAvatar(profile.title, image = avatar) },
        headlineContent = {
            Text(
                profile.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        },
        supportingContent = {
            val line = profile.description.ifBlank { profile.model }
            if (line.isNotBlank()) {
                Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectedMark(selected)
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_profile),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** The profile's [image] when it has one (`profiles.set_asset`), else its first letter on a tinted circle. */
@Composable
internal fun ProfileAvatar(title: String, size: Int = 36, image: ByteArray? = null) {
    val bitmap = remember(image) {
        image?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun NewProfileForm(
    state: ProfilesViewModel.UiState,
    onCreate: (name: String, description: String, copyCurrent: Boolean, copyKeys: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var copyCurrent by remember { mutableStateOf(true) }
    var copyKeys by remember { mutableStateOf(true) }
    val valid = HermesProfiles.isValidName(name)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
        verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        item { SheetHeader(stringResource(R.string.new_profile), onBack = onBack) }
        item {
            OutlinedTextField(
                value = name,
                // The name is also a folder and a command on the server: lowercase it as typed.
                onValueChange = { name = it.lowercase().replace(' ', '-') },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                isError = name.isNotEmpty() && !valid,
                supportingText = {
                    Text(
                        stringResource(R.string.english_letters_numbers_and_e_g_writer)
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.what_is_it_for_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { copyCurrent = !copyCurrent }
                    .padding(vertical = HxSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.copy_settings_and_skills_from_this),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = copyCurrent, onCheckedChange = { copyCurrent = it })
            }
        }
        item {
            // A settings copy brings that profile's .env, keys included: the server copies
            // it with config.yaml and has no option to leave it out.
            val keysLocked = copyCurrent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !keysLocked) { copyKeys = !copyKeys }
                    .padding(vertical = HxSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.copy_api_keys))
                    Text(
                        if (keysLocked) {
                            stringResource(R.string.copying_settings_brings_this_profiles)
                        } else {
                            stringResource(R.string.from_the_main_profile_off_the_new)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = keysLocked || copyKeys,
                    onCheckedChange = { copyKeys = it },
                    enabled = !keysLocked,
                )
            }
        }
        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Button(
                onClick = { onCreate(name, description, copyCurrent, copyCurrent || copyKeys) },
                enabled = valid && !state.isCreating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.create_and_switch))
                }
            }
        }
    }
}

/**
 * One profile's settings: name, what it is for, and its SOUL (the persona every chat in
 * it starts from). The model is changed from the chat's model chip while the profile is
 * open, which already writes to the open profile; here it is only shown.
 */
@Composable
private fun ProfileEditor(
    profile: HermesProfiles.Profile,
    state: ProfilesViewModel.UiState,
    isActive: Boolean,
    onSave: (name: String, newName: String, description: String, soul: String) -> Unit,
    onDelete: (name: String) -> Unit,
    onSetAvatar: (name: String, image: Uri?) -> Unit,
    onBack: () -> Unit,
) {
    val details = state.editing
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSetAvatar(profile.name, uri)
    }
    // The default profile's id cannot change; renaming it sets the name it is shown with.
    var newName by remember(details) { mutableStateOf(if (profile.isDefault) profile.title else profile.name) }
    var description by remember(details) { mutableStateOf(details?.description.orEmpty()) }
    var soul by remember(details) { mutableStateOf(details?.soul.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val nameValid = if (profile.isDefault) newName.isNotBlank() else HermesProfiles.isValidName(newName)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
        verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        item { SheetHeader(profile.title, onBack = onBack) }
        if (details == null) {
            item {
                if (state.isLoadingEditor) {
                    CircularProgressIndicator(modifier = Modifier.padding(HxSpace.sm))
                } else {
                    SheetHint(state.error ?: "")
                }
            }
            return@LazyColumn
        }
        item {
            val avatar = state.avatars[profile.name]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ProfileAvatar(newName.ifBlank { profile.title }, size = 56, image = avatar)
                if (state.isSavingAvatar) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = {
                        pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.profile_avatar_choose)) }
                    if (avatar != null) {
                        TextButton(onClick = { onSetAvatar(profile.name, null) }) {
                            Text(stringResource(R.string.profile_avatar_remove))
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = if (profile.isDefault) it else it.lowercase().replace(' ', '-') },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                isError = !nameValid,
                supportingText = if (profile.isDefault) null else {
                    { Text(stringResource(R.string.english_letters_numbers_and)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.what_is_it_for)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = soul,
                onValueChange = { soul = it },
                label = { Text(stringResource(R.string.persona_soul_md)) },
                placeholder = {
                    Text(stringResource(R.string.who_this_agent_is_and_how_it_should_work))
                },
                minLines = 6,
                maxLines = 14,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            val model = details.model.ifBlank { stringResource(R.string.same_as_the_main_profile) }
            SheetHint(
                stringResource(R.string.model_change_it_from_the_chats_model, model)
            )
        }
        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Button(
                onClick = { onSave(profile.name, newName.trim(), description, soul) },
                enabled = nameValid && !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        }
        // The default profile is the Hermes install itself; the CLI refuses to delete it.
        if (!profile.isDefault) {
            item {
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.delete_profile), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_2, profile.title)) },
            text = {
                Text(
                    buildString {
                        append(
                            stringResource(R.string.its_chats_memory_skills_and_settings_are)
                        )
                        if (isActive) {
                            append("\n\n")
                            append(stringResource(R.string.the_app_goes_back_to_the_main_profile))
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(profile.name)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

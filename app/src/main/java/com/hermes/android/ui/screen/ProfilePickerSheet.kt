package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.data.ProfilesRepository
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.i18n.t
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
    onSelect: (ProfilesRepository.Profile) -> Unit,
    onCreate: (name: String, description: String, copyCurrent: Boolean) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

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
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
        ) {
            item { SheetHeader(t("Profiles", "پروفایل‌ها"), onBack = null) }
            item {
                SheetHint(
                    t(
                        "Each profile is a separate agent with its own model, skills, memory and chats.",
                        "هر پروفایل یک عامل جداست، با مدل، مهارت‌ها، حافظه و گفتگوهای خودش.",
                    )
                )
            }
            when {
                state.profiles.isEmpty() && state.isLoading -> item {
                    CircularProgressIndicator(modifier = Modifier.padding(HxSpace.sm))
                }
                state.profiles.isEmpty() && state.error != null -> item { SheetHint(state.error) }
                else -> items(state.profiles, key = { it.name }) { profile ->
                    val selected = if (active == null) profile.isDefault else profile.name == active
                    ProfileRow(profile, selected) { onSelect(profile) }
                }
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { creating = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                    headlineContent = { Text(t("New profile", "پروفایل جدید")) },
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: ProfilesRepository.Profile, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { ProfileAvatar(profile.title) },
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
        trailingContent = { SelectedMark(selected) },
    )
}

/** First letter on a tinted circle, until profiles get real avatars here. */
@Composable
internal fun ProfileAvatar(title: String, size: Int = 36) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun NewProfileForm(
    state: ProfilesViewModel.UiState,
    onCreate: (name: String, description: String, copyCurrent: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var copyCurrent by remember { mutableStateOf(true) }
    val valid = ProfilesRepository.isValidName(name)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
        verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        item { SheetHeader(t("New profile", "پروفایل جدید"), onBack = onBack) }
        item {
            OutlinedTextField(
                value = name,
                // The name is also a folder and a command on the server: lowercase it as typed.
                onValueChange = { name = it.lowercase().replace(' ', '-') },
                label = { Text(t("Name", "نام")) },
                singleLine = true,
                isError = name.isNotEmpty() && !valid,
                supportingText = {
                    Text(
                        t(
                            "English letters, numbers, - and _ (e.g. writer)",
                            "حروف انگلیسی، عدد، - و _ (مثلاً writer)",
                        )
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
                label = { Text(t("What is it for? (optional)", "برای چه کاری؟ (اختیاری)")) },
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
                    t("Copy settings and skills from this profile", "کپی تنظیمات و مهارت‌ها از پروفایل فعلی"),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = copyCurrent, onCheckedChange = { copyCurrent = it })
            }
        }
        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Button(
                onClick = { onCreate(name, description, copyCurrent) },
                enabled = valid && !state.isCreating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(t("Create and switch", "بساز و برو به آن"))
                }
            }
        }
    }
}

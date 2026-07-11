package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.GroupDivider
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.SkillItem
import com.hermes.android.ui.viewmodel.SkillsViewModel

/**
 * Skills Browser — list, search, inspect, add/edit/delete, install from hub.
 * Rebuilt on the design system: one grouped list of rows (tap to inspect),
 * per-row overflow menu instead of three always-visible inline buttons.
 *
 * Depends ONLY on [SkillsViewModel].
 */
@Composable
fun SkillsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SkillsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (uiState.inspectedSkillName != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissInspect() },
            title = { Text(uiState.inspectedSkillName ?: "") },
            text = { Text(uiState.inspectedSkillDetail ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissInspect() }) { Text(t("Close", "بستن")) }
            },
        )
    }

    // Manual add/edit — skills.manage has no create/edit RPC, so this reads
    // and writes ~/.hermes/skills/<name>.md directly (see SkillsViewModel).
    if (uiState.editingSkillName != null) {
        var nameField by remember(uiState.editingSkillOriginalName) {
            mutableStateOf(uiState.editingSkillOriginalName ?: "")
        }
        var contentField by remember(uiState.editingSkillContent) {
            mutableStateOf(uiState.editingSkillContent)
        }
        val isNew = uiState.editingSkillOriginalName == null
        AlertDialog(
            onDismissRequest = { viewModel.dismissSkillEditor() },
            title = { Text(if (isNew) t("New Skill", "مهارت جدید") else t("Edit Skill", "ویرایش مهارت")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                    OutlinedTextField(
                        value = nameField,
                        onValueChange = { nameField = it },
                        label = { Text(t("Name", "نام")) },
                        singleLine = true,
                        enabled = isNew,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (uiState.isLoadingSkillContent) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        OutlinedTextField(
                            value = contentField,
                            onValueChange = { contentField = it },
                            label = { Text(t("Content (Markdown)", "محتوا (مارک‌داون)")) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            minLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.saveSkill(nameField, contentField) },
                    enabled = nameField.isNotBlank(),
                ) { Text(t("Save", "ذخیره")) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSkillEditor() }) { Text(t("Cancel", "انصراف")) }
            },
        )
    }

    HermesScaffold(
        title = t("Skills", "مهارت‌ها"),
        subtitle = if (uiState.skills.isEmpty()) null else {
            t("${uiState.skills.size} installed", "${uiState.skills.size} نصب‌شده")
        },
        onBack = onNavigateBack,
        actions = {
            IconButton(onClick = { viewModel.reloadSkills() }) {
                Icon(Icons.Default.Refresh, contentDescription = t("Reload", "بارگذاری مجدد"))
            }
            IconButton(onClick = { viewModel.startNewSkill() }) {
                Icon(Icons.Default.Add, contentDescription = t("New skill", "مهارت جدید"))
            }
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // skills.manage supports "search" server-side; typing here queries
            // the hub, not just the local list.
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchSkills(it)
                },
                placeholder = { Text(t("Search skills...", "جستجوی مهارت‌ها...")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HxSpace.screen, vertical = HxSpace.sm),
                singleLine = true,
                shape = RoundedCornerShape(50),
            )

            when {
                uiState.isLoading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(HxSpace.sm))
                    Text(
                        t("Loading skills…", "در حال بارگذاری مهارت‌ها…"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                uiState.skills.isEmpty() -> HermesEmptyState(
                    icon = Icons.Default.AutoAwesome,
                    title = t("No skills available", "مهارتی موجود نیست"),
                    caption = t(
                        "Skills teach the agent new abilities — search the hub or create one",
                        "مهارت‌ها توانایی‌های جدید به ایجنت یاد می‌دن — از مخزن جستجو کن یا خودت بساز",
                    ),
                    actionLabel = t("New skill", "مهارت جدید"),
                    onAction = { viewModel.startNewSkill() },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = HxSpace.sm, bottom = HxSpace.xl),
                ) {
                    item {
                        SettingsGroup {
                            uiState.skills.forEachIndexed { index, skill ->
                                if (index > 0) GroupDivider()
                                SkillRow(skill, viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillRow(skill: SkillItem, viewModel: SkillsViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    SettingRow(
        title = skill.name,
        subtitle = skill.category.takeIf { it.isNotBlank() },
        icon = Icons.Default.AutoAwesome,
        onClick = { viewModel.inspectSkill(skill.name) },
        trailing = {
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = t("Options", "گزینه‌ها"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(t("Edit", "ویرایش")) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { viewModel.startEditSkill(skill.name); menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text(t("Install from hub", "نصب از مخزن")) },
                        leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                        onClick = { viewModel.installSkill(skill.name); menuExpanded = false },
                    )
                    DropdownMenuItem(
                        text = { Text(t("Delete", "حذف"), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = { viewModel.deleteSkill(skill.name); menuExpanded = false },
                    )
                }
            }
        },
    )
}

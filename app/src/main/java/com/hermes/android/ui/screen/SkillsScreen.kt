package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.SkillItem
import com.hermes.android.ui.viewmodel.SkillsViewModel

/**
 * Skills Browser screen — list, enable/disable, reload skills.
 *
 * Depends ONLY on [SkillsViewModel] — never on gateway or runtime.
 *
 * Reference: Phase 1.5 Rule 1
 */
@OptIn(ExperimentalMaterial3Api::class)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Skills", "مهارت‌ها")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "بازگشت"))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reloadSkills() }) {
                        Icon(Icons.Default.Refresh, contentDescription = t("Reload", "بارگذاری مجدد"))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startNewSkill() }) {
                Icon(Icons.Default.Add, contentDescription = t("New skill", "مهارت جدید"))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Fix: skills.manage supports "search" (server.py:12224) but the
            // screen only ever called list/install — there was no way to find
            // a skill you didn't already know the exact name of.
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchSkills(it)
                },
                label = { Text("Search skills") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )

            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text("Loading skills…", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (uiState.skills.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No skills available", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.skills, key = { it.name }) { skill ->
                        SkillCard(skill, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillCard(skill: SkillItem, viewModel: SkillsViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.inspectSkill(skill.name) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = t("category: ${skill.category}", "دسته: ${skill.category}"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = { viewModel.startEditSkill(skill.name) }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = t("Edit", "ویرایش"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { viewModel.deleteSkill(skill.name) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = t("Delete", "حذف"),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            // "Install" pulls this named skill from the remote skills hub —
            // distinct from Edit, which changes the local file directly.
            androidx.compose.material3.TextButton(
                onClick = { viewModel.installSkill(skill.name) },
            ) {
                Text(t("Install from hub", "نصب از مخزن"))
            }
        }
    }
}

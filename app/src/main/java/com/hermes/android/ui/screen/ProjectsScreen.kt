package com.hermes.android.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.domain.projects.Projects
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.viewmodel.ProjectsViewModel

@Composable
fun ProjectsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onNewSession: (String) -> Unit = {},
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.createdSessionId) {
        uiState.createdSessionId?.let { sid ->
            viewModel.clearCreatedSessionId()
            onNewSession(sid)
        }
    }

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Projects.ProjectSummary?>(null) }
    var showAddFolder by rememberSaveable { mutableStateOf(false) }

    val detail = uiState.selectedProject
    // Back from a project returns to the project list, like the top bar's arrow.
    BackHandler(enabled = detail != null) { viewModel.closeProject() }
    HermesScaffold(
        title = detail?.label ?: stringResource(R.string.projects),
        onBack = if (detail != null) { { viewModel.closeProject() } } else onNavigateBack,
        actions = {
            val path = uiState.selectedProjectPath
            if (detail != null && path != null) {
                IconButton(onClick = { viewModel.createProjectSession(path) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_chat_2))
                }
            } else if (detail == null) {
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_project))
                }
            }
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        when {
            detail != null -> ProjectDetailList(
                padding = padding,
                detail = detail,
                facts = uiState.facts,
                stored = uiState.selectedStored,
                isLoading = uiState.isLoadingDetail,
                onOpenSession = onOpenSession,
                onAddFolder = { showAddFolder = true },
                onRemoveFolder = viewModel::removeFolder,
            )
            uiState.isLoading && uiState.projects.isEmpty() -> Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            uiState.projects.isEmpty() && uiState.archived.isEmpty() -> Column(modifier = Modifier.padding(padding)) {
                HermesEmptyState(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.no_projects_yet),
                    caption = stringResource(R.string.projects_are_grouped_automatically_from),
                )
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = HxSpace.screen, end = HxSpace.screen,
                    top = HxSpace.sm, bottom = HxSpace.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                items(uiState.projects, key = { it.id }) { project ->
                    Surface(
                        shape = RoundedCornerShape(HxRadius.md),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.openProject(project) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f).padding(HxSpace.md)) {
                                Text(
                                    project.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(R.string.sessions_2, project.sessionCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Only projects the user made can be renamed or archived; automatic ones follow the chats.
                            if (project.isStored) {
                                ProjectMenu(
                                    onRename = { renaming = project },
                                    onArchive = { viewModel.archiveProject(project.id, restore = false) },
                                )
                            }
                        }
                    }
                }
                if (uiState.archived.isNotEmpty()) {
                    item(key = "archived_header") {
                        Text(
                            stringResource(R.string.archived),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = HxSpace.md, start = HxSpace.xs),
                        )
                    }
                    items(uiState.archived, key = { "archived_" + it.id }) { project ->
                        Surface(
                            shape = RoundedCornerShape(HxRadius.md),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(start = HxSpace.md, end = HxSpace.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    project.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.archiveProject(project.id, restore = true) }) {
                                    Text(stringResource(R.string.unarchive))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by rememberSaveable { mutableStateOf("") }
        var folder by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.new_project)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FolderField(folder) { folder = it }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createProject(name, folder)
                        showCreate = false
                    },
                    enabled = name.isNotBlank() && folder.isNotBlank(),
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel_2)) }
            },
        )
    }

    renaming?.let { project ->
        var name by remember(project.id) { mutableStateOf(project.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename_project)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameProject(project.id, name)
                        renaming = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel_2)) }
            },
        )
    }

    if (showAddFolder && uiState.selectedStored != null) {
        var folder by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFolder = false },
            title = { Text(stringResource(R.string.add_folder)) },
            text = { FolderField(folder) { folder = it } },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addFolder(folder)
                        showAddFolder = false
                    },
                    enabled = folder.isNotBlank(),
                ) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolder = false }) { Text(stringResource(R.string.cancel_2)) }
            },
        )
    }
}

/** A path on the server (where Hermes runs), not on the phone. */
@Composable
private fun FolderField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.project_folder_on_server)) },
        placeholder = { Text(stringResource(R.string.project_folder_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProjectMenu(onRename: () -> Unit, onArchive: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                onClick = {
                    open = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.archive)) },
                onClick = {
                    open = false
                    onArchive()
                },
            )
        }
    }
}

@Composable
private fun ProjectDetailList(
    padding: PaddingValues,
    detail: Projects.ProjectDetail,
    facts: String?,
    stored: Projects.StoredProject?,
    isLoading: Boolean,
    onOpenSession: (String) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
) {
    if (isLoading) {
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(
            start = HxSpace.screen, end = HxSpace.screen,
            top = HxSpace.sm, bottom = HxSpace.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        if (!facts.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(HxRadius.md),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        facts,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(HxSpace.md),
                    )
                }
            }
        }
        if (stored != null) {
            item(key = "folders_header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.folders),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = HxSpace.xs),
                    )
                    TextButton(onClick = onAddFolder) { Text(stringResource(R.string.add_folder)) }
                }
            }
            items(stored.folders, key = { "folder_$it" }) { folder ->
                Surface(
                    shape = RoundedCornerShape(HxRadius.md),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = HxSpace.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = HxSpace.sm)) {
                            Text(
                                folder,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (folder == stored.primaryPath) {
                                Text(
                                    stringResource(R.string.project_primary_folder),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        IconButton(onClick = { onRemoveFolder(folder) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }
            }
        }
        if (detail.sessions.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_sessions_in_this_project),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(HxSpace.md),
                )
            }
        }
        items(detail.sessions, key = { it.id }) { session ->
            Surface(
                shape = RoundedCornerShape(HxRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenSession(session.id) },
            ) {
                Column(modifier = Modifier.padding(HxSpace.md)) {
                    Text(
                        session.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (session.preview.isNotBlank()) {
                        Text(
                            session.preview,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

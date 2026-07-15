package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.data.ProjectsRepository
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.i18n.t
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

    val detail = uiState.selectedProject
    HermesScaffold(
        title = detail?.label ?: t("Projects", "پروژه‌ها"),
        onBack = if (detail != null) { { viewModel.closeProject() } } else onNavigateBack,
        actions = if (detail != null && uiState.selectedProjectPath != null) {
            {
                IconButton(onClick = { viewModel.createProjectSession(uiState.selectedProjectPath!!) }) {
                    Icon(Icons.Default.Add, contentDescription = t("New chat", "چت جدید"))
                }
            }
        } else { {} },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        when {
            detail != null -> ProjectDetailList(
                padding = padding,
                detail = detail,
                facts = uiState.facts,
                isLoading = uiState.isLoadingDetail,
                onOpenSession = onOpenSession,
            )
            uiState.isLoading && uiState.projects.isEmpty() -> Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            uiState.projects.isEmpty() -> Column(modifier = Modifier.padding(padding)) {
                HermesEmptyState(
                    icon = Icons.Default.Folder,
                    title = t("No projects yet", "هنوز پروژه‌ای نیست"),
                    caption = t(
                        "Projects are grouped automatically from where your sessions run",
                        "پروژه‌ها خودکار از محل اجرای گفتگوهات گروه‌بندی می‌شن",
                    ),
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
                        Column(modifier = Modifier.padding(HxSpace.md)) {
                            Text(
                                project.label,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                t(
                                    "${project.sessionCount} sessions",
                                    "${project.sessionCount} گفتگو",
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectDetailList(
    padding: PaddingValues,
    detail: ProjectsRepository.ProjectDetail,
    facts: String?,
    isLoading: Boolean,
    onOpenSession: (String) -> Unit,
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
        if (detail.sessions.isEmpty()) {
            item {
                Text(
                    t("No sessions in this project", "گفتگویی توی این پروژه نیست"),
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

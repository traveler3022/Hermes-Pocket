package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.session.ForeignSession
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.icons.filled.History
import com.hermes.android.ui.viewmodel.ForeignSessionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Claude Code and Codex CLI sessions on the machine Hermes runs on: look at one, then import it
 * into Hermes' history and keep going there.
 */
@Composable
fun ForeignSessionsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    viewModel: ForeignSessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ForeignSessionsViewModel.Event.OpenChat -> onOpenSession(event.sessionId)
                is ForeignSessionsViewModel.Event.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    HermesScaffold(
        title = stringResource(R.string.foreign_sessions_title),
        subtitle = state.host.takeIf { it.isNotBlank() }?.let { stringResource(R.string.foreign_sessions_on_host, it) },
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = viewModel::load, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = HxSpace.screen, vertical = HxSpace.xs),
            ) {
                listOf(
                    null to stringResource(R.string.foreign_sessions_all),
                    "claude" to "Claude Code",
                    "codex" to "Codex CLI",
                ).forEach { (source, label) ->
                    FilterChip(
                        selected = state.source == source,
                        onClick = { viewModel.setSource(source) },
                        label = { Text(label) },
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading && state.sessions.isEmpty() ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.loadError != null -> HermesEmptyState(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.foreign_sessions_title),
                        caption = state.loadError,
                        actionLabel = stringResource(R.string.reload),
                        onAction = viewModel::load,
                    )
                    state.sessions.isEmpty() -> HermesEmptyState(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.foreign_sessions_empty),
                        caption = stringResource(R.string.foreign_sessions_empty_caption),
                    )
                    else -> ForeignList(state, viewModel)
                }
            }
        }
    }

    state.selected?.let { ForeignPreviewSheet(it, state, viewModel) }
}

@Composable
private fun ForeignList(state: ForeignSessionsViewModel.UiState, viewModel: ForeignSessionsViewModel) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, top = HxSpace.sm, bottom = HxSpace.xl),
        verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        if (state.unreadable > 0) {
            item(key = "unreadable") {
                Text(
                    text = stringResource(R.string.foreign_sessions_unreadable, state.unreadable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Prefixed so a handle can never match the keys of the other rows.
        items(state.sessions, key = { "fs:${it.id}" }) { session ->
            ForeignRow(session, dateFormat, onClick = { viewModel.open(session) })
        }
        if (state.nextOffset != null) {
            item(key = "more") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.loadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        TextButton(onClick = viewModel::loadMore) { Text(stringResource(R.string.foreign_sessions_more)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForeignRow(session: ForeignSession, dateFormat: SimpleDateFormat, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(HxRadius.md)).clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = HxSpace.inner, vertical = HxSpace.md)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (session.excerpt.isNotBlank() && session.excerpt != session.title) {
                Text(
                    text = session.excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val meta = buildList {
                add(session.label)
                add("${session.turnCount} ${stringResource(R.string.messages)}")
                if (session.modifiedAt > 0) add(dateFormat.format(Date(session.modifiedAt * 1000)))
            }.joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 3.dp),
            )
            session.cwd?.let { cwd ->
                Text(
                    text = cwd,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForeignPreviewSheet(
    session: ForeignSession,
    state: ForeignSessionsViewModel.UiState,
    viewModel: ForeignSessionsViewModel,
) {
    ModalBottomSheet(
        onDismissRequest = viewModel::closePreview,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = HxSpace.screen)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(session.label, state.preview?.cwd ?: session.cwd).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = HxSpace.sm),
            )
            val preview = state.preview
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).weight(1f, fill = false)) {
                when {
                    state.previewError != null -> Text(
                        text = state.previewError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    preview == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                        if (preview.truncated) {
                            item(key = "truncated") {
                                Text(
                                    text = stringResource(R.string.foreign_sessions_last_of, preview.messages.size, preview.total),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(preview.messages.size) { index ->
                            val message = preview.messages[index]
                            val user = message.role == "user"
                            Surface(
                                shape = RoundedCornerShape(HxRadius.md),
                                color = if (user) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(HxSpace.md)) {
                                    Text(
                                        text = if (user) stringResource(R.string.foreign_sessions_you) else session.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = message.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 12,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val imported = preview?.importedSessionId != null
            Button(
                onClick = viewModel::importSelected,
                enabled = !state.importing && state.previewError == null,
                modifier = Modifier.fillMaxWidth().padding(vertical = HxSpace.md),
            ) {
                if (state.importing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(if (imported) R.string.foreign_sessions_open else R.string.foreign_sessions_import))
                }
            }
        }
    }
}

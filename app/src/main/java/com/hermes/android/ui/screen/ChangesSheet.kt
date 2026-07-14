package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChangesViewModel

/**
 * "Changes" sheet — checkpoint diff/restore for the current chat session
 * (rollback.list/diff/restore, session.undo). Opened over ChatScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesSheet(
    sessionId: String,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    viewModel: ChangesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.lastActionMessage) {
        uiState.lastActionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HxSpace.screen)
                .padding(bottom = HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(t("Changes", "تغییرات"), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.undoLastTurn() }) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.height(0.dp))
                    Text(t("Undo last turn", "واگرد آخرین نوبت"))
                }
            }

            when {
                uiState.selectedDiff != null && uiState.selectedHash != null -> DiffView(
                    diff = uiState.selectedDiff!!,
                    hash = uiState.selectedHash!!,
                    isRestoring = uiState.isRestoring,
                    onBack = { viewModel.closeDiff() },
                    onRestore = { hash -> viewModel.restore(hash) },
                )
                uiState.isLoadingDiff -> LoadingRow()
                uiState.isLoading -> LoadingRow()
                uiState.checkpoints.isEmpty() -> HermesEmptyState(
                    icon = Icons.Default.Undo,
                    title = t("No checkpoints yet", "هنوز هیچ چک‌پوینتی نیست"),
                    caption = t(
                        "Checkpoints appear here after the agent edits files",
                        "بعد از این‌که ایجنت فایلی رو ویرایش کنه، چک‌پوینت‌ها اینجا میان",
                    ),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
                ) {
                    items(uiState.checkpoints, key = { it.hash }) { cp ->
                        Surface(
                            shape = RoundedCornerShape(HxRadius.md),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.viewDiff(cp.hash) },
                        ) {
                            Column(modifier = Modifier.padding(HxSpace.md)) {
                                Text(
                                    cp.message.ifBlank { cp.hash.take(8) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    cp.timestamp,
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
}

@Composable
private fun DiffView(
    diff: com.hermes.android.data.SessionRepository.CheckpointDiff,
    hash: String,
    isRestoring: Boolean,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
        if (diff.stat.isNotBlank()) {
            Text(
                diff.stat,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(HxRadius.md),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = diff.rendered ?: diff.diff.ifBlank { t("No changes", "بدون تغییر") },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(HxSpace.md).heightIn(max = 320.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
            TextButton(onClick = onBack) { Text(t("Back", "بازگشت")) }
            TextButton(
                onClick = { onRestore(hash) },
                enabled = !isRestoring,
            ) { Text(if (isRestoring) t("Restoring…", "در حال بازگردانی…") else t("Restore this version", "بازگردانی به این نسخه")) }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(HxSpace.lg)) { CircularProgressIndicator() }
}

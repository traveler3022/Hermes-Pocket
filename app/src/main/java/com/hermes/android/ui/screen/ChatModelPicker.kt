package com.hermes.android.ui.screen

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.viewmodel.ChatUiState
import com.hermes.android.ui.viewmodel.ModelPickerViewModel

/**
 * The chat's model picker wiring, kept out of ChatScreen: follows the open
 * chat's session.info, loads the catalogue when the sheet opens, and shows
 * switch notices, the confirm dialog and the sheet.
 */
@Composable
internal fun ChatModelPicker(
    viewModel: ModelPickerViewModel,
    chat: ChatUiState,
    showSheet: Boolean,
    snackbarHostState: SnackbarHostState,
    onReasoningLevelChange: (String) -> Unit,
    onDismissSheet: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(chat.sessionInfoSeq) {
        chat.sessionModel?.let { viewModel.onSessionModel(chat.activeSessionId, it, chat.sessionProvider) }
    }
    LaunchedEffect(showSheet) {
        if (showSheet) viewModel.load(chat.activeSessionId)
    }
    LaunchedEffect(state.notice) {
        state.notice?.let { notice ->
            // Clear AFTER showing: clearing changes this effect's key and would cancel the snackbar.
            snackbarHostState.showSnackbar(message = notice, duration = SnackbarDuration.Short)
            viewModel.clearNotice()
        }
    }

    state.confirm?.let { ModelSwitchConfirmDialog(it, viewModel::confirmSwitch, viewModel::dismissConfirm) }

    if (showSheet) {
        ModelPickerSheet(
            models = state.models,
            favorites = state.favorites,
            activeModel = state.activeModel,
            activeProvider = state.activeProvider,
            reasoningLevel = chat.reasoningLevel,
            isLoading = state.isLoading,
            onSelectModel = {
                viewModel.select(it, chat.activeSessionId, chat.activeSessionKey)
                onDismissSheet()
            },
            onToggleFavorite = viewModel::toggleFavorite,
            onReasoningLevelChange = onReasoningLevelChange,
            onDismiss = onDismissSheet,
        )
    }
}

package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatUiState
import com.hermes.android.ui.viewmodel.ModelPickerViewModel

/**
 * The composer's model button: the running model's short name in a quiet
 * pill — no fill of its own, secondary text colour — so it reads as a control
 * of the message box, never heavier than the conversation. Tap opens the sheet.
 */
@Composable
internal fun ComposerModelButton(activeModel: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 160.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = activeModel?.takeIf { it.isNotBlank() }?.let(::shortModelName) ?: t("Model", "مدل"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = t("Change model", "تغییر مدل"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

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

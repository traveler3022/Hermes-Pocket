package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.domain.messaging.TelegramApplyResult
import com.hermes.android.ui.viewmodel.TelegramSetupUiState
import com.hermes.android.ui.viewmodel.TelegramSetupViewModel
import com.hermes.android.ui.viewmodel.TelegramStep

/**
 * The Telegram wizard, step by step: make the bot in Telegram, pick who may talk to it, save.
 * Hy4ri/hermes-mobile's dialog asks to scan a QR it never draws; on a phone the link opens
 * Telegram directly, and it can be copied for another device.
 */
@Composable
internal fun TelegramSetupDialog(state: TelegramSetupUiState, viewModel: TelegramSetupViewModel) {
    val step = state.step
    if (step is TelegramStep.Idle && state.error == null && !state.expired) return
    val busy = step is TelegramStep.Starting || step is TelegramStep.Saving
    AlertDialog(
        onDismissRequest = { if (!busy) viewModel.close() },
        title = {
            Text(
                when (step) {
                    TelegramStep.Idle -> stringResource(R.string.telegram_setup)
                    TelegramStep.Starting -> stringResource(R.string.starting_2)
                    is TelegramStep.Waiting -> stringResource(R.string.step_1_make_the_bot)
                    is TelegramStep.Ready -> stringResource(R.string.step_2_who_may_use_it)
                    is TelegramStep.Saving -> stringResource(R.string.saving)
                    is TelegramStep.Done -> stringResource(R.string.telegram_connected)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.expired) {
                    Text(
                        stringResource(R.string.the_setup_link_expired_before_the_bot),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when (step) {
                    TelegramStep.Starting, is TelegramStep.Saving -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is TelegramStep.Waiting -> WaitingStep(step)
                    is TelegramStep.Ready -> ReadyStep(step, state, viewModel)
                    is TelegramStep.Done -> DoneStep(step)
                    TelegramStep.Idle -> Unit
                }
            }
        },
        confirmButton = {
            when (step) {
                is TelegramStep.Ready -> Button(onClick = { viewModel.apply() }, enabled = state.allowedIds.isNotEmpty()) {
                    Text(stringResource(R.string.save_and_restart))
                }
                TelegramStep.Idle -> TextButton(onClick = { viewModel.start() }) { Text(stringResource(R.string.try_again)) }
                is TelegramStep.Done -> TextButton(onClick = { viewModel.close() }) { Text(stringResource(R.string.done_2)) }
                else -> Unit
            }
        },
        dismissButton = {
            if (!busy && step !is TelegramStep.Done) {
                TextButton(onClick = { viewModel.close() }) { Text(stringResource(R.string.cancel_2)) }
            }
        },
    )
}

@Composable
private fun WaitingStep(step: TelegramStep.Waiting) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val link = step.pairing.deepLink
    Text(
        stringResource(
            R.string.telegram_open_and_create,
            if (step.pairing.suggestedUsername.isNotEmpty()) {
                stringResource(R.string.telegram_suggested_name, step.pairing.suggestedUsername)
            } else {
                ""
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (link.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { runCatching { uriHandler.openUri(link) } }) { Text(stringResource(R.string.open_telegram)) }
            TextButton(onClick = { clipboard.setText(AnnotatedString(link)) }) { Text(stringResource(R.string.copy_link)) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.waiting_for_telegram),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadyStep(step: TelegramStep.Ready, state: TelegramSetupUiState, viewModel: TelegramSetupViewModel) {
    var newId by remember { mutableStateOf("") }
    step.botUsername?.let {
        Text(stringResource(R.string.bot_made, it), style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        stringResource(R.string.only_these_telegram_user_ids_may_talk_to),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.allowedIds.forEach { id ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (id == step.ownerUserId) stringResource(R.string.you_2, id) else id,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.removeAllowedId(id) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), modifier = Modifier.size(18.dp))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newId,
            onValueChange = { value -> newId = value.filter { it.isDigit() } },
            placeholder = { Text(stringResource(R.string.telegram_user_id)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { if (viewModel.addAllowedId(newId)) newId = "" }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
        }
    }
}

@Composable
private fun DoneStep(step: TelegramStep.Done) {
    val bot = step.botUsername?.let { "@$it" } ?: stringResource(R.string.the_bot)
    Text(
        when (val result = step.result) {
            TelegramApplyResult.Done -> stringResource(R.string.is_saved, bot)
            TelegramApplyResult.Restarting -> stringResource(R.string.is_saved_and_the_messaging_gateway_is, bot)
            is TelegramApplyResult.NeedsRestart -> stringResource(
                R.string.telegram_saved_restart_failed,
                bot,
                result.reason?.let { ": $it" } ?: "",
            )
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hermes.android.domain.messaging.TelegramSaveResult
import com.hermes.android.ui.viewmodel.TelegramSetupUiState
import com.hermes.android.ui.viewmodel.TelegramSetupViewModel
import com.hermes.android.ui.viewmodel.TelegramStep
import com.hermes.android.R
import androidx.compose.ui.res.stringResource

/**
 * Connects the user's own Telegram bot in two steps: the token from @BotFather, then the
 * numeric user ids allowed to talk to it (as many as wanted). Saved straight to the server.
 */
@Composable
internal fun TelegramSetupDialog(state: TelegramSetupUiState, viewModel: TelegramSetupViewModel) {
    val step = state.step
    if (step == TelegramStep.Closed) return
    AlertDialog(
        onDismissRequest = { viewModel.close() },
        title = {
            Text(
                when (step) {
                    TelegramStep.Closed, TelegramStep.Token -> stringResource(R.string.step_1_bot_token)
                    TelegramStep.Users -> stringResource(R.string.step_2_allowed_users)
                    TelegramStep.Saving -> stringResource(R.string.saving)
                    is TelegramStep.Done -> stringResource(R.string.telegram_connected)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                when (step) {
                    TelegramStep.Closed -> Unit
                    TelegramStep.Token -> TokenStep(state, viewModel)
                    TelegramStep.Users -> UsersStep(state, viewModel)
                    TelegramStep.Saving -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is TelegramStep.Done -> DoneStep(step.result)
                }
            }
        },
        confirmButton = {
            when (step) {
                TelegramStep.Token -> Button(onClick = { viewModel.toUsers() }, enabled = viewModel.tokenLooksRight(state.token)) {
                    Text(stringResource(R.string.next_2))
                }
                TelegramStep.Users -> Button(onClick = { viewModel.save() }, enabled = state.allowedIds.isNotEmpty()) {
                    Text(stringResource(R.string.save))
                }
                is TelegramStep.Done -> TextButton(onClick = { viewModel.close() }) { Text(stringResource(R.string.done_2)) }
                else -> Unit
            }
        },
        dismissButton = {
            when (step) {
                TelegramStep.Token -> TextButton(onClick = { viewModel.close() }) { Text(stringResource(R.string.cancel_2)) }
                TelegramStep.Users -> TextButton(onClick = { viewModel.backToToken() }) { Text(stringResource(R.string.back_3)) }
                else -> Unit
            }
        },
    )
}

@Composable
private fun TokenStep(state: TelegramSetupUiState, viewModel: TelegramSetupViewModel) {
    val uriHandler = LocalUriHandler.current
    Text(
        stringResource(R.string.in_telegram_message_botfather_send),
        style = MaterialTheme.typography.bodyMedium,
    )
    TextButton(onClick = { runCatching { uriHandler.openUri("https://t.me/BotFather") } }) {
        Text(stringResource(R.string.open_botfather))
    }
    OutlinedTextField(
        value = state.token,
        onValueChange = { viewModel.setToken(it) },
        label = { Text(stringResource(R.string.bot_token_2)) },
        placeholder = { Text("123456789:ABC…") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun UsersStep(state: TelegramSetupUiState, viewModel: TelegramSetupViewModel) {
    var newId by remember { mutableStateOf("") }
    Text(
        stringResource(R.string.only_these_numeric_telegram_user_ids_may),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.allowedIds.forEach { id ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(id, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.removeAllowedId(id) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), modifier = Modifier.size(18.dp))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newId,
            onValueChange = { value -> newId = value.filter { it.isDigit() } },
            placeholder = { Text(stringResource(R.string.numeric_user_id)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { if (viewModel.addAllowedId(newId)) newId = "" }, enabled = newId.isNotEmpty()) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
        }
    }
}

@Composable
private fun DoneStep(result: TelegramSaveResult) {
    Text(
        when (result) {
            TelegramSaveResult.Live -> stringResource(R.string.the_bot_is_saved_and_already_running)
            TelegramSaveResult.Restarting -> stringResource(R.string.the_bot_is_saved_and_the_messaging)
            is TelegramSaveResult.NeedsRestart -> stringResource(
                R.string.telegram_bot_saved_restart_failed,
                result.reason?.let { ": $it" } ?: "",
            )
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

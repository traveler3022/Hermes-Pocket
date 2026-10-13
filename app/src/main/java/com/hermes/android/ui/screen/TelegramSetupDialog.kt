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
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.TelegramSetupUiState
import com.hermes.android.ui.viewmodel.TelegramSetupViewModel
import com.hermes.android.ui.viewmodel.TelegramStep

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
                    TelegramStep.Closed, TelegramStep.Token -> t("Step 1: bot token", "قدم ۱: توکن ربات")
                    TelegramStep.Users -> t("Step 2: allowed users", "قدم ۲: کاربران مجاز")
                    TelegramStep.Saving -> t("Saving…", "در حال ذخیره…")
                    is TelegramStep.Done -> t("Telegram connected", "تلگرام وصل شد")
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
                    Text(t("Next", "بعدی"))
                }
                TelegramStep.Users -> Button(onClick = { viewModel.save() }, enabled = state.allowedIds.isNotEmpty()) {
                    Text(t("Save", "ذخیره"))
                }
                is TelegramStep.Done -> TextButton(onClick = { viewModel.close() }) { Text(t("Done", "تمام")) }
                else -> Unit
            }
        },
        dismissButton = {
            when (step) {
                TelegramStep.Token -> TextButton(onClick = { viewModel.close() }) { Text(t("Cancel", "انصراف")) }
                TelegramStep.Users -> TextButton(onClick = { viewModel.backToToken() }) { Text(t("Back", "قبلی")) }
                else -> Unit
            }
        },
    )
}

@Composable
private fun TokenStep(state: TelegramSetupUiState, viewModel: TelegramSetupViewModel) {
    val uriHandler = LocalUriHandler.current
    Text(
        t(
            "In Telegram, message @BotFather, send /newbot and follow its steps. Paste the token it gives you here.",
            "در تلگرام به ‎@BotFather پیام بده، ‎/newbot را بفرست و مراحلش را برو. توکنی که می‌دهد را اینجا بزن.",
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    TextButton(onClick = { runCatching { uriHandler.openUri("https://t.me/BotFather") } }) {
        Text(t("Open @BotFather", "باز کردن ‎@BotFather"))
    }
    OutlinedTextField(
        value = state.token,
        onValueChange = { viewModel.setToken(it) },
        label = { Text(t("Bot token", "توکن ربات")) },
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
        t(
            "Only these numeric Telegram user ids may talk to the agent. Add as many as you want (your own id: message @userinfobot).",
            "فقط این شناسه‌های عددی تلگرام می‌توانند با ایجنت حرف بزنند. هر چندتا خواستی اضافه کن (شناسهٔ خودت: به ‎@userinfobot پیام بده).",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.allowedIds.forEach { id ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(id, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.removeAllowedId(id) }) {
                Icon(Icons.Default.Close, contentDescription = t("Remove", "حذف"), modifier = Modifier.size(18.dp))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newId,
            onValueChange = { value -> newId = value.filter { it.isDigit() } },
            placeholder = { Text(t("Numeric user id", "شناسهٔ عددی")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { if (viewModel.addAllowedId(newId)) newId = "" }, enabled = newId.isNotEmpty()) {
            Icon(Icons.Default.Add, contentDescription = t("Add", "افزودن"))
        }
    }
}

@Composable
private fun DoneStep(result: TelegramSaveResult) {
    Text(
        when (result) {
            TelegramSaveResult.Live -> t("The bot is saved and already running. Message it in Telegram.", "ربات ذخیره شد و همین حالا روشن است. در تلگرام به آن پیام بده.")
            TelegramSaveResult.Restarting -> t(
                "The bot is saved and the messaging gateway is restarting. Message it in a moment.",
                "ربات ذخیره شد و گیت‌وی پیام‌رسان دوباره راه می‌افتد. چند لحظه بعد به آن پیام بده.",
            )
            is TelegramSaveResult.NeedsRestart -> t(
                "The bot is saved, but the messaging gateway could not restart" +
                    (result.reason?.let { ": $it" } ?: "") + ". Run `hermes gateway restart` on the server.",
                "ربات ذخیره شد، ولی گیت‌وی پیام‌رسان دوباره راه نیفتاد" +
                    (result.reason?.let { ": $it" } ?: "") + ". روی سرور `hermes gateway restart` را اجرا کن.",
            )
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

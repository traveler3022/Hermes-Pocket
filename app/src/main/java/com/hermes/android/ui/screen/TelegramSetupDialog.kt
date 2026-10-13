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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hermes.android.domain.messaging.TelegramApplyResult
import com.hermes.android.ui.i18n.t
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
                    TelegramStep.Idle -> t("Telegram setup", "راه‌اندازی تلگرام")
                    TelegramStep.Starting -> t("Starting…", "در حال شروع…")
                    is TelegramStep.Waiting -> t("Step 1: make the bot", "قدم ۱: ساختن بات")
                    is TelegramStep.Ready -> t("Step 2: who may use it", "قدم ۲: چه کسانی استفاده کنند")
                    is TelegramStep.Saving -> t("Saving…", "در حال ذخیره…")
                    is TelegramStep.Done -> t("Telegram connected", "تلگرام وصل شد")
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
                        t("The setup link expired before the bot was made. Start again.", "لینک راه‌اندازی قبل از ساخته شدن بات منقضی شد. دوباره شروع کن."),
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
                    Text(t("Save and restart", "ذخیره و راه‌اندازی دوباره"))
                }
                TelegramStep.Idle -> TextButton(onClick = { viewModel.start() }) { Text(t("Try again", "دوباره")) }
                is TelegramStep.Done -> TextButton(onClick = { viewModel.close() }) { Text(t("Done", "تمام")) }
                else -> Unit
            }
        },
        dismissButton = {
            if (!busy && step !is TelegramStep.Done) {
                TextButton(onClick = { viewModel.close() }) { Text(t("Cancel", "انصراف")) }
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
        t(
            "Open Telegram and create the bot there" +
                (if (step.pairing.suggestedUsername.isNotEmpty()) " (suggested name: @${step.pairing.suggestedUsername})" else "") +
                ". Come back here afterwards — this page moves on by itself.",
            "تلگرام را باز کن و بات را آنجا بساز" +
                (if (step.pairing.suggestedUsername.isNotEmpty()) " (نام پیشنهادی: @${step.pairing.suggestedUsername})" else "") +
                ". بعد به اینجا برگرد؛ این صفحه خودش جلو می‌رود.",
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (link.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { runCatching { uriHandler.openUri(link) } }) { Text(t("Open Telegram", "باز کردن تلگرام")) }
            TextButton(onClick = { clipboard.setText(AnnotatedString(link)) }) { Text(t("Copy link", "کپی لینک")) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            t("Waiting for Telegram…", "در انتظار تلگرام…"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadyStep(step: TelegramStep.Ready, state: TelegramSetupUiState, viewModel: TelegramSetupViewModel) {
    var newId by remember { mutableStateOf("") }
    step.botUsername?.let {
        Text(t("Bot made: @$it", "بات ساخته شد: ‎@$it"), style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        t(
            "Only these Telegram user ids may talk to the agent. The bot's maker is added already.",
            "فقط این شناسه‌های کاربری تلگرام می‌توانند با ایجنت حرف بزنند. سازندهٔ بات از قبل اضافه شده.",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.allowedIds.forEach { id ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (id == step.ownerUserId) t("$id (you)", "$id (خودت)") else id,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.removeAllowedId(id) }) {
                Icon(Icons.Default.Close, contentDescription = t("Remove", "حذف"), modifier = Modifier.size(18.dp))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newId,
            onValueChange = { value -> newId = value.filter { it.isDigit() } },
            placeholder = { Text(t("Telegram user id", "شناسهٔ کاربری تلگرام")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { if (viewModel.addAllowedId(newId)) newId = "" }) {
            Icon(Icons.Default.Add, contentDescription = t("Add", "افزودن"))
        }
    }
}

@Composable
private fun DoneStep(step: TelegramStep.Done) {
    val bot = step.botUsername?.let { "@$it" } ?: t("The bot", "بات")
    Text(
        when (val result = step.result) {
            TelegramApplyResult.Done -> t("$bot is saved.", "$bot ذخیره شد.")
            TelegramApplyResult.Restarting -> t(
                "$bot is saved and the messaging gateway is restarting. Message the bot in a moment.",
                "$bot ذخیره شد و گیت‌وی پیام‌رسان دوباره راه می‌افتد. چند لحظه بعد به بات پیام بده.",
            )
            is TelegramApplyResult.NeedsRestart -> t(
                "$bot is saved, but the messaging gateway could not restart" +
                    (result.reason?.let { ": $it" } ?: "") + ". Run `hermes gateway restart` on the server.",
                "$bot ذخیره شد، ولی گیت‌وی پیام‌رسان دوباره راه نیفتاد" +
                    (result.reason?.let { ": $it" } ?: "") + ". روی سرور `hermes gateway restart` را اجرا کن.",
            )
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

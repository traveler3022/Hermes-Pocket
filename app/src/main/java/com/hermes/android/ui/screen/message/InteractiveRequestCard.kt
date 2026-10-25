package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ClarifyQuestionUi
import com.hermes.android.ui.viewmodel.InteractiveKind
import com.hermes.android.ui.design.HxStatus

/**
 * A question the agent is blocked on: clarify (one question — free text, one
 * choice or several — or a batch), a sudo password, or a secret. Answers are
 * picked first and sent with one button, so a stray tap never answers.
 */
@Composable
internal fun InteractiveRequestCard(
    message: ChatMessage.InteractiveRequest,
    onRespondToClarify: (requestId: String, picked: List<String>) -> Unit = { _, _ -> },
    onRespondToClarifyBatch: (requestId: String, answers: Map<String, List<String>>) -> Unit = { _, _ -> },
    /** Skips the whole clarify card, as the desktop's Skip does. */
    onSkipClarify: (requestId: String) -> Unit = {},
    onRespondToSudo: (requestId: String, password: String) -> Unit = { _, _ -> },
    onRespondToSecret: (requestId: String, value: String) -> Unit = { _, _ -> },
) {
    // Amber while it waits on the user — the same amber as the approval sheet
    // and the drawer's "waiting" dot. Once answered or expired it is history,
    // so it drops to a neutral outline instead of still calling for attention.
    val settled = message.answered || message.expired
    val accent = if (settled) MaterialTheme.colorScheme.outline else HxStatus.waiting
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = if (settled) 0.05f else 0.1f))
            .border(1.dp, accent.copy(alpha = if (settled) 0.2f else 0.35f), RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (message.kind in VAULT_KINDS) {
                VaultHeader(message)
            } else if (message.questions.isEmpty() && message.kind != InteractiveKind.SECRET) {
                Text(
                    text = message.question.ifBlank { stringResource(R.string.sudo_password_required) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            when {
                message.answered -> CardStatus(stringResource(R.string.answered))
                message.expired -> CardStatus(stringResource(R.string.no_longer_waiting_for_an_answer))
                message.kind == InteractiveKind.CLARIFY && message.questions.isNotEmpty() ->
                    BatchClarify(message.questions, onSkip = { onSkipClarify(message.requestId) }) {
                        onRespondToClarifyBatch(message.requestId, it)
                    }
                message.kind == InteractiveKind.CLARIFY -> {
                    var picked by remember(message.requestId) { mutableStateOf(emptyList<String>()) }
                    ClarifyAnswerInput(message.choices, message.multiSelect, picked) { picked = it }
                    SendRow(
                        enabled = picked.any { it.isNotBlank() },
                        onSkip = { onSkipClarify(message.requestId) },
                        skipLabel = stringResource(R.string.clarify_skip),
                    ) {
                        onRespondToClarify(message.requestId, picked)
                    }
                }
                // Vault answers go back like a secret's: {value}, '' = declined.
                message.kind == InteractiveKind.VAULT_UNLOCK ->
                    MaskedInput(
                        label = stringResource(R.string.vault_master_password),
                        placeholder = stringResource(R.string.vault_master_password),
                        onSkip = { onRespondToSecret(message.requestId, "") },
                        skipLabel = stringResource(R.string.vault_keep_locked),
                    ) { onRespondToSecret(message.requestId, it) }
                message.kind == InteractiveKind.VAULT_CODE ->
                    VaultCodeInput(
                        onSkip = { onRespondToSecret(message.requestId, "") },
                    ) { onRespondToSecret(message.requestId, it) }
                message.kind == InteractiveKind.VAULT_SAVE_LOGIN ->
                    VaultLoginInput(
                        onSkip = { onRespondToSecret(message.requestId, "") },
                    ) { identifier, password ->
                        val answer = kotlinx.serialization.json.buildJsonObject {
                            put("identifier", kotlinx.serialization.json.JsonPrimitive(identifier))
                            put("password", kotlinx.serialization.json.JsonPrimitive(password))
                        }
                        onRespondToSecret(message.requestId, answer.toString())
                    }
                message.kind == InteractiveKind.SUDO ->
                    MaskedInput(label = null, placeholder = stringResource(R.string.enter_sudo_password)) {
                        onRespondToSudo(message.requestId, it)
                    }
                else ->
                    MaskedInput(label = message.question, placeholder = stringResource(R.string.enter_value)) {
                        onRespondToSecret(message.requestId, it)
                    }
            }
        }
    }
}

@Composable
private fun ColumnScope.BatchClarify(
    questions: List<ClarifyQuestionUi>,
    onSkip: () -> Unit,
    onSubmit: (Map<String, List<String>>) -> Unit,
) {
    var answers by remember(questions) { mutableStateOf(emptyMap<String, List<String>>()) }
    questions.forEach { q ->
        Text(q.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        ClarifyAnswerInput(q.choices, q.multiSelect, answers[q.qid].orEmpty()) { answers = answers + (q.qid to it) }
    }
    // One answer is enough, as on the desktop: a question left empty goes back as skipped.
    SendRow(
        enabled = questions.any { q -> answers[q.qid].orEmpty().any { it.isNotBlank() } },
        onSkip = onSkip,
        skipLabel = stringResource(R.string.clarify_skip),
    ) {
        onSubmit(answers)
    }
}

/**
 * Free text when there are no choices; otherwise a radio list, or checkboxes when [multiSelect],
 * with an "Other" field under it (Hermes always offers one). A typed answer replaces the picked
 * choice, or with [multiSelect] is one more answer beside the ticked ones.
 */
@Composable
private fun ClarifyAnswerInput(
    choices: List<String>?,
    multiSelect: Boolean,
    picked: List<String>,
    onPickedChange: (List<String>) -> Unit,
) {
    if (choices.isNullOrEmpty()) {
        OutlinedTextField(
            value = picked.firstOrNull().orEmpty(),
            onValueChange = { onPickedChange(if (it.isEmpty()) emptyList() else listOf(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.type_answer)) },
        )
        return
    }
    val chosen = picked.filter { it in choices }
    val typed = picked.firstOrNull { it !in choices }.orEmpty()
    choices.forEach { choice ->
        val selected = choice in chosen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    onPickedChange(
                        when {
                            !multiSelect -> listOf(choice)
                            selected -> picked - choice
                            else -> picked + choice
                        },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multiSelect) {
                Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(8.dp))
            } else {
                RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
            }
            Text(choice, style = MaterialTheme.typography.bodyMedium)
        }
    }
    OutlinedTextField(
        value = typed,
        onValueChange = { text ->
            val answer = if (text.isEmpty()) emptyList() else listOf(text)
            onPickedChange(if (multiSelect) chosen + answer else answer)
        },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.clarify_other)) },
    )
}

private val VAULT_KINDS = setOf(InteractiveKind.VAULT_UNLOCK, InteractiveKind.VAULT_SAVE_LOGIN, InteractiveKind.VAULT_CODE)

/** What the vault asks, in words: whose master password, which site's login or code. */
@Composable
private fun VaultHeader(message: ChatMessage.InteractiveRequest) {
    val name = message.question
    val (title, body) = when (message.kind) {
        InteractiveKind.VAULT_UNLOCK ->
            stringResource(R.string.vault_unlock_title, name) to stringResource(R.string.vault_unlock_desc, name)
        InteractiveKind.VAULT_SAVE_LOGIN ->
            stringResource(R.string.vault_save_title, name) to
                stringResource(R.string.vault_save_desc, message.detail.ifBlank { name })
        else ->
            (if (name.isBlank()) stringResource(R.string.vault_code_title_plain) else stringResource(R.string.vault_code_title, name)) to
                message.detail
    }
    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    if (body.isNotBlank()) {
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColumnScope.VaultCodeInput(onSkip: () -> Unit, onSubmit: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it.trim() },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.vault_code)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    SendRow(enabled = value.isNotBlank(), onSkip = onSkip, skipLabel = stringResource(R.string.vault_skip)) { onSubmit(value) }
}

/** Identifier plain, password masked; both needed, as on the desktop. */
@Composable
private fun ColumnScope.VaultLoginInput(onSkip: () -> Unit, onSubmit: (identifier: String, password: String) -> Unit) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    OutlinedTextField(
        value = identifier,
        onValueChange = { identifier = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.vault_identifier)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.vault_password)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    SendRow(
        enabled = identifier.isNotBlank() && password.isNotEmpty(),
        onSkip = onSkip,
        skipLabel = stringResource(R.string.vault_skip),
        sendLabel = stringResource(R.string.save),
    ) { onSubmit(identifier.trim(), password) }
}

@Composable
private fun ColumnScope.SendRow(
    enabled: Boolean,
    onSkip: () -> Unit,
    skipLabel: String,
    sendLabel: String? = null,
    onSend: () -> Unit,
) {
    Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onSkip) { Text(skipLabel) }
        Button(onClick = onSend, enabled = enabled) { Text(sendLabel ?: stringResource(R.string.send)) }
    }
}

@Composable
private fun ColumnScope.MaskedInput(
    label: String?,
    placeholder: String,
    onSkip: (() -> Unit)? = null,
    skipLabel: String = "",
    onSubmit: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        modifier = Modifier.fillMaxWidth(),
        label = if (label != null) {
            { Text(label) }
        } else {
            null
        },
        placeholder = { Text(placeholder) },
        visualTransformation = PasswordVisualTransformation(),
        // Masking only hides the dots on screen; without this the keyboard treats a sudo
        // password or an API key as ordinary text and may learn and suggest it later.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    if (onSkip != null) {
        SendRow(enabled = value.isNotBlank(), onSkip = onSkip, skipLabel = skipLabel) { onSubmit(value) }
    } else {
        SendButton(enabled = value.isNotBlank()) { onSubmit(value) }
    }
}

@Composable
private fun ColumnScope.SendButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.align(Alignment.End)) {
        Text(stringResource(R.string.send))
    }
}

@Composable
private fun CardStatus(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

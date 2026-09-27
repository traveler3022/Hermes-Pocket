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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ClarifyQuestionUi
import com.hermes.android.ui.viewmodel.InteractiveKind

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
    onRespondToSudo: (requestId: String, password: String) -> Unit = { _, _ -> },
    onRespondToSecret: (requestId: String, value: String) -> Unit = { _, _ -> },
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
            .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (message.questions.isEmpty() && message.kind != InteractiveKind.SECRET) {
                Text(
                    text = message.question.ifBlank { t("Sudo password required", "رمز sudo لازم است") },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            when {
                message.answered -> CardStatus(t("Answered", "پاسخ داده شد"))
                message.expired -> CardStatus(t("No longer waiting for an answer", "دیگر منتظر پاسخ نیست"))
                message.kind == InteractiveKind.CLARIFY && message.questions.isNotEmpty() ->
                    BatchClarify(message.questions) { onRespondToClarifyBatch(message.requestId, it) }
                message.kind == InteractiveKind.CLARIFY -> {
                    var picked by remember(message.requestId) { mutableStateOf(emptyList<String>()) }
                    ClarifyAnswerInput(message.choices, message.multiSelect, picked) { picked = it }
                    SendButton(enabled = picked.any { it.isNotBlank() }) {
                        onRespondToClarify(message.requestId, picked)
                    }
                }
                message.kind == InteractiveKind.SUDO ->
                    MaskedInput(label = null, placeholder = t("Enter sudo password...", "رمز sudo بنویسید...")) {
                        onRespondToSudo(message.requestId, it)
                    }
                else ->
                    MaskedInput(label = message.question, placeholder = t("Enter value...", "مقدار را وارد کنید...")) {
                        onRespondToSecret(message.requestId, it)
                    }
            }
        }
    }
}

@Composable
private fun ColumnScope.BatchClarify(questions: List<ClarifyQuestionUi>, onSubmit: (Map<String, List<String>>) -> Unit) {
    var answers by remember(questions) { mutableStateOf(emptyMap<String, List<String>>()) }
    questions.forEach { q ->
        Text(q.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        ClarifyAnswerInput(q.choices, q.multiSelect, answers[q.qid].orEmpty()) { answers = answers + (q.qid to it) }
    }
    SendButton(enabled = questions.all { q -> answers[q.qid].orEmpty().any { it.isNotBlank() } }) {
        onSubmit(answers)
    }
}

/** Free text when there are no choices; otherwise a radio list, or checkboxes when [multiSelect]. */
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
            placeholder = { Text(t("Type answer...", "جواب بنویسید...")) },
        )
        return
    }
    choices.forEach { choice ->
        val selected = choice in picked
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
}

@Composable
private fun ColumnScope.MaskedInput(label: String?, placeholder: String, onSubmit: (String) -> Unit) {
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
    SendButton(enabled = value.isNotBlank()) { onSubmit(value) }
}

@Composable
private fun ColumnScope.SendButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.align(Alignment.End)) {
        Text(t("Send", "ارسال"))
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

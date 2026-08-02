package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.InteractiveKind

@Composable
internal fun InteractiveRequestCard(
    message: ChatMessage.InteractiveRequest,
    onRespondToClarify: (requestId: String, answer: String) -> Unit = { _, _ -> },
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
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "\u2753 ${message.question}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (message.answered) {
                Text(
                    text = t("Answered", "پاسخ داده شد"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else when (message.kind) {
                InteractiveKind.CLARIFY -> if (message.choices != null) {
                    message.choices.forEach { choice ->
                        OutlinedButton(
                            onClick = { onRespondToClarify(message.requestId, choice) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) { Text(choice) }
                    }
                } else {
                    var answer by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(t("Type answer...", "جواب بنویسید...")) },
                    )
                    Button(
                        onClick = { onRespondToClarify(message.requestId, answer) },
                        enabled = answer.isNotBlank(),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                    ) { Text(t("Send", "ارسال")) }
                }
                InteractiveKind.SUDO -> {
                    var password by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(t("Enter sudo password...", "رمز sudo بنویسید...")) },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(
                        onClick = { onRespondToSudo(message.requestId, password) },
                        enabled = password.isNotBlank(),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                    ) { Text(t("Send", "ارسال")) }
                }
                InteractiveKind.SECRET -> {
                    var secretValue by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = secretValue,
                        onValueChange = { secretValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(message.question) },
                        placeholder = { Text(t("Enter value...", "مقدار را وارد کنید...")) },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(
                        onClick = { onRespondToSecret(message.requestId, secretValue) },
                        enabled = secretValue.isNotBlank(),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                    ) { Text(t("Send", "ارسال")) }
                }
            }
        }
    }
}

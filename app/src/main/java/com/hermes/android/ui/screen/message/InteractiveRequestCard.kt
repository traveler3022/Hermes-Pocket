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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.InteractiveKind

/**
 * Anything the agent needs an answer to, drawn IN the conversation.
 *
 * Command approvals used to arrive as an undismissable modal bottom sheet
 * instead. It covered the transcript completely, so the one question a person
 * needs answered before approving a command — what was it doing that it wants
 * to run this? — was behind the sheet asking them to decide, and there was no
 * way to look. A card in the flow keeps the reasoning above it readable and
 * scrollable while the decision is open.
 */
@Composable
internal fun InteractiveRequestCard(
    message: ChatMessage.InteractiveRequest,
    onRespondToClarify: (requestId: String, answer: String) -> Unit = { _, _ -> },
    onRespondToSudo: (requestId: String, password: String) -> Unit = { _, _ -> },
    onRespondToSecret: (requestId: String, value: String) -> Unit = { _, _ -> },
    onRespondToApproval: (choice: String) -> Unit = {},
) {
    // An approval is a decision about running a command, not a question about
    // the work: it gets the warning accent so it cannot be mistaken for one.
    val isApproval = message.kind == InteractiveKind.APPROVAL
    val accent = if (isApproval) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.1f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (isApproval) {
                    "\uD83D\uDEE1 ${message.question}"
                } else {
                    "\u2753 ${message.question}"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // The command itself: monospace and LTR whatever the app language,
            // and scrollable sideways so a long one is readable rather than
            // wrapped into soup.
            message.command?.takeIf { it.isNotBlank() }?.let { command ->
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = command,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 6,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (message.answered) {
                // What was answered, not just that it was: a card reading
                // only "Answered" left the exchange unreadable afterwards.
                // message.answer is null for sudo/secret by design.
                Text(
                    text = message.answer?.let { "\u21B3 $it" } ?: t("Answered", "پاسخ داده شد"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else when (message.kind) {
                // Deny stays one tap away, exactly as it was in the sheet.
                InteractiveKind.APPROVAL -> {
                    OutlinedButton(
                        onClick = { onRespondToApproval("deny") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) { Text(t("Deny", "رد کن")) }
                    Button(
                        onClick = { onRespondToApproval("once") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) { Text(t("Allow once", "یک بار اجازه بده")) }
                    if (message.allowPermanent) {
                        OutlinedButton(
                            onClick = { onRespondToApproval("always") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) { Text(t("Always allow", "همیشه اجازه بده")) }
                        // What "always" would actually whitelist — a standing
                        // permission is not something to grant blind.
                        message.patternKeys.takeIf { it.isNotEmpty() }?.let { keys ->
                            Text(
                                text = t(
                                    "Always allows: ${keys.joinToString(", ")}",
                                    "همیشه اجازه می‌ده: ${keys.joinToString("، ")}",
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

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

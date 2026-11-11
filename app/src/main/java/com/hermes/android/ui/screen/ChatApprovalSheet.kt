package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.icons.filled.Security
import com.hermes.android.ui.viewmodel.ApprovalCardModel

/**
 * The card for `approval.request`, docked above the composer inside the chat's column.
 *
 * Not a modal sheet: the chat behind it stays scrollable, so the reader can look at what
 * the agent did before deciding. It has no close: the gateway holds the turn until
 * `approval.respond` arrives, so hiding it would leave the agent stuck waiting with no
 * visible reason. Back does not answer it either. The buttons are the only exits, Deny
 * always one tap away. The same request also mirrors to an Android notification for when
 * the app is backgrounded; answering either one dismisses both.
 *
 * At most min(360dp, half the screen) tall; the description, command and patterns scroll
 * inside it while the answers stay in view.
 *
 * Choice values match ApprovalActionReceiver: "once" | "session" | "always" | "deny";
 * only the ones the server listed in the request's choices are offered.
 */
@Composable
internal fun ApprovalCard(
    card: ApprovalCardModel,
    onRespond: (choice: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val approval = card.request
    val otherChatTitle = card.otherChatTitle
    val maxHeight = minOf(360, LocalConfiguration.current.screenHeightDp / 2).dp
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .heightIn(max = maxHeight),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Header: shield bubble, title, the chat it came from, and "1 of N".
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(HxRadius.sm))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.command_approval),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (otherChatTitle != null) {
                        Text(
                            text = if (otherChatTitle.isNotBlank()) {
                                stringResource(R.string.approval_from_chat, otherChatTitle)
                            } else {
                                stringResource(R.string.approval_from_other_chat)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (card.showsPosition) {
                    Text(
                        text = stringResource(R.string.approval_position, card.position, card.total),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // What scrolls: description, the command and its patterns. The buttons stay put.
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (approval.description.isNotBlank()) {
                    Text(
                        text = approval.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // The command itself: always monospace and LTR, whatever the app
                // language, because it's shell text.
                Surface(
                    shape = RoundedCornerShape(HxRadius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        SelectionContainer {
                            Text(
                                text = approval.command,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    textDirection = TextDirection.Ltr,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                // Pattern keys: what "always allow" would actually whitelist.
                if (approval.patternKeys.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        approval.patternKeys.forEach { pattern ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = pattern,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        textDirection = TextDirection.Ltr,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onRespond("deny") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.deny))
                }
                if ("once" in approval.choices) {
                    Button(
                        onClick = { onRespond("once") },
                        modifier = Modifier.weight(1.4f),
                    ) {
                        Text(stringResource(R.string.allow_once))
                    }
                }
            }

            val session = "session" in approval.choices
            val always = approval.allowPermanent && "always" in approval.choices
            if (session || always) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (session) {
                        TextButton(onClick = { onRespond("session") }, modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.allow_for_this_chat),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (always) {
                        TextButton(onClick = { onRespond("always") }, modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.always_allow_this_pattern),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxTone
import com.hermes.android.ui.design.hxSoftShadow
import com.hermes.android.ui.icons.filled.Security
import com.hermes.android.ui.viewmodel.PendingApprovalUi

/**
 * The answer to `approval.request`, drawn in the composer's place while the
 * agent waits: the chat above stays in view, and the composer comes back (its
 * draft kept in the ViewModel) once the request is answered or withdrawn.
 *
 * The buttons are the only way out: the gateway holds the turn until
 * `approval.respond` arrives, and Deny is always one tap away. The same request
 * also mirrors to an Android notification for when the app is in the
 * background; answering either one clears both.
 *
 * Choice values match ApprovalActionReceiver: "once" | "session" | "always" | "deny";
 * only the ones the server listed in [PendingApprovalUi.choices] are offered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApprovalPanel(
    approval: PendingApprovalUi,
    onRespond: (choice: String) -> Unit,
    /** The request is from a chat other than the one on screen (a background turn). */
    fromOtherChat: Boolean = false,
) {
    // Same pill as the composer it stands in for, so the bottom of the screen
    // keeps one shape.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .hxSoftShadow(radius = 10.dp, shape = RoundedCornerShape(HxRadius.lg))
            .clip(RoundedCornerShape(HxRadius.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        // Header: shield tile + title/description
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(HxRadius.sm))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.command_approval),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (fromOtherChat) {
                    Text(
                        text = stringResource(R.string.approval_from_other_chat),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (approval.description.isNotBlank()) {
                    Text(
                        text = approval.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // The command itself: always monospace and LTR, whatever the app
        // language, because it's shell text. A long script scrolls inside a
        // capped box, so the chat above and the buttons below stay on screen.
        Surface(
            shape = RoundedCornerShape(HxRadius.sm),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
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
                            .verticalScroll(rememberScrollState())
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
                        shape = RoundedCornerShape(HxRadius.full),
                        color = HxTone.card,
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

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

        // The broader permissions stay quiet: one line of text links under
        // the two main answers.
        val showSession = "session" in approval.choices
        val showAlways = approval.allowPermanent && "always" in approval.choices
        if (showSession || showAlways) {
            // Wraps to two lines when both labels don't fit (German, small phones).
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (showSession) {
                    TextButton(onClick = { onRespond("session") }) {
                        Text(
                            stringResource(R.string.allow_for_this_chat),
                            style = MaterialTheme.typography.labelMedium,
                            color = HxTone.textTertiary,
                        )
                    }
                }
                if (showAlways) {
                    TextButton(onClick = { onRespond("always") }) {
                        Text(
                            stringResource(R.string.always_allow_this_pattern),
                            style = MaterialTheme.typography.labelMedium,
                            color = HxTone.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

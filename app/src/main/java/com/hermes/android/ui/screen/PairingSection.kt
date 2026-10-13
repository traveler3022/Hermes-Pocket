package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.domain.messaging.MessagingPairing.Request
import com.hermes.android.domain.messaging.MessagingPairing.User
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.viewmodel.PairingUiState
import com.hermes.android.ui.viewmodel.PairingViewModel

/** Pending access requests to the user's bots, and who is allowed: approve, revoke, clear. */
@Composable
fun PairingSection(state: PairingUiState, viewModel: PairingViewModel) {
    var revoking by remember { mutableStateOf<User?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(HxSpace.inner),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.pairing_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
                    }
                }
            }
            Text(
                stringResource(R.string.pairing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.loadError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (!state.loading && state.loadError == null && state.pending.isEmpty() && state.approved.isEmpty()) {
                Text(
                    stringResource(R.string.pairing_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.pending.isNotEmpty()) {
                SubHeader(stringResource(R.string.pairing_pending))
                state.pending.forEach { request ->
                    PendingRow(request, enabled = !state.busy) { viewModel.approve(request) }
                }
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = !state.busy,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.pairing_clear_all)) }
            }

            if (state.approved.isNotEmpty()) {
                SubHeader(stringResource(R.string.pairing_approved))
                state.approved.forEach { user ->
                    PersonRow(
                        name = user.userName.ifBlank { user.userId },
                        detail = "${platformName(user.platform)} · ${user.userId}",
                    ) {
                        TextButton(onClick = { revoking = user }, enabled = !state.busy) {
                            Text(stringResource(R.string.pairing_revoke), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    revoking?.let { user ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text(stringResource(R.string.pairing_revoke_confirm, user.userName.ifBlank { user.userId })) },
            confirmButton = {
                TextButton(onClick = {
                    revoking = null
                    viewModel.revoke(user)
                }) { Text(stringResource(R.string.pairing_revoke), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.pairing_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearPending()
                }) { Text(stringResource(R.string.pairing_clear_all), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = HxSpace.xs),
    )
}

@Composable
private fun PendingRow(request: Request, enabled: Boolean, onApprove: () -> Unit) {
    val age = request.ageMinutes?.let { stringResource(R.string.pairing_minutes_ago, it) }
    PersonRow(
        name = request.userName.ifBlank { request.userId },
        detail = listOfNotNull(platformName(request.platform), request.userId, age).joinToString(" · "),
        note = if (request.requestId == null) stringResource(R.string.pairing_needs_code) else null,
    ) {
        if (request.requestId != null) {
            Button(onClick = onApprove, enabled = enabled, shape = RoundedCornerShape(50)) {
                Text(stringResource(R.string.pairing_approve))
            }
        }
    }
}

@Composable
private fun PersonRow(
    name: String,
    detail: String,
    note: String? = null,
    action: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action()
    }
}

private fun platformName(platform: String): String = platform.replaceFirstChar { it.uppercase() }

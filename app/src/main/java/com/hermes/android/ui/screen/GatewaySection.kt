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
import androidx.compose.material3.OutlinedButton
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
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.viewmodel.GatewayCardState

/**
 * The messaging gateway (`hermes gateway`, the process behind the bots): its state and
 * Start / Stop / Restart, as the dashboard's sidebar has them. Stop asks first.
 */
@Composable
fun GatewaySection(
    state: GatewayCardState,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    var confirmStop by remember { mutableStateOf(false) }
    val status = state.status
    val running = status?.running == true
    val enabled = state.busy == null && !state.loading

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
                    stringResource(R.string.gateway_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (status != null) {
                    StatusChip(
                        // Hermes' own word when it has one (degraded, draining, startup_failed…).
                        label = when (val word = status.state) {
                            null, "running" -> stringResource(if (running) R.string.gateway_running else R.string.gateway_stopped)
                            "stopped" -> stringResource(R.string.gateway_stopped)
                            else -> word.replace('_', ' ')
                        },
                        color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.loading || state.busy != null) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
                    }
                }
            }
            Text(
                stringResource(R.string.gateway_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.loadError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            status?.platforms?.forEach { platform ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        platform.name.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        platform.state.orEmpty().replace('_', ' '),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (platform.state == "connected") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                platform.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (status != null && !running) {
                status.exitReason?.let {
                    Text(
                        stringResource(R.string.gateway_exit_reason, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (status != null && status.sharedWith.size > 1) {
                Text(
                    stringResource(R.string.gateway_shared, status.sharedWith.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm), modifier = Modifier.fillMaxWidth()) {
                    if (running) {
                        OutlinedButton(
                            onClick = { confirmStop = true },
                            enabled = enabled,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.gateway_stop)) }
                        Button(
                            onClick = onRestart,
                            enabled = enabled,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.gateway_restart)) }
                    } else {
                        Button(
                            onClick = onStart,
                            enabled = enabled,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.gateway_start)) }
                    }
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.gateway_stop_confirm_title)) },
            text = { Text(stringResource(R.string.gateway_stop_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    onStop()
                }) { Text(stringResource(R.string.gateway_stop), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Shown instead of the controls on the built-in Linux. */
@Composable
fun GatewayUnavailableNote() {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(HxSpace.inner),
            verticalArrangement = Arrangement.spacedBy(HxSpace.xs),
        ) {
            Text(
                stringResource(R.string.gateway_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.gateway_builtin_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

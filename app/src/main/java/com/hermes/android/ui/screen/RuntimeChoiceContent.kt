package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.ui.viewmodel.RuntimeChoiceUi

@Composable
internal fun RuntimeChoiceRow(
    /** Null while the user has not picked yet: neither chip is shown as chosen. */
    selected: RuntimeChoiceUi?,
    enabled: Boolean,
    onSelect: (RuntimeChoiceUi) -> Unit,
    /** Settings offer the user's own server too; first-run setup has no place to enter one. */
    showServer: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == RuntimeChoiceUi.BuiltInLinux,
            enabled = enabled,
            onClick = { onSelect(RuntimeChoiceUi.BuiltInLinux) },
            label = { Text(stringResource(R.string.built_in_linux)) },
        )
        FilterChip(
            selected = selected == RuntimeChoiceUi.Termux,
            enabled = enabled,
            onClick = { onSelect(RuntimeChoiceUi.Termux) },
            label = { Text("Termux") },
        )
        if (showServer) {
            FilterChip(
                selected = selected == RuntimeChoiceUi.RemoteServer,
                enabled = enabled,
                onClick = { onSelect(RuntimeChoiceUi.RemoteServer) },
                label = { Text(stringResource(R.string.server)) },
            )
        }
    }
}

@Composable
internal fun BuiltInLinuxDetectedContent(
    diskFreeBytes: Long?,
    onStartInstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.install_hermes_inside_the_app),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(R.string.linux_is_built_into_the_app_hermes_agent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            diskFreeBytes?.let {
                Text(
                    stringResource(R.string.free_space, formatBytes(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
    Button(onClick = onStartInstall, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.install_hermes))
    }
}

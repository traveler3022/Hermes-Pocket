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
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.RuntimeChoiceUi

@Composable
internal fun RuntimeChoiceRow(
    /** Null while the user has not picked yet: neither chip is shown as chosen. */
    selected: RuntimeChoiceUi?,
    enabled: Boolean,
    onSelect: (RuntimeChoiceUi) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == RuntimeChoiceUi.BuiltInLinux,
            enabled = enabled,
            onClick = { onSelect(RuntimeChoiceUi.BuiltInLinux) },
            label = { Text(t("Built-in Linux", "لینوکس داخلی")) },
        )
        FilterChip(
            selected = selected == RuntimeChoiceUi.Termux,
            enabled = enabled,
            onClick = { onSelect(RuntimeChoiceUi.Termux) },
            label = { Text("Termux") },
        )
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
                t("Install Hermes inside the app", "نصب Hermes داخل خود اپ"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                t(
                    "Linux is built into the app; Hermes Agent and its Python packages " +
                        "are downloaded into it. Needs about 1 GB free and a stable connection.",
                    "لینوکس داخل خود اپ است؛ Hermes Agent و پکیج‌های پایتونش داخل آن دانلود می‌شوند. " +
                        "حدود ۱ گیگابایت فضای خالی و اینترنت پایدار لازم است.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            diskFreeBytes?.let {
                Text(
                    t("Free space: ${formatBytes(it)}", "فضای خالی: ${formatBytes(it)}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
    Button(onClick = onStartInstall, modifier = Modifier.fillMaxWidth()) {
        Text(t("Install Hermes", "نصب Hermes"))
    }
}

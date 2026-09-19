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
    selected: RuntimeChoiceUi,
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
                    "Downloads Ubuntu (~30 MB), then installs Hermes Agent into it. " +
                        "Needs about 2 GB free and a stable connection; takes 5–15 minutes.",
                    "اوبونتو (حدود ۳۰ مگابایت) دانلود می‌شود و Hermes Agent داخلش نصب می‌شود. " +
                        "حدود ۲ گیگابایت فضای خالی و اینترنت پایدار لازم است؛ ۵ تا ۱۵ دقیقه طول می‌کشد.",
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

package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.domain.memory.MemoryProviders.ConfigField
import com.hermes.android.domain.memory.MemoryProviders.FieldKind
import com.hermes.android.domain.memory.MemoryProviders.Provider
import com.hermes.android.domain.memory.MemoryProviders.ProviderState
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.MemoryProvidersViewModel
import com.hermes.android.ui.viewmodel.MemoryProvidersViewModel.Busy
import com.hermes.android.ui.viewmodel.MemoryProvidersViewModel.Notice

/**
 * Under the memory files: which external memory provider the agent uses (with its
 * settings and install step), and erasing the built-in memory. [onBuiltInReset] reloads
 * the files shown above after an erase.
 */
@Composable
internal fun MemoryProvidersPanel(
    onBuiltInReset: () -> Unit,
    viewModel: MemoryProvidersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.notice) {
        if (state.notice == Notice.Reset) onBuiltInReset()
    }
    var confirmReset by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
        Text(t("External memory", "حافظهٔ بیرونی"), style = MaterialTheme.typography.titleMedium)
        Text(
            t(
                "Built-in memory is always on. One extra provider can store and search more of what the agent learns.",
                "حافظهٔ داخلی همیشه روشن است. یک سرویس اضافه هم می‌شود روشن کرد تا ایجنت چیزهای بیشتری را نگه دارد و در آن جستجو کند.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val status = state.status
        when {
            state.isLoading && status == null -> CircularProgressIndicator(Modifier.size(24.dp))
            state.loadError != null && status == null -> Column {
                Text(
                    t("Couldn't load: ", "بارگذاری نشد: ") + state.loadError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = viewModel::load) { Text(t("Try again", "دوباره")) }
            }
            status != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (status.active.isBlank()) {
                            t("In use: built-in only", "در حال استفاده: فقط حافظهٔ داخلی")
                        } else {
                            t("In use: ${status.active}", "در حال استفاده: ${status.active}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (status.active.isNotBlank()) {
                        TextButton(enabled = state.busy == null, onClick = viewModel::turnOff) {
                            Text(t("Turn off", "خاموش کردن"))
                        }
                    }
                }
                status.providers.forEach { provider ->
                    ProviderRow(provider, active = provider.name == status.active) { viewModel.edit(provider.name) }
                }
                Text(
                    t(
                        "More providers (Honcho, Mem0, Supermemory…) come as plugins: on the Hermes machine run `hermes plugins install <name>`.",
                        "سرویس‌های دیگر (Honcho، Mem0، Supermemory و…) افزونه‌اند: روی دستگاه هرمس `hermes plugins install <name>` را اجرا کن.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                Text(
                    t("Erase built-in memory", "پاک کردن حافظهٔ داخلی"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = HxSpace.sm),
                )
                Text(
                    "MEMORY.md ${sizeText(status.memoryFileBytes)} · USER.md ${sizeText(status.userFileBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    enabled = state.busy == null && (status.memoryFileBytes > 0 || status.userFileBytes > 0),
                    onClick = { confirmReset = true },
                ) {
                    if (state.busy == Busy.RESETTING) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(t("Erase…", "پاک کردن…"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // The panel's own outcome line; an open editor shows its own.
        if (state.editing == null) state.notice?.let { NoticeText(it) }
    }

    if (confirmReset) {
        ResetDialog(
            onPick = { target ->
                confirmReset = false
                viewModel.resetBuiltIn(target)
            },
            onDismiss = { confirmReset = false },
        )
    }

    val editing = state.editing
    val provider = state.status?.providers?.firstOrNull { it.name == editing }
    if (editing != null) {
        ProviderEditor(
            name = editing,
            provider = provider,
            state = state,
            onValue = viewModel::setValue,
            onInstall = viewModel::install,
            onSave = viewModel::saveAndActivate,
            onDismiss = { viewModel.edit(null) },
        )
    }
}

@Composable
private fun ProviderRow(provider: Provider, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(HxSpace.inner)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                Text(provider.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (active) StatusChip(label = t("in use", "فعال"), color = MaterialTheme.colorScheme.primary)
                val (label, ok) = when {
                    !provider.dependenciesInstalled -> t("needs install", "نیاز به نصب") to false
                    provider.state == ProviderState.READY -> t("ready", "آماده") to true
                    provider.state == ProviderState.NEEDS_CONFIG -> t("needs settings", "نیاز به تنظیم") to false
                    provider.state == ProviderState.MISSING -> t("not found", "پیدا نشد") to false
                    else -> t("unavailable", "در دسترس نیست") to false
                }
                StatusChip(
                    label = label,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (provider.description.isNotBlank()) {
                Text(
                    provider.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ProviderEditor(
    name: String,
    provider: Provider?,
    state: MemoryProvidersViewModel.UiState,
    onValue: (String, String) -> Unit,
    onInstall: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = state.busy != null
    val visible = state.fields.filter { field -> field.showWhen.all { (k, v) -> state.values[k].orEmpty() == v } }
    val missingRequired = visible.any { field ->
        field.required && state.values[field.key].isNullOrBlank() && !(field.kind == FieldKind.SECRET && field.isSet)
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                if (provider != null && !provider.dependenciesInstalled) {
                    Text(
                        t(
                            "It needs packages installed on the Hermes machine first.",
                            "اول باید چند بسته روی دستگاه هرمس نصب شود.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    provider.externalTools.forEach { command ->
                        Text(
                            command,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(enabled = !busy, onClick = onInstall) {
                        if (state.busy == Busy.INSTALLING) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(t("  Installing… (can take minutes)", "  در حال نصب… (چند دقیقه طول می‌کشد)"))
                        } else {
                            Text(t("Install", "نصب"))
                        }
                    }
                }
                state.install?.let { result ->
                    Text(
                        if (result.ok) t("Install finished.", "نصب تمام شد.") else t("Install failed:", "نصب ناموفق بود:"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    result.steps.filter { !result.ok || it.status == "failed" }.forEach { step ->
                        Text(
                            "${step.status}: ${step.name}" + if (step.detail.isNotBlank()) "\n${step.detail}" else "",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when {
                    state.isLoadingFields -> CircularProgressIndicator(Modifier.size(24.dp))
                    visible.isEmpty() -> Text(
                        t("Nothing to set for this one.", "این یکی تنظیمی ندارد."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> visible.forEach { field ->
                        ConfigFieldInput(field, state.values[field.key].orEmpty(), enabled = !busy) { onValue(field.key, it) }
                    }
                }
                state.notice?.let { NoticeText(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && !state.isLoadingFields && !missingRequired, onClick = onSave) {
                if (state.busy == Busy.SAVING) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(t("Save and use", "ذخیره و استفاده"))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(t("Close", "بستن")) }
        },
    )
}

@Composable
private fun ConfigFieldInput(field: ConfigField, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val label = field.label + if (field.required) " *" else ""
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (field.kind) {
            FieldKind.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = value.equals("true", ignoreCase = true),
                    enabled = enabled,
                    onCheckedChange = { onChange(it.toString()) },
                )
            }
            FieldKind.SELECT -> {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    field.options.forEach { option ->
                        FilterChip(
                            selected = value == option.value,
                            enabled = enabled,
                            onClick = { onChange(option.value) },
                            label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
            else -> OutlinedTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                label = { Text(label) },
                placeholder = {
                    Text(
                        if (field.kind == FieldKind.SECRET && field.isSet) {
                            t("Saved — leave empty to keep it", "ذخیره شده؛ خالی بماند تا عوض نشود")
                        } else {
                            field.placeholder
                        },
                    )
                },
                visualTransformation = if (field.kind == FieldKind.SECRET) {
                    PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (field.kind) {
                        FieldKind.INTEGER -> KeyboardType.Number
                        FieldKind.NUMBER -> KeyboardType.Decimal
                        FieldKind.SECRET -> KeyboardType.Password
                        else -> KeyboardType.Text
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val help = listOf(field.description, field.url).filter { it.isNotBlank() }.joinToString("\n")
        if (help.isNotBlank()) {
            Text(help, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ResetDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Erase built-in memory?", "حافظهٔ داخلی پاک شود؟")) },
        text = {
            Text(
                t(
                    "What the agent remembered there is deleted for good. External providers are not touched.",
                    "چیزهایی که ایجنت آنجا به خاطر سپرده برای همیشه پاک می‌شود. به سرویس بیرونی دست زده نمی‌شود.",
                ),
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onPick("memory") }) { Text(t("Only MEMORY.md", "فقط MEMORY.md")) }
                TextButton(onClick = { onPick("user") }) { Text(t("Only USER.md", "فقط USER.md")) }
                TextButton(onClick = { onPick("all") }) {
                    Text(t("Both", "هر دو"), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(t("Cancel", "انصراف")) }
            }
        },
    )
}

@Composable
private fun NoticeText(notice: Notice) {
    val (text, failed) = when (notice) {
        Notice.Saved -> t("Saved and turned on.", "ذخیره و روشن شد.") to false
        Notice.TurnedOff -> t("Turned off. Built-in memory only.", "خاموش شد. فقط حافظهٔ داخلی.") to false
        Notice.Reset -> t("Erased.", "پاک شد.") to false
        is Notice.Failed -> notice.reason to true
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun sizeText(bytes: Long): String = when {
    bytes <= 0 -> t("empty", "خالی")
    bytes < 1024 -> t("$bytes B", "$bytes بایت")
    else -> t("${bytes / 1024} KB", "${bytes / 1024} کیلوبایت")
}

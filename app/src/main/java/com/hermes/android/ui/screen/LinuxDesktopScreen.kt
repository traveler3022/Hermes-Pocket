package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.runtime.linux.LinuxDesktop
import com.hermes.android.ui.design.GroupDivider
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.SectionHeader
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.LinuxDesktopViewModel

/**
 * Browser & desktop (VNC): Aether's Alpine Chrome page plus its settings — the Chromium the
 * agent browses with, on a desktop the user can watch here or from a VNC client.
 */
@Composable
fun LinuxDesktopScreen(
    onNavigateBack: () -> Unit,
    onOpenViewer: () -> Unit,
    viewModel: LinuxDesktopViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<DesktopDialog?>(null) }
    val clearedMessage = t("Browser data cleared.", "داده‌های مرورگر پاک شد.")

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    HermesScaffold(
        title = t("Browser & desktop", "مرورگر و دسکتاپ"),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            if (ui.installed == false) {
                Text(
                    text = t(
                        "Install “Browser & desktop (VNC)” under Linux → Packages first.",
                        "اول بستهٔ «مرورگر و دسکتاپ (VNC)» را از لینوکس ← بسته‌ها نصب کنید.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
                return@Column
            }

            Spacer(Modifier.height(12.dp))
            StatusCard(
                state = state,
                busy = ui.busy || ui.installed == null,
                restartPending = ui.restartPending,
                onOpen = onOpenViewer,
                onStart = viewModel::startInBackground,
                onStop = viewModel::stop,
                onRestart = viewModel::restart,
            )

            SectionHeader(t("Agent", "عامل"))
            SettingsGroup {
                SettingRow(
                    title = t("Agent browses here", "عامل با این مرورگر کار کند"),
                    subtitle = t(
                        "Hermes' browser tools use this Chromium, so you can watch and take over. Starts with Hermes.",
                        "ابزارهای مرورگر Hermes از همین Chromium استفاده می‌کنند و شما کارش را زنده می‌بینید و می‌توانید دست بگیرید. همراه Hermes روشن می‌شود.",
                    ),
                    icon = Icons.Default.SmartToy,
                    trailing = {
                        Switch(
                            checked = settings.agentBrowser,
                            onCheckedChange = { on -> viewModel.update { it.copy(agentBrowser = on) } },
                            enabled = !ui.busy,
                        )
                    },
                )
            }

            SectionHeader(t("Browser", "مرورگر"))
            SettingsGroup {
                SettingRow(
                    title = t("Home page", "صفحهٔ شروع"),
                    subtitle = settings.homepage,
                    icon = Icons.Default.Home,
                    onClick = { dialog = DesktopDialog.Homepage },
                )
                GroupDivider()
                SettingRow(
                    title = t("Clear browser data", "پاک کردن داده‌های مرورگر"),
                    subtitle = t("Cookies, logins, history, cache", "کوکی‌ها، ورودها، تاریخچه و کش"),
                    icon = Icons.Default.DeleteSweep,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { dialog = DesktopDialog.ClearData },
                )
            }

            SectionHeader(t("Display", "نمایشگر"))
            SettingsGroup {
                SettingRow(
                    title = t("Screen size", "اندازهٔ صفحه"),
                    subtitle = with(settings.resolution) { "${t(titleEn, titleFa)} · ${width}×$height" },
                    icon = Icons.Default.AspectRatio,
                    onClick = { dialog = DesktopDialog.Resolution },
                )
            }

            SectionHeader("VNC")
            SettingsGroup {
                SettingRow(
                    title = t("Allow other devices", "اتصال از دستگاه‌های دیگر"),
                    subtitle = if (settings.vncLan && settings.vncPassword.isNotEmpty()) {
                        viewModel.lanAddress()?.let {
                            t("Connect a VNC client to $it", "با یک برنامهٔ VNC به $it وصل شوید")
                        } ?: t("Not on a Wi-Fi network", "به شبکهٔ Wi-Fi وصل نیستید")
                    } else {
                        t(
                            "Off: only this app can see the desktop",
                            "خاموش: فقط همین اپ دسکتاپ را می‌بیند",
                        )
                    },
                    icon = Icons.Default.Wifi,
                    trailing = {
                        Switch(
                            checked = settings.vncLan,
                            onCheckedChange = { on ->
                                if (on && settings.vncPassword.isEmpty()) {
                                    dialog = DesktopDialog.Password
                                } else {
                                    viewModel.update { it.copy(vncLan = on) }
                                }
                            },
                            enabled = !ui.busy,
                        )
                    },
                )
                GroupDivider()
                SettingRow(
                    title = t("VNC password", "رمز VNC"),
                    subtitle = if (settings.vncPassword.isEmpty()) {
                        t("Not set", "تنظیم نشده")
                    } else {
                        "•".repeat(settings.vncPassword.length)
                    },
                    icon = Icons.Default.Lock,
                    onClick = { dialog = DesktopDialog.Password },
                )
            }
            Text(
                text = t(
                    "The agent can also control the whole desktop (screenshots, mouse, keyboard) through its terminal; Hermes gets a built-in skill that explains how.",
                    "عامل می‌تواند کل دسکتاپ را هم از راه ترمینال کنترل کند: عکس صفحه، ماوس و کیبورد. یک مهارت داخلی به Hermes اضافه می‌شود که روش کار را توضیح می‌دهد.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }

    when (dialog) {
        DesktopDialog.Homepage -> TextSettingDialog(
            title = t("Home page", "صفحهٔ شروع"),
            initial = settings.homepage,
            keyboardType = KeyboardType.Uri,
            onDismiss = { dialog = null },
            onSave = { value ->
                viewModel.update { it.copy(homepage = value.trim().ifBlank { LinuxDesktop.DefaultHomepage }) }
                dialog = null
            },
        )
        DesktopDialog.Password -> TextSettingDialog(
            title = t("VNC password", "رمز VNC"),
            initial = settings.vncPassword,
            keyboardType = KeyboardType.Password,
            password = true,
            hint = t("6–8 characters; VNC uses only the first 8.", "۶ تا ۸ کاراکتر؛ VNC فقط ۸ کاراکتر اول را می‌خواند."),
            isValid = { it.length >= 6 },
            onDismiss = { dialog = null },
            onSave = { value ->
                viewModel.update { it.copy(vncPassword = value, vncLan = true) }
                dialog = null
            },
        )
        DesktopDialog.Resolution -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(t("Screen size", "اندازهٔ صفحه")) },
            text = {
                Column {
                    LinuxDesktop.Resolution.values().forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(
                                selected = option == settings.resolution,
                                onClick = {
                                    viewModel.update { it.copy(resolution = option) }
                                    dialog = null
                                },
                            )
                            Text("${t(option.titleEn, option.titleFa)} · ${option.width}×${option.height}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(t("Close", "بستن")) } },
        )
        DesktopDialog.ClearData -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(t("Clear browser data?", "داده‌های مرورگر پاک شود؟")) },
            text = {
                Text(
                    t(
                        "Signs out of every site in this browser. The desktop stops first.",
                        "از همهٔ سایت‌ها در این مرورگر خارج می‌شوید. اول دسکتاپ متوقف می‌شود.",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearBrowserData(clearedMessage)
                    dialog = null
                }) { Text(t("Clear", "پاک کن"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(t("Cancel", "لغو")) } },
        )
        null -> Unit
    }
}

private enum class DesktopDialog { Homepage, Password, Resolution, ClearData }

@Composable
private fun StatusCard(
    state: LinuxDesktop.State,
    busy: Boolean,
    restartPending: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    val (label, color) = when (state) {
        LinuxDesktop.State.Running -> t("Running", "روشن") to Color(0xFF22C55E)
        LinuxDesktop.State.Starting -> t("Starting…", "در حال روشن شدن…") to Color(0xFFF59E0B)
        LinuxDesktop.State.Stopped -> t("Stopped", "خاموش") to MaterialTheme.colorScheme.outline
        is LinuxDesktop.State.Error -> t("Failed", "خطا") to MaterialTheme.colorScheme.error
    }
    SettingsGroup {
        SettingRow(
            title = t("Desktop", "دسکتاپ"),
            subtitle = when {
                state is LinuxDesktop.State.Error -> state.message.lines().first()
                restartPending -> t("Restart to apply the new settings", "برای اعمال تنظیمات جدید، دوباره روشن کنید")
                else -> t("Chromium on a VNC display inside the built-in Linux", "Chromium روی یک نمایشگر VNC داخل لینوکس داخلی")
            },
            icon = Icons.Default.DesktopWindows,
            trailing = {
                if (busy || state == LinuxDesktop.State.Starting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    StatusChip(label = label, color = color)
                }
            },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        ) {
            Button(onClick = onOpen, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(t("Open viewer", "باز کردن نمایشگر"))
            }
            when {
                state == LinuxDesktop.State.Running && restartPending ->
                    OutlinedButton(onClick = onRestart, enabled = !busy) { Text(t("Restart", "راه‌اندازی دوباره")) }
                state == LinuxDesktop.State.Running ->
                    OutlinedButton(onClick = onStop, enabled = !busy) { Text(t("Stop", "توقف")) }
                else ->
                    OutlinedButton(
                        onClick = onStart,
                        enabled = !busy && state != LinuxDesktop.State.Starting,
                    ) { Text(t("Start", "روشن کردن")) }
            }
        }
    }
}

@Composable
private fun TextSettingDialog(
    title: String,
    initial: String,
    keyboardType: KeyboardType,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    password: Boolean = false,
    hint: String? = null,
    isValid: (String) -> Boolean = { true },
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                supportingText = hint?.let { text -> @Composable { Text(text) } },
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = isValid(value)) { Text(t("Save", "ذخیره")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "لغو")) } },
    )
}

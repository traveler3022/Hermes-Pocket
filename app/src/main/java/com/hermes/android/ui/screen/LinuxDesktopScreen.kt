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
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.AspectRatio
import com.hermes.android.ui.icons.filled.DeleteSweep
import com.hermes.android.ui.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import com.hermes.android.ui.icons.filled.SmartToy
import com.hermes.android.ui.icons.filled.Wifi
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
import com.hermes.android.runtime.DefaultDesktopHomepage
import com.hermes.android.runtime.DesktopResolution
import com.hermes.android.runtime.DesktopState
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
    val clearedMessage = stringResource(R.string.browser_data_cleared)

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    HermesScaffold(
        title = stringResource(R.string.browser_desktop),
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
                    text = stringResource(R.string.install_browser_desktop_vnc_under_linux),
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

            SectionHeader(stringResource(R.string.agent))
            SettingsGroup {
                SettingRow(
                    title = stringResource(R.string.agent_browses_here),
                    subtitle = stringResource(R.string.hermes_browser_tools_use_this_chromium),
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

            SectionHeader(stringResource(R.string.browser))
            SettingsGroup {
                SettingRow(
                    title = stringResource(R.string.home_page),
                    subtitle = settings.homepage,
                    icon = Icons.Default.Home,
                    onClick = { dialog = DesktopDialog.Homepage },
                )
                GroupDivider()
                SettingRow(
                    title = stringResource(R.string.clear_browser_data),
                    subtitle = stringResource(R.string.cookies_logins_history_cache),
                    icon = Icons.Default.DeleteSweep,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { dialog = DesktopDialog.ClearData },
                )
            }

            SectionHeader(stringResource(R.string.display))
            SettingsGroup {
                SettingRow(
                    title = stringResource(R.string.screen_size),
                    subtitle = with(settings.resolution) { "${t(titleEn, titleFa)} · ${width}×$height" },
                    icon = Icons.Default.AspectRatio,
                    onClick = { dialog = DesktopDialog.Resolution },
                )
            }

            SectionHeader("VNC")
            SettingsGroup {
                SettingRow(
                    title = stringResource(R.string.allow_other_devices),
                    subtitle = if (settings.vncLan && settings.vncPassword.isNotEmpty()) {
                        viewModel.lanAddress()?.let {
                            stringResource(R.string.connect_a_vnc_client_to, it)
                        } ?: stringResource(R.string.not_on_a_wi_fi_network)
                    } else {
                        stringResource(R.string.off_only_this_app_can_see_the_desktop)
                    },
                    icon = Icons.Default.Wifi,
                    trailing = {
                        Switch(
                            checked = settings.vncLan,
                            onCheckedChange = { on ->
                                if (on && settings.vncPassword.isEmpty()) {
                                    dialog = DesktopDialog.PasswordToShare
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
                    title = stringResource(R.string.vnc_password),
                    subtitle = if (settings.vncPassword.isEmpty()) {
                        stringResource(R.string.not_set)
                    } else {
                        "•".repeat(settings.vncPassword.length)
                    },
                    icon = Icons.Default.Lock,
                    onClick = { dialog = DesktopDialog.Password },
                )
            }
            Text(
                text = stringResource(R.string.the_agent_can_also_control_the_whole),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }

    when (dialog) {
        DesktopDialog.Homepage -> TextSettingDialog(
            title = stringResource(R.string.home_page),
            initial = settings.homepage,
            keyboardType = KeyboardType.Uri,
            onDismiss = { dialog = null },
            onSave = { value ->
                viewModel.update { it.copy(homepage = value.trim().ifBlank { DefaultDesktopHomepage }) }
                dialog = null
            },
        )
        DesktopDialog.Password, DesktopDialog.PasswordToShare -> {
            // Only the LAN switch shares the desktop. Saving from the password row used to
            // turn LAN access on as well, exposing VNC to the network unasked.
            val share = dialog == DesktopDialog.PasswordToShare
            TextSettingDialog(
                title = stringResource(R.string.vnc_password),
                initial = settings.vncPassword,
                keyboardType = KeyboardType.Password,
                password = true,
                hint = stringResource(R.string.s_6_8_characters_vnc_uses_only_the_first_8),
                isValid = { it.length >= 6 },
                onDismiss = { dialog = null },
                onSave = { value ->
                    viewModel.update { it.copy(vncPassword = value, vncLan = it.vncLan || share) }
                    dialog = null
                },
            )
        }
        DesktopDialog.Resolution -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.screen_size)) },
            text = {
                Column {
                    DesktopResolution.values().forEach { option ->
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
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.close)) } },
        )
        DesktopDialog.ClearData -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.clear_browser_data_2)) },
            text = {
                Text(
                    stringResource(R.string.signs_out_of_every_site_in_this_browser),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearBrowserData(clearedMessage)
                    dialog = null
                }) { Text(stringResource(R.string.clear_2), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        null -> Unit
    }
}

/** [PasswordToShare]: opened by the LAN switch, so saving it also turns sharing on. */
private enum class DesktopDialog { Homepage, Password, PasswordToShare, Resolution, ClearData }

@Composable
private fun StatusCard(
    state: DesktopState,
    busy: Boolean,
    restartPending: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    val (label, color) = when (state) {
        DesktopState.Running -> stringResource(R.string.running_3) to Color(0xFF22C55E)
        DesktopState.Starting -> stringResource(R.string.starting) to Color(0xFFF59E0B)
        DesktopState.Stopped -> stringResource(R.string.stopped_2) to MaterialTheme.colorScheme.outline
        is DesktopState.Error -> stringResource(R.string.failed) to MaterialTheme.colorScheme.error
    }
    SettingsGroup {
        SettingRow(
            title = stringResource(R.string.desktop),
            subtitle = when {
                state is DesktopState.Error -> state.message.lines().first()
                restartPending -> stringResource(R.string.restart_to_apply_the_new_settings)
                else -> stringResource(R.string.chromium_on_a_vnc_display_inside_the)
            },
            icon = Icons.Default.DesktopWindows,
            trailing = {
                if (busy || state == DesktopState.Starting) {
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
                Text(stringResource(R.string.open_viewer))
            }
            when {
                state == DesktopState.Running && restartPending ->
                    OutlinedButton(onClick = onRestart, enabled = !busy) { Text(stringResource(R.string.restart)) }
                state == DesktopState.Running ->
                    OutlinedButton(onClick = onStop, enabled = !busy) { Text(stringResource(R.string.stop)) }
                else ->
                    OutlinedButton(
                        onClick = onStart,
                        enabled = !busy && state != DesktopState.Starting,
                    ) { Text(stringResource(R.string.start)) }
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
            TextButton(onClick = { onSave(value) }, enabled = isValid(value)) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

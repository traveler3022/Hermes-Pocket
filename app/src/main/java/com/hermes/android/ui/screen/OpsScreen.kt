package com.hermes.android.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.ops.DebugShare
import com.hermes.android.domain.ops.OpsAction
import com.hermes.android.domain.ops.ShellHook
import com.hermes.android.ui.design.GroupDivider
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.SectionHeader
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.icons.filled.CloudDownload
import com.hermes.android.ui.icons.filled.DeleteSweep
import com.hermes.android.ui.icons.filled.Description
import com.hermes.android.ui.icons.filled.History
import com.hermes.android.ui.icons.filled.Key
import com.hermes.android.ui.icons.filled.Inventory2
import com.hermes.android.ui.icons.filled.Security
import com.hermes.android.ui.icons.filled.SwapVert
import com.hermes.android.ui.icons.filled.Terminal
import com.hermes.android.ui.viewmodel.OpsViewModel

/**
 * Hermes' maintenance tools on the machine it runs on: doctor and the other diagnostics,
 * backup and restore, the debug report, rollback checkpoints and shell hooks.
 */
@Composable
fun OpsScreen(
    onNavigateBack: () -> Unit = {},
    /** Opens a Settings page (a [SettingsSection] name) on its own: .env keys, config editor, connection log. */
    onOpenSettingsPage: (String) -> Unit = {},
    viewModel: OpsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    var confirmShare by rememberSaveable { mutableStateOf(false) }
    var confirmPrune by rememberSaveable { mutableStateOf(false) }
    var addingHook by rememberSaveable { mutableStateOf(false) }
    var removingHook by remember { mutableStateOf<ShellHook?>(null) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    HermesScaffold(
        title = stringResource(R.string.ops_title),
        subtitle = stringResource(R.string.ops_subtitle),
        onBack = onNavigateBack,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = viewModel::load, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            val busy = state.run?.running == true

            SectionHeader(stringResource(R.string.ops_diagnostics))
            SettingsGroup {
                ActionRow(OpsAction.DOCTOR, Icons.Default.Build, R.string.ops_doctor, R.string.ops_doctor_desc, busy, viewModel)
                GroupDivider()
                ActionRow(OpsAction.SECURITY_AUDIT, Icons.Default.Security, R.string.ops_security_audit, R.string.ops_security_audit_desc, busy, viewModel)
                GroupDivider()
                ActionRow(OpsAction.PROMPT_SIZE, Icons.Default.Description, R.string.ops_prompt_size, R.string.ops_prompt_size_desc, busy, viewModel)
                GroupDivider()
                ActionRow(OpsAction.DUMP, Icons.Default.Terminal, R.string.ops_dump, R.string.ops_dump_desc, busy, viewModel)
                GroupDivider()
                ActionRow(OpsAction.CONFIG_MIGRATE, Icons.Default.SwapVert, R.string.ops_config_migrate, R.string.ops_config_migrate_desc, busy, viewModel)
            }

            SectionHeader(stringResource(R.string.ops_backup_section))
            SettingsGroup {
                SettingRow(
                    title = stringResource(R.string.ops_backup),
                    subtitle = stringResource(R.string.ops_backup_desc),
                    icon = Icons.Default.Inventory2,
                    onClick = if (busy) null else viewModel::backup,
                )
                if (state.backupArchive != null) {
                    GroupDivider()
                    SettingRow(
                        title = stringResource(R.string.ops_backup_download),
                        subtitle = state.backupArchive?.substringAfterLast('/'),
                        icon = Icons.Default.CloudDownload,
                        onClick = if (state.downloadingBackup) null else viewModel::downloadBackup,
                        trailing = if (state.downloadingBackup) {
                            { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                    )
                }
                GroupDivider()
                SettingRow(
                    title = stringResource(R.string.ops_restore),
                    subtitle = stringResource(R.string.ops_restore_desc),
                    icon = Icons.Default.History,
                    onClick = if (busy) null else {
                        { pickBackup.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
                    },
                )
            }

            SectionHeader(stringResource(R.string.ops_support))
            SettingsGroup {
                SettingRow(
                    title = stringResource(R.string.ops_debug_share),
                    subtitle = stringResource(R.string.ops_debug_share_desc),
                    icon = Icons.Default.Share,
                    onClick = if (state.sharing) null else {
                        { confirmShare = true }
                    },
                    trailing = if (state.sharing) {
                        { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else {
                        null
                    },
                )
            }

            SectionHeader(stringResource(R.string.ops_config_logs))
            SettingsGroup {
                SettingRow(
                    title = stringResource(R.string.env_keys_title),
                    subtitle = stringResource(R.string.env_keys_subtitle),
                    icon = Icons.Default.Key,
                    onClick = { onOpenSettingsPage(SettingsSection.ENV_KEYS.name) },
                )
                GroupDivider()
                SettingRow(
                    title = stringResource(R.string.config_editor_title),
                    subtitle = stringResource(R.string.config_editor_subtitle),
                    icon = Icons.Default.Description,
                    onClick = { onOpenSettingsPage(SettingsSection.CONFIG_EDITOR.name) },
                )
                GroupDivider()
                SettingRow(
                    title = stringResource(R.string.connection_log),
                    subtitle = stringResource(R.string.every_connect_drop_and_retry_with_what),
                    icon = Icons.Default.History,
                    onClick = { onOpenSettingsPage(SettingsSection.CONNECTION_LOG.name) },
                )
            }

            SectionHeader(stringResource(R.string.ops_checkpoints))
            SettingsGroup {
                val cp = state.checkpoints
                SettingRow(
                    title = stringResource(R.string.ops_checkpoints_prune),
                    subtitle = when {
                        cp == null && state.loading -> stringResource(R.string.ops_loading)
                        cp == null -> stringResource(R.string.ops_unavailable)
                        cp.sessions.isEmpty() -> stringResource(R.string.ops_checkpoints_none)
                        else -> stringResource(R.string.ops_checkpoints_summary, cp.sessions.size, formatBytes(cp.totalBytes))
                    },
                    icon = Icons.Default.DeleteSweep,
                    onClick = if (busy || cp == null || cp.sessions.isEmpty()) null else {
                        { confirmPrune = true }
                    },
                )
            }

            SectionHeader(stringResource(R.string.ops_hooks))
            SettingsGroup {
                val hooks = state.hooks
                when {
                    hooks == null -> SettingRow(
                        title = stringResource(R.string.ops_hooks),
                        subtitle = if (state.loading) stringResource(R.string.ops_loading) else state.loadError ?: stringResource(R.string.ops_unavailable),
                        icon = Icons.Default.Terminal,
                    )
                    else -> {
                        if (hooks.hooks.isEmpty()) {
                            SettingRow(
                                title = stringResource(R.string.ops_hooks_none),
                                subtitle = stringResource(R.string.ops_hooks_desc),
                                icon = Icons.Default.Terminal,
                            )
                        }
                        hooks.hooks.forEachIndexed { index, hook ->
                            if (index > 0) GroupDivider()
                            SettingRow(
                                title = hook.event + (hook.matcher?.let { " · $it" } ?: ""),
                                subtitle = hook.command + "\n" + when {
                                    !hook.allowed -> stringResource(R.string.ops_hook_not_approved)
                                    !hook.executable -> stringResource(R.string.ops_hook_not_executable)
                                    else -> stringResource(R.string.ops_hook_active)
                                },
                                icon = Icons.Default.Terminal,
                                iconTint = if (hook.allowed && hook.executable) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                trailing = {
                                    IconButton(onClick = { removingHook = hook }, enabled = !state.hookBusy) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ops_hook_remove))
                                    }
                                },
                            )
                        }
                        GroupDivider()
                        SettingRow(
                            title = stringResource(R.string.ops_hook_add),
                            icon = Icons.Default.Add,
                            onClick = if (state.hookBusy || hooks.validEvents.isEmpty()) null else {
                                { addingHook = true }
                            },
                        )
                    }
                }
            }
        }
    }

    state.run?.let { run -> RunSheet(run, onDismiss = viewModel::closeRun) }

    state.debugShare?.let { share -> DebugShareDialog(share, onDismiss = viewModel::dismissDebugShare) }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.ops_restore)) },
            text = { Text(stringResource(R.string.ops_restore_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    viewModel.restore(uri)
                }) { Text(stringResource(R.string.ops_restore_go), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmShare) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            title = { Text(stringResource(R.string.ops_debug_share)) },
            text = { Text(stringResource(R.string.ops_debug_share_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmShare = false
                    viewModel.shareDebugReport()
                }) { Text(stringResource(R.string.ops_debug_share_go)) }
            },
            dismissButton = { TextButton(onClick = { confirmShare = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmPrune) {
        AlertDialog(
            onDismissRequest = { confirmPrune = false },
            title = { Text(stringResource(R.string.ops_checkpoints_prune)) },
            text = { Text(stringResource(R.string.ops_checkpoints_prune_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmPrune = false
                    viewModel.pruneCheckpoints()
                }) { Text(stringResource(R.string.ops_checkpoints_prune_go), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmPrune = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    removingHook?.let { hook ->
        AlertDialog(
            onDismissRequest = { removingHook = null },
            title = { Text(stringResource(R.string.ops_hook_remove)) },
            text = { Text(hook.event + "\n" + hook.command) },
            confirmButton = {
                TextButton(onClick = {
                    removingHook = null
                    viewModel.removeHook(hook)
                }) { Text(stringResource(R.string.ops_hook_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removingHook = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (addingHook) {
        AddHookDialog(
            events = state.hooks?.validEvents.orEmpty(),
            onDismiss = { addingHook = false },
            onAdd = { event, command, matcher, approve ->
                addingHook = false
                viewModel.addHook(event, command, matcher, approve)
            },
        )
    }
}

@Composable
private fun ActionRow(
    action: OpsAction,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: Int,
    subtitle: Int,
    busy: Boolean,
    viewModel: OpsViewModel,
) {
    SettingRow(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        icon = icon,
        onClick = if (busy) null else {
            { viewModel.run(action) }
        },
    )
}

private fun titleOf(action: OpsAction): Int = when (action) {
    OpsAction.DOCTOR -> R.string.ops_doctor
    OpsAction.SECURITY_AUDIT -> R.string.ops_security_audit
    OpsAction.PROMPT_SIZE -> R.string.ops_prompt_size
    OpsAction.DUMP -> R.string.ops_dump
    OpsAction.CONFIG_MIGRATE -> R.string.ops_config_migrate
    OpsAction.CHECKPOINTS_PRUNE -> R.string.ops_checkpoints_prune
    OpsAction.BACKUP -> R.string.ops_backup
    OpsAction.RESTORE -> R.string.ops_restore
}

/** The command's output as it comes in, selectable, with its outcome on top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunSheet(run: OpsViewModel.Run, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val output = run.lines.joinToString("\n")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = HxSpace.screen).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(titleOf(run.action)),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = when {
                            run.running -> stringResource(R.string.ops_running)
                            run.error != null -> run.error
                            run.exitCode == 0 -> stringResource(R.string.ops_done)
                            run.exitCode != null -> stringResource(R.string.ops_failed_code, run.exitCode)
                            else -> stringResource(R.string.ops_finished_unknown)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            run.running -> MaterialTheme.colorScheme.onSurfaceVariant
                            run.succeeded -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        },
                    )
                }
                if (run.running) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else if (output.isNotEmpty()) {
                    TextButton(onClick = { copyText(context, output) }) { Text(stringResource(R.string.ops_copy)) }
                }
            }
            Surface(
                shape = RoundedCornerShape(HxRadius.sm),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth().padding(top = HxSpace.md).heightIn(min = 120.dp, max = 460.dp),
            ) {
                val vertical = rememberScrollState()
                LaunchedEffect(run.lines.size) { vertical.animateScrollTo(vertical.maxValue) }
                Box(modifier = Modifier.verticalScroll(vertical).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                    SelectionContainer {
                        Text(
                            text = output.ifEmpty { if (run.running) "…" else stringResource(R.string.ops_no_output) },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (run.running) {
                Text(
                    stringResource(R.string.ops_runs_on),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = HxSpace.sm),
                )
            }
        }
    }
}

@Composable
private fun DebugShareDialog(share: DebugShare, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ops_debug_share_done)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                share.urls.forEach { (label, url) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                            SelectionContainer { Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        }
                        TextButton(onClick = { copyText(context, url) }) { Text(stringResource(R.string.ops_copy)) }
                    }
                }
                share.failures.forEach { failure ->
                    Text(failure, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                share.autoDeleteSeconds?.let { seconds ->
                    Text(
                        stringResource(R.string.ops_debug_share_expiry, (seconds / 3600).coerceAtLeast(1)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (share.urls.isNotEmpty()) {
                TextButton(onClick = {
                    val text = share.urls.joinToString("\n") { (label, url) -> "$label: $url" }
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text(stringResource(R.string.ops_share)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ops_close)) } },
    )
}

@Composable
private fun AddHookDialog(
    events: List<String>,
    onDismiss: () -> Unit,
    onAdd: (event: String, command: String, matcher: String?, approve: Boolean) -> Unit,
) {
    var event by rememberSaveable { mutableStateOf(events.firstOrNull().orEmpty()) }
    var command by rememberSaveable { mutableStateOf("") }
    var matcher by rememberSaveable { mutableStateOf("") }
    var approve by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ops_hook_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedTextField(
                        value = event,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.ops_hook_event)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // A transparent layer over the read-only field opens the list.
                    Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        events.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                event = option
                                expanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.ops_hook_command)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = matcher,
                    onValueChange = { matcher = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.ops_hook_matcher)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = approve, onCheckedChange = { approve = it })
                    Text(stringResource(R.string.ops_hook_approve), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.ops_hook_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(event, command, matcher.ifBlank { null }, approve) },
                enabled = event.isNotBlank() && command.isNotBlank(),
            ) { Text(stringResource(R.string.ops_hook_add_go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Hermes", text))
}

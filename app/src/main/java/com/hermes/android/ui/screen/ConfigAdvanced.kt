package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import androidx.compose.material.icons.Icons
import com.hermes.android.ui.icons.filled.Description
import com.hermes.android.ui.icons.filled.Key
import com.hermes.android.ui.viewmodel.ConfigViewModel

/**
 * Advanced (approved design I): env editor + reload, MCP servers editor +
 * reload, a command console over shell.exec (with process.stop as the
 * emergency brake), the live gateway stderr log, and the raw config view.
 */
@Composable
internal fun AdvancedSection(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
    onOpen: (SettingsSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsCardGroup {
            SettingsNavRow(
                title = stringResource(R.string.env_keys_title),
                subtitle = stringResource(R.string.env_keys_subtitle),
                icon = Icons.Default.Key,
                onClick = { onOpen(SettingsSection.ENV_KEYS) },
            )
            com.hermes.android.ui.design.GroupDivider(startIndent = 62.dp)
            SettingsNavRow(
                title = stringResource(R.string.config_editor_title),
                subtitle = stringResource(R.string.config_editor_subtitle),
                icon = Icons.Default.Description,
                onClick = { onOpen(SettingsSection.CONFIG_EDITOR) },
            )
        }
        var editingEnv by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = com.hermes.android.ui.design.settingsCardColor()),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.environment_variables_env),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = { editingEnv = !editingEnv }) {
                        Text(if (editingEnv) stringResource(R.string.close) else stringResource(R.string.edit))
                    }
                }
                Text(
                    text = stringResource(R.string.hermes_env_api_keys_and_other_env_vars),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (editingEnv) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("\u26A0\uFE0F", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.advanced_setting_if_you_dont_know_what),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
                LaunchedEffect(Unit) { viewModel.loadEnvFile() }
                if (state.isLoadingEnv) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    var envText by remember(state.envText) { mutableStateOf(state.envText) }
                    OutlinedTextField(
                        value = envText,
                        onValueChange = { envText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 4,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.reloadEnv() },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.reload)) }
                        if (envText != state.envText) {
                            Button(
                                onClick = { viewModel.saveEnvFile(envText) },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.save)) }
                        }
                    }
                }
                }
            }
        }

        var editingMcp by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = com.hermes.android.ui.design.settingsCardColor()),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.mcp_servers),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = { editingMcp = !editingMcp }) {
                        Text(if (editingMcp) stringResource(R.string.close) else stringResource(R.string.edit))
                    }
                }
                Text(
                    text = stringResource(R.string.raw_json_config_yamls_mcp_servers),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (editingMcp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("\u26A0\uFE0F", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.advanced_setting_invalid_json_here_will),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
                LaunchedEffect(Unit) { viewModel.loadMcpServers() }
                if (state.isLoadingMcp) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    var mcpText by remember(state.mcpServersText) { mutableStateOf(state.mcpServersText) }
                    OutlinedTextField(
                        value = mcpText,
                        onValueChange = { mcpText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 4,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.reloadMcp() },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.reload)) }
                        if (mcpText != state.mcpServersText) {
                            Button(
                                onClick = { viewModel.saveMcpServers(mcpText) },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.save)) }
                        }
                    }
                }
                }
            }
        }

        SettingsLabel(stringResource(R.string.command_console))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = com.hermes.android.ui.design.settingsCardColor()),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.run_one_off_shell_commands_on_the_server),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                state.consoleEntries.forEach { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "$ ${entry.command}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = entry.output,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                            ),
                            color = if (entry.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 12,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                var consoleInput by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = consoleInput,
                    onValueChange = { consoleInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("df -h /") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                    ),
                    singleLine = true,
                    enabled = !state.isConsoleRunning,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            viewModel.runConsoleCommand(consoleInput)
                            consoleInput = ""
                        },
                        enabled = consoleInput.isNotBlank() && !state.isConsoleRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isConsoleRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.run))
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.stopProcesses() },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.stop_processes)) }
                }
            }
        }

        SettingsLabel(stringResource(R.string.gateway_log))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = com.hermes.android.ui.design.settingsCardColor()),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.live_server_stderr_fills_in_as_events),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (state.gatewayLog.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_log_lines_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = state.gatewayLog.takeLast(40).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }

        SettingsLabel(stringResource(R.string.connection_log))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = com.hermes.android.ui.design.settingsCardColor()),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.every_connect_drop_and_retry_with_what),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                // Loaded here rather than in the ViewModel's init: it is a disk read whose
                // only reader is this card, and it has to be re-read to be current.
                LaunchedEffect(Unit) { viewModel.loadConnectionJournal() }
                if (state.connectionJournal.isNotEmpty()) {
                    Text(
                        text = state.connectionJournal,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    Button(
                        onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(state.connectionJournal))
                        },
                        enabled = state.connectionJournal.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.copy)) }
                    OutlinedButton(
                        onClick = { viewModel.loadConnectionJournal() },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.refresh)) }
                    OutlinedButton(
                        onClick = { viewModel.clearConnectionJournal() },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.clear)) }
                }
            }
        }

        SettingsLabel(stringResource(R.string.current_configuration))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = com.hermes.android.ui.design.settingsCardColor(),
            ),
        ) {
            Text(
                text = state.configYaml.ifEmpty { "(empty)" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

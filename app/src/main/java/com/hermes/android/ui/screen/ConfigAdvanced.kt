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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var editingEnv by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                        text = t("Environment Variables (.env)", "متغیرهای محیطی (.env)"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = { editingEnv = !editingEnv }) {
                        Text(if (editingEnv) t("Close", "بستن") else t("Edit", "ویرایش"))
                    }
                }
                Text(
                    text = t("~/.hermes/.env — API keys and other env vars", "~/.hermes/.env — کلیدهای API و سایر متغیرها"),
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
                        text = t(
                            "Advanced setting. If you don't know what this does, don't touch it — a bad edit here can break the agent or the whole system.",
                            "تنظیمات پیشرفته. اگه نمی‌دونی این چیه، دستش نزن — یه ویرایش اشتباه اینجا می‌تونه ایجنت یا کل سیستم رو خراب کنه.",
                        ),
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
                        ) { Text(t("Reload", "بارگذاری مجدد")) }
                        if (envText != state.envText) {
                            Button(
                                onClick = { viewModel.saveEnvFile(envText) },
                                modifier = Modifier.weight(1f),
                            ) { Text(t("Save", "ذخیره")) }
                        }
                    }
                }
                }
            }
        }

        var editingMcp by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                        text = t("MCP Servers", "سرورهای MCP"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = { editingMcp = !editingMcp }) {
                        Text(if (editingMcp) t("Close", "بستن") else t("Edit", "ویرایش"))
                    }
                }
                Text(
                    text = t("Raw JSON — config.yaml's mcp_servers section", "JSON خام — بخش mcp_servers فایل config.yaml"),
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
                        text = t(
                            "Advanced setting. Invalid JSON here will fail to save; a wrong server entry can stop MCP tools from loading.",
                            "تنظیمات پیشرفته. JSON نامعتبر ذخیره نمی‌شه؛ یه ورودی اشتباه می‌تونه باعث بشه ابزارهای MCP لود نشن.",
                        ),
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
                        ) { Text(t("Reload", "بارگذاری مجدد")) }
                        if (mcpText != state.mcpServersText) {
                            Button(
                                onClick = { viewModel.saveMcpServers(mcpText) },
                                modifier = Modifier.weight(1f),
                            ) { Text(t("Save", "ذخیره")) }
                        }
                    }
                }
                }
            }
        }

        Text(
            text = t("Command Console", "کنسول فرمان"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = t(
                        "Run one-off shell commands on the server — for diagnostics without SSH.",
                        "اجرای فرمان‌های تکی روی سرور — برای عیب‌یابی بدون SSH.",
                    ),
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
                            Text(t("Run", "اجرا"))
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.stopProcesses() },
                        modifier = Modifier.weight(1f),
                    ) { Text(t("Stop processes", "توقف فرایندها")) }
                }
            }
        }

        Text(
            text = t("Gateway Log", "لاگ گیت\u200Cوی"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = t(
                        "Live server stderr — fills in as events arrive while the app is open.",
                        "stderr زندهٔ سرور — تا وقتی برنامه باز است با رسیدن رویدادها پر می‌شود.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (state.gatewayLog.isEmpty()) {
                    Text(
                        text = t("No log lines yet", "هنوز خطی نرسیده"),
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

        Text(
            text = t("Current Configuration", "پیکربندی فعلی"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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

package com.hermes.android.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.service.HermesGatewayService
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.GatewayConnectionUi
import com.hermes.android.ui.viewmodel.InstallInstructionsUi
import com.hermes.android.ui.viewmodel.InstallProgressUi
import com.hermes.android.ui.viewmodel.RuntimeEffect
import com.hermes.android.ui.viewmodel.RuntimeUiState
import com.hermes.android.ui.viewmodel.RuntimeViewModel
import kotlinx.coroutines.launch

/**
 * Runtime Setup screen — guides the user through:
 * 1. Detecting the runtime (migration adapter, in migration phase)
 * 2. Installing Hermes (via generated bash command run in external terminal)
 * 3. Verifying installation
 *
 * This screen depends ONLY on [RuntimeViewModel] — never on the runtime
 * package directly (Phase 1.5 Rule 1: Strict Layer Dependency).
 *
 * Reference: ADR-002 (Native Compose), ADR-009 (production embedded Python),
 *            Phase 1.5 Rule 1 (Strict Layer Dependency)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeSetupScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: RuntimeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    val installInstructions by viewModel.installInstructions.collectAsStateWithLifecycle()
    val installing by viewModel.installing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val isRemote = viewModel.isRemoteRuntime

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.detect()
    }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RuntimeEffect.StartForegroundService -> HermesGatewayService.start(context)
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    com.hermes.android.ui.design.HermesScaffold(
        title = if (isRemote) {
            com.hermes.android.ui.i18n.t("Server Connection", "اتصال سرور")
        } else {
            com.hermes.android.ui.i18n.t("Termux & Agent Setup", "راه‌اندازی ترموکس و ایجنت")
        },
        subtitle = if (isRemote) {
            com.hermes.android.ui.i18n.t("Address, token, and connection status", "آدرس، توکن و وضعیت اتصال")
        } else null,
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isRemote) {
                // ── Remote server flow (design A) ─────────────────────────
                // The live gateway connection state drives everything here;
                // RuntimeUiState only matters for the legacy Termux flow.
                val connection by viewModel.connectionState.collectAsStateWithLifecycle()

                Text(
                    text = "⬡",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = t("Connect to your Hermes server", "اتصال به سرور هرمس"),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = t(
                        "The agent lives on your server — this app is just the key to it.",
                        "عامل روی سرور شماست؛ این اپ فقط کلید آن است.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                ServerConfigCard(
                    initialUrl = serverConfig.serverUrl,
                    initialToken = serverConfig.token,
                    onSaveAndConnect = { url, token ->
                        viewModel.saveServerConfigAndConnect(url, token)
                    },
                )

                // Mockup-A shape: the live state sits as a centered chip
                // right under the Save & Connect button.
                ConnectionStatusBlock(
                    connection = connection,
                    onReconnect = { viewModel.startGateway() },
                )

                OutlinedButton(
                    onClick = { viewModel.runDoctor() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(t("Test connection", "آزمایش اتصال"))
                }

                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/NousResearch/hermes_agent"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(t("Server setup guide", "راهنمای راه‌اندازی سرور"))
                }
            } else {
                // ── Legacy Termux flow (unchanged) ─────────────────────────
                Text(
                    text = "Hermes",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Termux & Hermes Agent Gateway Connection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                when (val state = uiState) {
                    is RuntimeUiState.NotDetected -> {
                        Text("Detecting runtime...", style = MaterialTheme.typography.bodyLarge)
                        CircularProgressIndicator()
                    }

                    is RuntimeUiState.Detecting -> {
                        Text("Detecting runtime...", style = MaterialTheme.typography.bodyLarge)
                        CircularProgressIndicator()
                    }

                    is RuntimeUiState.Detected -> {
                        DetectedContent(
                            version = state.version,
                            diskFreeBytes = state.diskFreeBytes,
                            onShowInstallInstructions = { viewModel.prepareInstallInstructions() },
                            onStartInstall = { viewModel.startInstall() },
                            onLaunchHostApp = { viewModel.launchHostApp() },
                            onStartGateway = { viewModel.startGateway() },
                        )
                    }

                    is RuntimeUiState.Installing -> {
                        InstallingContent(
                            progress = installProgress,
                        )
                    }

                    is RuntimeUiState.Installed -> {
                        InstalledContent(
                            hermesVersion = state.hermesVersion,
                            onStartGateway = { viewModel.startGateway() },
                        )
                    }

                    is RuntimeUiState.Running -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Gateway is running 🎉",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    // Never render the token on screen.
                                    text = state.webSocketUrl.substringBefore("?token="),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.startGateway() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Restart Agent Gateway")
                                }
                            }
                        }
                    }

                    is RuntimeUiState.Error -> {
                        ErrorContent(
                            message = state.message,
                            onRetry = { viewModel.detect() },
                            onFetchLogs = { viewModel.fetchLogs() },
                        )
                    }
                }

                Button(
                    onClick = { viewModel.fetchLogs() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Fetch & View Logs")
                }

                OutlinedButton(
                    onClick = { viewModel.runDoctor() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Run diagnostics (hermes doctor)")
                }
            }

            AnimatedVisibility(visible = logs != null) {
                logs?.let { logText ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Execution Logs",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Button(
                                    onClick = {
                                        copyToClipboard(context, logText)
                                        scope.launch { snackbarHostState.showSnackbar("Logs copied to clipboard") }
                                    },
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Copy Logs")
                                }
                            }
                            Text(
                                text = "Logs are also saved to: /sdcard/Download/hermes_logs.txt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = logText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = installInstructions != null) {
                installInstructions?.let { instructions ->
                    InstallInstructionsCard(
                        instructions = instructions,
                        onCopy = {
                            instructions.command?.let { cmd ->
                                copyToClipboard(context, cmd)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Command copied to clipboard")
                                }
                            }
                        },
                    )
                }
            }

            if (installing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Server address + token input for the remote runtime.
 *
 * The token is rendered masked (password field) with a show/hide toggle,
 * and is never echoed anywhere else in the UI.
 */
@Composable
private fun ServerConfigCard(
    initialUrl: String,
    initialToken: String,
    onSaveAndConnect: (url: String, token: String) -> Unit,
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var token by rememberSaveable(initialToken) { mutableStateOf(initialToken) }
    var showToken by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = t("Hermes Server", "سرور هرمس"),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(t("Server address", "آدرس سرور")) },
                placeholder = { Text("wss://example.com:2083") },
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(t("Session token", "توکن نشست")) },
                singleLine = true,
                visualTransformation = if (showToken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showToken) t("Hide token", "پنهان کردن توکن") else t("Show token", "نمایش توکن"),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = t(
                    "The token must match HERMES_DASHBOARD_SESSION_TOKEN on your server.",
                    "توکن باید با HERMES_DASHBOARD_SESSION_TOKEN روی سرورت یکی باشه.",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onSaveAndConnect(url, token) },
                enabled = url.isNotBlank() && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(t("Save & Connect", "ذخیره و اتصال"))
            }
        }
    }
}

/**
 * Live connection state (mockup-A shape): a centered dot-chip under the
 * Save & Connect button, with the REAL failure cause — timeout / rejected
 * token / TLS — straight from the gateway's ConnectionState, plus the
 * reconnect attempt counter during backoff.
 */
@Composable
private fun ConnectionStatusBlock(
    connection: GatewayConnectionUi,
    onReconnect: () -> Unit,
) {
    val (color, label) = when (connection.state) {
        ChatConnectionState.Connected ->
            MaterialTheme.colorScheme.primary to t("Connected — gateway ready", "متصل — گیت‌وی آماده است")
        ChatConnectionState.Connecting ->
            MaterialTheme.colorScheme.tertiary to t("Connecting…", "در حال اتصال…")
        ChatConnectionState.Reconnecting ->
            MaterialTheme.colorScheme.tertiary to t("Reconnecting…", "در حال اتصال دوباره…")
        ChatConnectionState.Failed ->
            MaterialTheme.colorScheme.error to t("Connection failed", "اتصال ناموفق")
        ChatConnectionState.Disconnected ->
            MaterialTheme.colorScheme.onSurfaceVariant to t("Not connected", "متصل نیست")
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatusChip(label = label, color = color)
        connection.detail?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (connection.state == ChatConnectionState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        connection.reconnectAttempt?.let { attempt ->
            Text(
                text = t("Attempt $attempt", "تلاش $attempt"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (connection.state == ChatConnectionState.Failed ||
            connection.state == ChatConnectionState.Disconnected
        ) {
            TextButton(onClick = onReconnect) {
                Text(t("Try again", "تلاش دوباره"))
            }
        }
    }
}

@Composable
private fun DetectedContent(
    version: String?,
    diskFreeBytes: Long?,
    onShowInstallInstructions: () -> Unit,
    onStartInstall: () -> Unit,
    onLaunchHostApp: () -> Unit,
    onStartGateway: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Runtime detected ✓",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Version: ${version ?: "unknown"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            diskFreeBytes?.let {
                Text(
                    text = "Free disk: ${formatBytes(it)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = "Next step: install Hermes inside the runtime or start gateway.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }

    Button(
        onClick = onStartGateway,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Start Agent Gateway (Termux)")
    }

    val context = LocalContext.current
    Button(
        onClick = { openAppSettings(context) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Settings, contentDescription = null)
        Spacer(modifier = Modifier.size(8.dp))
        Text("Grant RUN_COMMAND Permission in Settings")
    }

    Button(
        onClick = onShowInstallInstructions,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Show install instructions")
    }

    Button(
        onClick = onLaunchHostApp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.OpenInNew, contentDescription = null)
        Spacer(modifier = Modifier.size(8.dp))
        Text("Open runtime host app")
    }

    OutlinedButton(
        onClick = onStartInstall,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Start Automated Install (RUN_COMMAND)")
    }
}

@Composable
private fun InstallingContent(progress: InstallProgressUi?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "Installing Hermes...",
                style = MaterialTheme.typography.titleMedium,
            )
            progress?.let { p ->
                Text(
                    text = "Stage: ${p.stage}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = p.message,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                p.percent?.let { pct ->
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledContent(hermesVersion: String?, onStartGateway: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Hermes installed ✓",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Version: ${hermesVersion ?: "unknown"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Next step: Start the Agent Gateway",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onStartGateway,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start Agent Gateway (Termux)")
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onFetchLogs: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("Retry") }
                Button(onClick = onFetchLogs) { Text("Fetch Logs") }
            }
        }
    }
}

@Composable
private fun InstallInstructionsCard(
    instructions: InstallInstructionsUi,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = instructions.title,
                style = MaterialTheme.typography.titleMedium,
            )
            instructions.steps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            instructions.command?.let { cmd ->
                Text(
                    text = cmd,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
            }
            if (instructions.command != null) {
                Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Copy command")
                }
            }
        }
    }
}

// ---- Helpers ----

private fun openAppSettings(context: Context) {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Hermes install command", text))
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024 * 1024)
    return when {
        mb >= 1024 -> "${mb / 1024} GB"
        else -> "$mb MB"
    }
}

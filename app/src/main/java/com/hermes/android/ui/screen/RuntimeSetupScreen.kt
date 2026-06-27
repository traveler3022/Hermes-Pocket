package com.hermes.android.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.viewmodel.InstallInstructionsUi
import com.hermes.android.ui.viewmodel.InstallProgressUi
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.detect()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termux & Agent Setup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            Text(
                text = "Hermes2",
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
                            Text("Gateway is running 🎉", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                text = state.webSocketUrl,
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
                    ErrorContent(message = state.message, onRetry = { viewModel.detect() })
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
private fun ErrorContent(message: String, onRetry: () -> Unit) {
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
            Button(onClick = onRetry) { Text("Retry") }
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

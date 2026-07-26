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

@Composable
internal fun DetectedContent(
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
internal fun InstallingContent(progress: InstallProgressUi?) {
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
internal fun InstalledContent(hermesVersion: String?, onStartGateway: () -> Unit) {
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
internal fun ErrorContent(message: String, onRetry: () -> Unit, onFetchLogs: () -> Unit) {
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
internal fun InstallInstructionsCard(
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

internal fun openAppSettings(context: Context) {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Hermes install command", text))
}

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024 * 1024)
    return when {
        mb >= 1024 -> "${mb / 1024} GB"
        else -> "$mb MB"
    }
}

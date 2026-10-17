package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.hermes.android.runtime.remote.SavedServer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.ContentCopy
import com.hermes.android.ui.icons.filled.Description
import com.hermes.android.ui.icons.filled.Dns
import com.hermes.android.ui.icons.filled.Visibility
import com.hermes.android.ui.icons.filled.VisibilityOff
import com.hermes.android.ui.icons.filled.OpenInNew
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.GatewayConnectionUi
import com.hermes.android.ui.viewmodel.InstallInstructionsUi
import com.hermes.android.ui.viewmodel.InstallProgressUi
import com.hermes.android.ui.viewmodel.RuntimeChoiceUi
import com.hermes.android.ui.viewmodel.RuntimeUiState
import com.hermes.android.ui.viewmodel.RuntimeViewModel
import com.hermes.android.ui.viewmodel.TailnetDeviceUi
import com.hermes.android.ui.viewmodel.TailscaleLoginEvent
import com.hermes.android.ui.viewmodel.TailscaleUi
import com.hermes.android.ui.viewmodel.TailscaleUiState
import com.hermes.android.ui.viewmodel.isRemoteSetupReady
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
    val runtimeChoice by viewModel.runtimeChoice.collectAsStateWithLifecycle()
    val isRemote = runtimeChoice == RuntimeChoiceUi.RemoteServer

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

    com.hermes.android.ui.design.HermesScaffold(
        title = if (isRemote) {
            com.hermes.android.ui.i18n.t("Server Connection", "اتصال سرور")
        } else if (runtimeChoice == RuntimeChoiceUi.BuiltInLinux) {
            com.hermes.android.ui.i18n.t("Built-in Linux & Agent", "لینوکس داخلی و ایجنت")
        } else {
            com.hermes.android.ui.i18n.t("Termux & Agent Setup", "راه‌اندازی ترموکس و ایجنت")
        },
        subtitle = if (isRemote) {
            com.hermes.android.ui.i18n.t("Address, sign-in, and connection status", "آدرس، ورود و وضعیت اتصال")
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
                // Same switch as on-device mode, or the user is stuck on the server once picked.
                RuntimeChoiceRow(
                    selected = runtimeChoice,
                    enabled = !installing,
                    onSelect = { viewModel.selectRuntime(it) },
                    showServer = true,
                )
                RemoteServerSetupContent(viewModel = viewModel, showDiagnostics = true)
            } else {
                // ── On-device flow: built-in Linux or Termux ───────────────
                Text(
                    text = "Hermes",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                RuntimeChoiceRow(
                    selected = runtimeChoice,
                    enabled = !installing,
                    onSelect = { viewModel.selectRuntime(it) },
                    showServer = true,
                )
                Text(
                    text = if (runtimeChoice == RuntimeChoiceUi.BuiltInLinux) {
                        stringResource(R.string.linux_runs_inside_this_app_no_termux)
                    } else {
                        "Termux & Hermes Agent Gateway Connection"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
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

                    is RuntimeUiState.Missing -> MissingContent(
                        storeUrl = state.storeUrl,
                        onCheckAgain = { viewModel.detect() },
                    )

                    is RuntimeUiState.Detected -> if (runtimeChoice == RuntimeChoiceUi.BuiltInLinux) {
                        BuiltInLinuxDetectedContent(
                            diskFreeBytes = state.diskFreeBytes,
                            onStartInstall = { viewModel.startInstall() },
                        )
                    } else {
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
                            startLabel = if (runtimeChoice == RuntimeChoiceUi.BuiltInLinux) {
                                stringResource(R.string.start_hermes)
                            } else {
                                "Start Agent Gateway (Termux)"
                            },
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
                            // Only the Termux log fetch writes this file.
                            if (!isRemote && runtimeChoice == RuntimeChoiceUi.Termux) {
                                Text(
                                    text = "Logs are also saved to: /sdcard/Download/hermes_logs.txt",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
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

/** Shared Tailscale, server sign-in, setup guide, and live gateway status for remote runtime setup. */
@Composable
internal fun RemoteServerSetupContent(
    viewModel: RuntimeViewModel,
    onContinue: (() -> Unit)? = null,
    showDiagnostics: Boolean = false,
) {
    val context = LocalContext.current
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val tailscaleUi by viewModel.tailscaleUi.collectAsStateWithLifecycle()
    val signedInAs by viewModel.remoteSignedInAs.collectAsStateWithLifecycle()
    val signingIn by viewModel.signingIn.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val ready = isRemoteSetupReady(signedInAs, connection)

    // This phone joins the server's tailnet directly; no Tailscale app is needed.
    LaunchedEffect(viewModel) { viewModel.startTailscale() }
    // Keep collecting while a browser tab covers the app during Tailscale sign-in.
    LaunchedEffect(viewModel) {
        viewModel.tailscaleLogin.collect { event ->
            when (event) {
                is TailscaleLoginEvent.Open -> openInBrowserTab(context, event.url)
                TailscaleLoginEvent.Done -> returnOverBrowserTab(context)
            }
        }
    }

    Text(
        text = "⬡",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(R.string.connect_to_your_hermes_server),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.hermes_runs_on_your_own_server_this_app),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    val savedServerList by viewModel.savedServerList.collectAsStateWithLifecycle()
    if (savedServerList.isNotEmpty()) {
        SavedServersCard(
            servers = savedServerList,
            currentUrl = serverConfig.serverUrl,
            connected = ready,
            busy = connecting || signingIn,
            onConnect = { viewModel.connectToSaved(it) },
            onForget = { viewModel.forgetSaved(it) },
            onRename = { server, name -> viewModel.renameSaved(server, name) },
        )
    }
    TailscaleCard(
        ui = tailscaleUi,
        serverUrl = serverConfig.serverUrl,
        onSignIn = { viewModel.signInToTailscale() },
        onSignOut = { viewModel.signOutOfTailscale() },
        onChoose = { viewModel.chooseServer(it) },
    )
    ServerConfigCard(
        initialUrl = serverConfig.serverUrl,
        signedInAs = signedInAs,
        signingIn = signingIn,
        connecting = connecting,
        onConnect = { url -> viewModel.connectToServer(url) },
        onCancelConnect = { viewModel.cancelConnect() },
        onSignIn = { url, user, password -> viewModel.signInToRemoteServer(url, user, password) },
        onSignOut = { viewModel.signOut() },
    )
    ConnectionStatusBlock(
        connection = connection,
        onReconnect = { viewModel.startGateway() },
    )

    if (showDiagnostics) {
        OutlinedButton(
            onClick = { viewModel.runDoctor() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Description, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.test_connection_2))
        }
    }

    ServerSetupGuide()

    if (onContinue != null) {
        Button(
            onClick = { if (isRemoteSetupReady(signedInAs, connection)) onContinue() },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.continue_text))
        }
    }
}

/**
 * Servers this phone connected to before. One tap connects again with the login kept for it, so
 * whoever connected through Tailscale never needs to know the address.
 */
@Composable
private fun SavedServersCard(
    servers: List<SavedServer>,
    currentUrl: String,
    connected: Boolean,
    busy: Boolean,
    onConnect: (SavedServer) -> Unit,
    onForget: (SavedServer) -> Unit,
    onRename: (SavedServer, String) -> Unit,
) {
    var forgetTarget by remember { mutableStateOf<SavedServer?>(null) }
    var renameTarget by remember { mutableStateOf<SavedServer?>(null) }
    renameTarget?.let { target ->
        var name by remember(target.url) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_saved_server)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    supportingText = { Text(target.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameTarget = null
                    onRename(target, name)
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    forgetTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { forgetTarget = null },
            title = { Text(stringResource(R.string.remove_saved_server)) },
            text = { Text(stringResource(R.string.remove_saved_server_body, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    forgetTarget = null
                    onForget(target)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { forgetTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.saved_servers), style = MaterialTheme.typography.titleMedium)
            servers.forEach { server ->
                val current = connected && server.url.trim().trimEnd('/') == currentUrl.trim().trimEnd('/')
                Surface(
                    onClick = { if (!current && !busy) onConnect(server) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                            Text(
                                text = server.name + if (current) "  ✓" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (current) stringResource(R.string.connected_now) else server.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { renameTarget = server }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.rename_saved_server),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { forgetTarget = server }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tailscale inside the app: this phone joins the user's tailnet itself, without the Tailscale app.
 * Shows Tailscale's own login, then the tailnet's devices; tapping one makes it the Hermes server.
 */
@Composable
private fun TailscaleCard(
    ui: TailscaleUi,
    serverUrl: String,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onChoose: (TailnetDeviceUi) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "Tailscale", style = MaterialTheme.typography.titleMedium)
            when (ui.state) {
                TailscaleUiState.Off, TailscaleUiState.Starting -> {
                    Text(stringResource(R.string.starting_tailscale), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                TailscaleUiState.NeedsLogin -> {
                    Text(
                        text = stringResource(R.string.sign_in_once_with_the_same_account_as),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sign_in_to_tailscale))
                    }
                }
                TailscaleUiState.NeedsApproval -> Text(
                    text = stringResource(R.string.approve_this_phone_in_the_tailscale),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TailscaleUiState.Unavailable -> Text(
                    text = stringResource(R.string.tailscale_isnt_available) + ui.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TailscaleUiState.Connected -> {
                    Text(
                        text = stringResource(R.string.connected_as_this_phone, ui.account, ui.device),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (ui.devices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_other_devices_in_this_tailnet_yet),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(stringResource(R.string.tap_your_server), style = MaterialTheme.typography.labelLarge)
                        val chosen = serverUrl.trim().trimEnd('/')
                        ui.devices.forEach { device ->
                            DeviceRow(
                                device = device,
                                selected = device.address.equals(chosen, ignoreCase = true),
                                onClick = { onChoose(device) },
                            )
                        }
                    }
                    TextButton(onClick = onSignOut) {
                        Text(stringResource(R.string.sign_out_of_tailscale))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: TailnetDeviceUi, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = (if (device.online) "● " else "○ ") + device.name + (if (selected) "  ✓" else ""),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            val path = when {
                !device.online -> stringResource(R.string.offline)
                device.direct -> stringResource(R.string.direct)
                device.relay.isNotEmpty() -> stringResource(R.string.via_relay, relayCity(device.relay))
                else -> null
            }
            Text(
                text = listOfNotNull(device.os.ifEmpty { null }, path).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tailscale relay (DERP) region codes, e.g. `tor`, as city names; an unknown code stays as is. */
@Composable
private fun relayCity(code: String): String {
    val city = RELAY_CITIES[code.lowercase()] ?: return code
    return com.hermes.android.ui.i18n.t(city.first, city.second)
}

private val RELAY_CITIES = mapOf(
    "nyc" to ("New York" to "نیویورک"), "sfo" to ("San Francisco" to "سان‌فرانسیسکو"),
    "sin" to ("Singapore" to "سنگاپور"), "fra" to ("Frankfurt" to "فرانکفورت"),
    "syd" to ("Sydney" to "سیدنی"), "blr" to ("Bangalore" to "بنگلور"),
    "tok" to ("Tokyo" to "توکیو"), "sao" to ("São Paulo" to "سائوپائولو"),
    "lhr" to ("London" to "لندن"), "dfw" to ("Dallas" to "دالاس"),
    "sea" to ("Seattle" to "سیاتل"), "tor" to ("Toronto" to "تورنتو"),
    "hnl" to ("Honolulu" to "هونولولو"), "nai" to ("Nairobi" to "نایروبی"),
    "mad" to ("Madrid" to "مادرید"), "ord" to ("Chicago" to "شیکاگو"),
    "lax" to ("Los Angeles" to "لس‌آنجلس"), "par" to ("Paris" to "پاریس"),
    "mia" to ("Miami" to "میامی"), "waw" to ("Warsaw" to "ورشو"),
    "dbi" to ("Dubai" to "دبی"), "jnb" to ("Johannesburg" to "ژوهانسبورگ"),
    "hkg" to ("Hong Kong" to "هنگ‌کنگ"), "den" to ("Denver" to "دنور"),
    "ams" to ("Amsterdam" to "آمستردام"), "nue" to ("Nuremberg" to "نورنبرگ"),
    "hel" to ("Helsinki" to "هلسینکی"), "ash" to ("Ashburn" to "اشبرن"),
)

/** Tailscale's login page in a browser tab on top of the app, so the app can come back by itself. */
private fun openInBrowserTab(context: Context, url: String) {
    val uri = Uri.parse(url)
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri) }
        .onFailure { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Brings the app back over the login tab, which lives in the app's own task. */
private fun returnOverBrowserTab(context: Context) {
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull() ?: return
    runCatching {
        activity.startActivity(
            Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }
}

/**
 * Server address and the Hermes sign-in for the remote runtime. Only `https://` addresses are
 * accepted: Hermes serves plain HTTP, so the encryption comes from Tailscale Serve in front of it.
 */
@Composable
private fun ServerConfigCard(
    initialUrl: String,
    signedInAs: String?,
    signingIn: Boolean,
    connecting: Boolean,
    onConnect: (url: String) -> Unit,
    onCancelConnect: () -> Unit,
    onSignIn: (url: String, username: String, password: String) -> Unit,
    onSignOut: () -> Unit,
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var manualLogin by rememberSaveable { mutableStateOf(false) }
    val busy = signingIn || connecting

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
                text = stringResource(R.string.hermes_server),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.server_address)) },
                placeholder = { Text("https://my-server.tail1234.ts.net") },
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            if (signedInAs != null) {
                Text(
                    text = stringResource(R.string.signed_in_as_every_connection_uses_a_new, signedInAs),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sign_out))
                }
            } else if (connecting) {
                // Stays until the app is connected: the setup command may still be running.
                Text(stringResource(R.string.server_pair_connecting), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.server_pair_waiting),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.server_pair_set_up_before),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onCancelConnect, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                Text(stringResource(R.string.server_pair_hint), style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { onConnect(url) },
                    enabled = !busy && url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.connect))
                }
                TextButton(onClick = { manualLogin = !manualLogin }, enabled = !busy) {
                    Text(stringResource(R.string.server_pair_manual))
                }
            }
            if (signedInAs == null && !connecting && manualLogin) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.hermes_username)) },
                    singleLine = true,
                    enabled = !signingIn,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.hermes_password)) },
                    singleLine = true,
                    enabled = !signingIn,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) stringResource(R.string.hide_password) else stringResource(R.string.show_password),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSignIn(url, username, password) },
                    enabled = !signingIn && url.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sign_in))
                }
                if (signingIn) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.the_hermes_login_you_set_on_the_server),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The repo's server setup (server/setup.sh) and its guide; they work once the repo is public. */
private const val SERVER_SETUP_COMMAND =
    "curl -fsSL https://raw.githubusercontent.com/traveler3022/Hermes-Pocket/main/server/setup.sh | sudo bash"
private const val SERVER_GUIDE_URL = "https://github.com/traveler3022/Hermes-Pocket/tree/main/server"

/**
 * How to put Hermes behind Tailscale Serve: one command on the server (server/setup.sh) keeps
 * Hermes on 127.0.0.1 and shares it as tailnet-only HTTPS; nothing is opened to the internet.
 */
@Composable
private fun ServerSetupGuide() {
    var open by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
                Text(if (open) stringResource(R.string.hide_server_setup) else stringResource(R.string.how_to_set_up_your_server))
            }
            if (open) {
                GuideStep(stringResource(R.string.s_1_on_the_server_where_hermes_is))
                GuideCode(SERVER_SETUP_COMMAND)
                GuideStep(
                    stringResource(R.string.it_installs_tailscale_if_needed_and),
                )
                GuideStep(
                    stringResource(R.string.s_2_in_this_app_tap_sign_in_to_tailscale),
                )
                TextButton(onClick = { runCatching { uriHandler.openUri(SERVER_GUIDE_URL) } }) {
                    Text(stringResource(R.string.full_guide_and_the_script_on_github))
                }
                Text(
                    text = stringResource(R.string.hermes_stays_on_127_0_0_1_and_no_port_is),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GuideStep(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

/** A command block; long-press to copy. */
@Composable
private fun GuideCode(code: String) {
    SelectionContainer {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(10.dp),
        )
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
            MaterialTheme.colorScheme.primary to stringResource(R.string.connected_gateway_ready)
        ChatConnectionState.Connecting ->
            MaterialTheme.colorScheme.tertiary to stringResource(R.string.connecting_2)
        ChatConnectionState.Reconnecting ->
            MaterialTheme.colorScheme.tertiary to stringResource(R.string.reconnecting_3)
        ChatConnectionState.Failed ->
            MaterialTheme.colorScheme.error to stringResource(R.string.connection_failed_2)
        ChatConnectionState.Disconnected ->
            MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.not_connected)
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
                text = stringResource(R.string.attempt, attempt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (connection.state == ChatConnectionState.Failed ||
            connection.state == ChatConnectionState.Disconnected
        ) {
            TextButton(onClick = onReconnect) {
                Text(stringResource(R.string.try_again_2))
            }
        }
    }
}

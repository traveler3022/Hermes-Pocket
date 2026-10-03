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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.hermes.android.ui.i18n.t
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
    val runtimeChoice by viewModel.runtimeChoice.collectAsStateWithLifecycle()

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
                // ── Remote server flow (design A) ─────────────────────────
                // The live gateway connection state drives everything here;
                // RuntimeUiState only matters for the legacy Termux flow.
                val connection by viewModel.connectionState.collectAsStateWithLifecycle()

                Text(
                    text = "⬡",
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

                // Tailscale inside the app: this phone joins the server's tailnet itself.
                LaunchedEffect(Unit) { viewModel.startTailscale() }
                // Not lifecycle-bound on purpose: the "come back" step arrives while the browser
                // tab covers this screen.
                LaunchedEffect(Unit) {
                    viewModel.tailscaleLogin.collect { event ->
                        when (event) {
                            is TailscaleLoginEvent.Open -> openInBrowserTab(context, event.url)
                            TailscaleLoginEvent.Done -> returnOverBrowserTab(context)
                        }
                    }
                }
                val tailscaleUi by viewModel.tailscaleUi.collectAsStateWithLifecycle()
                TailscaleCard(
                    ui = tailscaleUi,
                    serverUrl = serverConfig.serverUrl,
                    onSignIn = { viewModel.signInToTailscale() },
                    onSignOut = { viewModel.signOutOfTailscale() },
                    onChoose = { viewModel.chooseServer(it) },
                )

                val signedInAs by viewModel.remoteSignedInAs.collectAsStateWithLifecycle()
                val signingIn by viewModel.signingIn.collectAsStateWithLifecycle()
                ServerConfigCard(
                    initialUrl = serverConfig.serverUrl,
                    signedInAs = signedInAs,
                    signingIn = signingIn,
                    onSignIn = { url, user, password -> viewModel.signInToRemoteServer(url, user, password) },
                    onSignOut = { viewModel.signOut() },
                )

                // Mockup-A shape: the live state sits as a centered chip
                // right under the Sign in button.
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

                ServerSetupGuide()
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
                        t(
                            "Linux runs inside this app — no Termux needed",
                            "لینوکس داخل خود اپ اجرا می‌شود — بدون نیاز به ترموکس",
                        )
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
                                t("Start Hermes", "اجرای Hermes")
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
                    Text(t("Starting Tailscale…", "در حال روشن کردن Tailscale…"), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                TailscaleUiState.NeedsLogin -> {
                    Text(
                        text = t(
                            "Sign in once with the same account as your server, for example your Google account. " +
                                "The page opens over the app and closes by itself. No Tailscale app is needed on this phone.",
                            "یک بار با همان حسابی وارد شو که سرور با آن وارد شده، مثلاً حساب گوگل. " +
                                "صفحه روی اپ باز می‌شود و خودش بسته می‌شود. اپ Tailscale روی این گوشی لازم نیست.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Sign in to Tailscale", "ورود به Tailscale"))
                    }
                }
                TailscaleUiState.NeedsApproval -> Text(
                    text = t(
                        "Approve this phone in the Tailscale admin console (Machines).",
                        "این گوشی را در کنسول Tailscale، بخش Machines، تأیید کن.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TailscaleUiState.Unavailable -> Text(
                    text = t("Tailscale isn't available: ", "Tailscale در دسترس نیست: ") + ui.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TailscaleUiState.Connected -> {
                    Text(
                        text = t("Connected as ${ui.account}. This phone: ${ui.device}", "وصل با ${ui.account}. این گوشی: ${ui.device}"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (ui.devices.isEmpty()) {
                        Text(
                            text = t("No other devices in this tailnet yet.", "هنوز دستگاه دیگری در این شبکه نیست."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(t("Tap your server:", "سرورت را انتخاب کن:"), style = MaterialTheme.typography.labelLarge)
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
                        Text(t("Sign out of Tailscale", "خروج از Tailscale"))
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
                !device.online -> t("offline", "خاموش")
                device.direct -> t("direct", "مستقیم")
                device.relay.isNotEmpty() -> t("via relay ${device.relay}", "از رله‌ی ${device.relay}")
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
    onSignIn: (url: String, username: String, password: String) -> Unit,
    onSignOut: () -> Unit,
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

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
                placeholder = { Text("https://my-server.tail1234.ts.net") },
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                singleLine = true,
                enabled = !signingIn,
                modifier = Modifier.fillMaxWidth(),
            )
            if (signedInAs != null) {
                Text(
                    text = t(
                        "Signed in as $signedInAs. Every connection uses a new one-time ticket.",
                        "واردشده با $signedInAs. هر اتصال با یک تیکت یک‌بارمصرف تازه انجام می‌شود.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Text(t("Sign out", "خروج"))
                }
            } else {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(t("Hermes username", "نام کاربری هرمس")) },
                    singleLine = true,
                    enabled = !signingIn,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(t("Hermes password", "رمز هرمس")) },
                    singleLine = true,
                    enabled = !signingIn,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) t("Hide password", "پنهان کردن رمز") else t("Show password", "نمایش رمز"),
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
                    Text(t("Sign in", "ورود"))
                }
                if (signingIn) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = t(
                        "The Hermes login you set on the server. The app keeps it encrypted on this phone and signs " +
                            "in again by itself, so you type it once. Every connection uses a new one-time ticket.",
                        "همان نام کاربری و رمزی که روی سرور برای هرمس گذاشتی. اپ آن را رمزگذاری‌شده روی همین گوشی نگه " +
                            "می‌دارد و خودش دوباره وارد می‌شود؛ پس فقط یک بار تایپش می‌کنی. هر اتصال با یک تیکت یک‌بارمصرف تازه انجام می‌شود.",
                    ),
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
                Text(if (open) t("Hide server setup", "بستن راهنمای سرور") else t("How to set up your server", "راه‌اندازی سرور"))
            }
            if (open) {
                GuideStep(t("1. On the server where Hermes is installed, run:", "۱. روی سروری که Hermes رویش نصب است این را بزن:"))
                GuideCode(SERVER_SETUP_COMMAND)
                GuideStep(
                    t(
                        "It installs Tailscale if needed and prints a sign-in link: log in with the account you use " +
                            "in this app. If Tailscale asks to turn on HTTPS, open that link too. At the end it prints " +
                            "your Hermes username and password.",
                        "اگر لازم باشد Tailscale را نصب می‌کند و یک لینک ورود نشان می‌دهد: با همان حسابی که در این اپ " +
                            "داری وارد شو. اگر Tailscale خواست HTTPS را روشن کنی، آن لینک را هم باز کن. آخر کار نام کاربری " +
                            "و رمز هرمس را نشان می‌دهد.",
                    ),
                )
                GuideStep(
                    t(
                        "2. In this app, tap Sign in to Tailscale above and use the same account. Pick your server " +
                            "in the device list, then enter that username and password.",
                        "۲. در همین اپ، «ورود به Tailscale» را بزن و با همان حساب وارد شو. سرورت را در لیست دستگاه‌ها " +
                            "انتخاب کن و بعد همان نام کاربری و رمز را بزن.",
                    ),
                )
                TextButton(onClick = { runCatching { uriHandler.openUri(SERVER_GUIDE_URL) } }) {
                    Text(t("Full guide and the script on GitHub", "راهنمای کامل و اسکریپت در GitHub"))
                }
                Text(
                    text = t(
                        "Hermes stays on 127.0.0.1 and no port is opened to the internet: only your own Tailscale " +
                            "devices reach it, over an encrypted connection. Tip: turn on Device approval in Tailscale " +
                            "so every new device needs your OK.",
                        "Hermes روی 127.0.0.1 می‌ماند و هیچ پورتی به اینترنت باز نمی‌شود: فقط دستگاه‌های Tailscale خودت، " +
                            "با اتصال رمزنگاری‌شده، به آن می‌رسند. پیشنهاد: Device approval را در Tailscale روشن کن تا " +
                            "هر دستگاه جدید تأیید تو را لازم داشته باشد.",
                    ),
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


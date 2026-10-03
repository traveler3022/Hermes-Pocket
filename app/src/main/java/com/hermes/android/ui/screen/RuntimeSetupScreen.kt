package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
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

                val signedInAs by viewModel.remoteSignedInAs.collectAsStateWithLifecycle()
                val signingIn by viewModel.signingIn.collectAsStateWithLifecycle()
                ServerConfigCard(
                    initialUrl = serverConfig.serverUrl,
                    signedInAs = signedInAs,
                    signingIn = signingIn,
                    onSignIn = { url -> viewModel.signInToRemoteServer(url) },
                    onCancelSignIn = { viewModel.cancelSignIn() },
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
 * Server address and sign-in for the remote runtime. Only `https://` addresses are accepted:
 * Hermes serves plain HTTP, so the encryption comes from Tailscale Serve in front of it.
 */
@Composable
private fun ServerConfigCard(
    initialUrl: String,
    signedInAs: String?,
    signingIn: Boolean,
    onSignIn: (url: String) -> Unit,
    onCancelSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }

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
            when {
                signedInAs != null -> {
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
                }
                signingIn -> {
                    Text(
                        text = t(
                            "Finish signing in on your server's page in the browser, then come back here.",
                            "ورود را در صفحه‌ی سرورت در مرورگر تمام کن و به این‌جا برگرد.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = onCancelSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Cancel", "لغو"))
                    }
                }
                else -> {
                    Button(
                        onClick = { onSignIn(url) },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(t("Sign in", "ورود"))
                    }
                    Text(
                        text = t(
                            "Opens your server's login page. Your password stays there; the app gets a key, " +
                                "and every connection uses a new one-time ticket. Only https:// addresses are accepted.",
                            "صفحه‌ی ورود سرورت باز می‌شود. رمزت همان‌جا می‌ماند؛ اپ فقط یک کلید می‌گیرد " +
                                "و هر اتصال با یک تیکت یک‌بارمصرف تازه انجام می‌شود. فقط آدرس https:// قبول می‌شود.",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * How to put Hermes behind Tailscale Serve: Hermes stays on 127.0.0.1, Tailscale shares it as
 * tailnet-only HTTPS, and nothing on the server is opened to the internet.
 */
@Composable
private fun ServerSetupGuide() {
    var open by rememberSaveable { mutableStateOf(false) }
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
                GuideStep(t("1. On the server, install Tailscale and sign in:", "۱. روی سرور Tailscale را نصب کن و وارد شو:"))
                GuideCode("curl -fsSL https://tailscale.com/install.sh | sh\nsudo tailscale up")
                GuideStep(
                    t(
                        "2. In the Tailscale admin console turn on MagicDNS and HTTPS certificates. No domain needed: " +
                            "the server gets a free https://<machine>.<tailnet>.ts.net name with a real certificate.",
                        "۲. در کنسول Tailscale گزینه‌های MagicDNS و HTTPS certificates را روشن کن. دامنه لازم نیست: " +
                            "سرور یک اسم مجانی https://<machine>.<tailnet>.ts.net با گواهی واقعی می‌گیرد.",
                    ),
                )
                GuideStep(t("3. Give Hermes a login and that address, and keep it on 127.0.0.1:", "۳. به Hermes نام کاربری، رمز و همان آدرس را بده و روی 127.0.0.1 نگهش دار:"))
                GuideCode(
                    "export HERMES_DASHBOARD_BASIC_AUTH_USERNAME=you\n" +
                        "export HERMES_DASHBOARD_BASIC_AUTH_PASSWORD='a long password'\n" +
                        "export HERMES_DASHBOARD_BASIC_AUTH_SECRET=\$(openssl rand -hex 32)\n" +
                        "export HERMES_DASHBOARD_PUBLIC_URL=https://<machine>.<tailnet>.ts.net\n" +
                        "hermes dashboard --host 127.0.0.1 --port 9119 --no-open",
                )
                GuideStep(
                    t(
                        "4. Share it only inside your tailnet. Never use tailscale funnel: that one is public.",
                        "۴. فقط داخل شبکه‌ی Tailscale خودت به اشتراک بگذار. هرگز tailscale funnel نزن؛ آن عمومی است.",
                    ),
                )
                GuideCode("sudo tailscale serve --bg http://127.0.0.1:9119")
                GuideStep(
                    t(
                        "5. On this phone install Tailscale and sign in to the same account. Then enter " +
                            "https://<machine>.<tailnet>.ts.net above and tap Sign in.",
                        "۵. روی همین گوشی Tailscale را نصب کن و با همان حساب وارد شو. بعد " +
                            "https://<machine>.<tailnet>.ts.net را بالا بزن و «ورود» را بزن.",
                    ),
                )
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


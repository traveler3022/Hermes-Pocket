package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.data.OAuthProvider
import com.hermes.android.ui.design.GroupDivider
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.SectionHeader
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.icons.filled.ContentCopy
import com.hermes.android.ui.icons.filled.Dns
import com.hermes.android.ui.icons.filled.Key
import com.hermes.android.ui.viewmodel.ConfigUiState
import com.hermes.android.ui.viewmodel.ConfigViewModel
import com.hermes.android.ui.viewmodel.ProviderRow
import com.hermes.android.ui.viewmodel.ProvidersUiState
import com.hermes.android.ui.viewmodel.ProvidersViewModel

/**
 * Models → Providers: the two ways to add a provider (API key, account sign-in) above everything
 * already connected — Hermes' own providers from `model.options`, then custom servers from config.yaml.
 */
@Composable
internal fun ProvidersSection(
    configState: ConfigUiState,
    configViewModel: ConfigViewModel,
    snackbarHostState: SnackbarHostState,
    onOpen: (SettingsSection) -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProviderNotices(state, viewModel, snackbarHostState)
    LaunchedEffect(Unit) {
        viewModel.load()
        // Tells key providers from accounts in the list below; slow (a script), so in the background.
        viewModel.loadAccounts()
        configViewModel.loadProviders()
    }
    var selected by remember { mutableStateOf<ProviderRow?>(null) }
    var replacingKey by remember { mutableStateOf<ProviderRow?>(null) }
    val connected = state.connected

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        if (state.busy != null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        item { SectionHeader(t("Add a provider", "افزودن پرووایدر")) }
        item {
            SettingsGroup {
                SettingRow(
                    title = t("API key", "کلید API"),
                    subtitle = t(
                        "Hermes providers, or your own OpenAI-compatible server",
                        "پرووایدرهای هرمس، یا سرور سازگار با OpenAI خودت",
                    ),
                    icon = Icons.Default.Key,
                    onClick = { onOpen(SettingsSection.PROVIDER_KEYS) },
                )
                GroupDivider()
                SettingRow(
                    title = t("Sign in with account", "ورود با حساب"),
                    subtitle = t("OAuth: ChatGPT, Nous Portal, xAI Grok…", "OAuth: ChatGPT، Nous Portal، xAI Grok و…"),
                    icon = Icons.Default.AccountCircle,
                    onClick = { onOpen(SettingsSection.PROVIDER_ACCOUNTS) },
                )
            }
        }

        item { SectionHeader(t("Connected", "وصل‌شده‌ها")) }
        when {
            connected.isNotEmpty() -> item {
                SettingsGroup {
                    connected.forEachIndexed { index, row ->
                        if (index > 0) GroupDivider()
                        val account = state.signedInAccount(row.slug)
                        SettingRow(
                            title = row.name,
                            subtitle = listOfNotNull(
                                modelsText(row.modelCount),
                                account?.let { t("account", "حساب") },
                            ).joinToString(" · ").ifEmpty { null },
                            icon = if (account != null) Icons.Default.AccountCircle else Icons.Default.Key,
                            iconTint = if (row.isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            onClick = { selected = row },
                            trailing = if (row.isCurrent) {
                                { StatusChip(t("In use", "در حال استفاده"), MaterialTheme.colorScheme.primary) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            state.isLoading -> item { LoadingRow() }
            state.loadError != null -> item { ErrorRow(state.loadError.orEmpty(), onRetry = viewModel::load) }
            configState.providers.isEmpty() -> item {
                HintText(t("Nothing connected yet. Add a provider above.", "هنوز چیزی وصل نیست. از بالا یک پرووایدر اضافه کن."))
            }
        }

        if (configState.providers.isNotEmpty()) {
            item { SectionHeader(t("Custom servers", "سرورهای سفارشی")) }
            items(configState.providers, key = { "custom:" + it.slug }) { provider ->
                Box(Modifier.padding(horizontal = HxSpace.screen, vertical = HxSpace.xs)) {
                    ProviderCard(
                        provider = provider,
                        credentials = configState.credentialPool[provider.slug].orEmpty(),
                        isExpanded = configState.expandedProviderSlug == provider.slug,
                        onToggleExpand = { configViewModel.toggleProviderExpanded(provider.slug) },
                        onRemove = { configViewModel.removeProvider(provider.slug) },
                        onSetCredential = { key -> configViewModel.setCredential(provider.slug, key) },
                        onAddCredential = { key -> configViewModel.addCredential(provider.slug, key) },
                        onRemoveCredential = { credentialId -> configViewModel.removeCredentialEntry(provider.slug, credentialId) },
                        onSetPrimary = { configViewModel.setPrimaryProvider(provider) },
                    )
                }
            }
        }
    }

    selected?.let { row ->
        val account = state.signedInAccount(row.slug)
        ConnectedProviderDialog(
            row = row,
            account = account,
            // Until the accounts load, a key can't be told from an account sign-in.
            canReplaceKey = account == null && state.accountsLoaded,
            busy = state.busy != null,
            onReplaceKey = {
                selected = null
                replacingKey = row
            },
            onDisconnect = {
                viewModel.disconnect(row)
                selected = null
            },
            onDismiss = { selected = null },
        )
    }
    replacingKey?.let { row ->
        ProviderKeyDialog(
            row = row,
            keyUrl = viewModel.keyUrl(row.slug),
            busy = state.busy == row.slug,
            onSave = { key -> viewModel.saveKey(row, key) { replacingKey = null } },
            onDismiss = { replacingKey = null },
        )
    }
}

/** Providers → API key: a custom OpenAI-compatible server, or one of Hermes' key providers. */
@Composable
internal fun ProviderKeysSection(
    configViewModel: ConfigViewModel,
    snackbarHostState: SnackbarHostState,
    onOpen: (SettingsSection) -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProviderNotices(state, viewModel, snackbarHostState)
    LaunchedEffect(Unit) {
        if (state.rows.isEmpty()) viewModel.load()
    }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<ProviderRow?>(null) }
    var addingCustom by remember { mutableStateOf(false) }
    val providers = remember(state.rows, query) {
        state.keyProviders.filter {
            query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.slug.contains(query.trim(), ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        if (state.busy != null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        item { SectionHeader(t("Your own server", "سرور خودت")) }
        item {
            SettingsGroup {
                SettingRow(
                    title = t("Custom server", "سرور سفارشی"),
                    subtitle = t("Any OpenAI-compatible endpoint: address and key", "هر سرور سازگار با OpenAI: آدرس و کلید"),
                    icon = Icons.Default.Dns,
                    onClick = { addingCustom = true },
                )
            }
        }

        item { SectionHeader(t("Hermes providers", "پرووایدرهای هرمس")) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(t("Search providers…", "جستجوی پرووایدر…")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HxSpace.screen)
                    .padding(bottom = HxSpace.md),
            )
        }
        when {
            providers.isNotEmpty() -> item {
                SettingsGroup {
                    providers.forEachIndexed { index, row ->
                        if (index > 0) GroupDivider()
                        SettingRow(
                            title = row.name,
                            subtitle = row.keyEnv,
                            icon = Icons.Default.Key,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { editing = row },
                        )
                    }
                }
            }
            state.rows.isEmpty() && state.isLoading -> item { LoadingRow() }
            state.rows.isEmpty() && state.loadError != null -> item {
                ErrorRow(state.loadError.orEmpty(), onRetry = viewModel::load)
            }
            query.isNotBlank() -> item { HintText(t("No provider matches.", "پرووایدری پیدا نشد.")) }
            else -> item { HintText(t("Every key provider is already connected.", "همهٔ پرووایدرهای کلیدی وصل‌اند.")) }
        }
    }

    editing?.let { row ->
        ProviderKeyDialog(
            row = row,
            keyUrl = viewModel.keyUrl(row.slug),
            busy = state.busy == row.slug,
            onSave = { key ->
                // Once connected it lives in the Providers list, so go back there.
                viewModel.saveKey(row, key) {
                    editing = null
                    onOpen(SettingsSection.PROVIDERS)
                }
            },
            onDismiss = { editing = null },
        )
    }
    if (addingCustom) {
        AddProviderDialog(
            onDismiss = { addingCustom = false },
            onFetchModels = { url, key -> configViewModel.probeProviderModels(url, key) },
            onAdd = { slug, baseUrl, model, key ->
                configViewModel.addProvider(slug, baseUrl, model, key)
                addingCustom = false
                onOpen(SettingsSection.PROVIDERS)
            },
        )
    }
}

/**
 * Providers → Sign in with account: the desktop's Accounts list. Signing in runs the provider's
 * command in a terminal, as the desktop does for its terminal-only providers; "I've signed in"
 * re-reads the status.
 */
@Composable
internal fun ProviderAccountsSection(
    snackbarHostState: SnackbarHostState,
    onOpen: (SettingsSection) -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProviderNotices(state, viewModel, snackbarHostState)
    LaunchedEffect(Unit) { viewModel.loadAccounts() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val connected = state.accounts.filter { it.loggedIn }
    val others = state.accounts.filterNot { it.loggedIn }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        if (state.busy != null || (state.accountsLoading && state.accounts.isNotEmpty())) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        item {
            Text(
                text = t(
                    "Sign in with a subscription instead of an API key. The sign-in runs in a terminal: copy the command, run it in the Linux terminal (or Termux), then come back and tap “I've signed in”.",
                    "به‌جای کلید API با اشتراکت وارد شو. ورود در ترمینال انجام می‌شود: فرمان را کپی کن، در ترمینال لینوکس (یا ترموکس) اجرا کن، بعد برگرد و «وارد شدم» را بزن.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = HxSpace.screen).padding(top = HxSpace.lg),
            )
        }
        if (state.accounts.isEmpty()) {
            item {
                when {
                    state.accountsError != null ->
                        ErrorRow(state.accountsError.orEmpty(), onRetry = { viewModel.loadAccounts(force = true) })
                    state.accountsLoaded && !state.accountsLoading ->
                        HintText(t("This Hermes has no sign-in providers.", "این هرمس پرووایدر ورودی ندارد."))
                    else ->
                        LoadingRow(t("Checking sign-ins… this takes a few seconds", "در حال بررسی ورودها… چند ثانیه طول می‌کشد"))
                }
            }
        }
        if (connected.isNotEmpty()) {
            item { SectionHeader(t("Connected", "وصل‌شده")) }
            item { AccountGroup(connected, onSelect = { selectedId = it.id }) }
        }
        if (others.isNotEmpty()) {
            item {
                SectionHeader(if (connected.isEmpty()) t("Accounts", "حساب‌ها") else t("Other providers", "پرووایدرهای دیگر"))
            }
            item { AccountGroup(others, onSelect = { selectedId = it.id }) }
        }
    }

    // By id, so a recheck's fresh status shows in the open dialog.
    state.accounts.firstOrNull { it.id == selectedId }?.let { account ->
        AccountDialog(
            account = account,
            busy = state.busy == account.id,
            onRecheck = {
                viewModel.recheck(account) {
                    selectedId = null
                    onOpen(SettingsSection.PROVIDERS)
                }
            },
            onSignOut = {
                viewModel.signOut(account)
                selectedId = null
            },
            onDismiss = { selectedId = null },
        )
    }
}

@Composable
private fun AccountGroup(accounts: List<OAuthProvider>, onSelect: (OAuthProvider) -> Unit) {
    SettingsGroup {
        accounts.forEachIndexed { index, account ->
            if (index > 0) GroupDivider()
            SettingRow(
                title = account.name,
                subtitle = if (account.flow == "device_code") {
                    t("The command shows a link and a code for your browser", "فرمان یک لینک و یک کد برای مرورگر نشان می‌دهد")
                } else {
                    t("Sign in once in your terminal, then come back", "یک بار در ترمینال وارد شو و برگرد")
                },
                icon = Icons.Default.AccountCircle,
                iconTint = if (account.loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onSelect(account) },
                trailing = if (account.loggedIn) {
                    { StatusChip(t("Connected", "وصل"), MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
            )
        }
    }
}

/** A signed-out account: its command to copy and "I've signed in"; a signed-in one: sign out. */
@Composable
private fun AccountDialog(
    account: OAuthProvider,
    busy: Boolean,
    onRecheck: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var confirmingSignOut by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    !account.loggedIn -> t("Sign in with ${account.name}", "ورود با ${account.name}")
                    confirmingSignOut -> t("Sign out of ${account.name}?", "از ${account.name} خارج می‌شوی؟")
                    else -> account.name
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                when {
                    !account.loggedIn -> {
                        Text(t("Run this in a terminal, then tap “I've signed in”:", "این را در ترمینال اجرا کن، بعد «وارد شدم» را بزن:"))
                        Surface(
                            shape = RoundedCornerShape(HxRadius.sm),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = HxSpace.md),
                            ) {
                                SelectionContainer(Modifier.weight(1f)) {
                                    Text(
                                        text = account.cliCommand,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                IconButton(onClick = { clipboard.setText(AnnotatedString(account.cliCommand)) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = t("Copy", "کپی"))
                                }
                            }
                        }
                    }
                    confirmingSignOut -> Text(t("Hermes forgets this sign-in.", "هرمس این ورود را فراموش می‌کند."))
                    else -> {
                        Text(t("Connected.", "وصل است."))
                        if (!account.disconnectable) {
                            account.disconnectHint?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                !account.loggedIn -> TextButton(onClick = onRecheck, enabled = !busy) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(t("I've signed in", "وارد شدم"))
                    }
                }
                !account.disconnectable -> TextButton(onClick = onDismiss) { Text(t("Close", "بستن")) }
                confirmingSignOut -> TextButton(onClick = onSignOut, enabled = !busy) {
                    Text(t("Sign out", "خروج"), color = MaterialTheme.colorScheme.error)
                }
                else -> TextButton(onClick = { confirmingSignOut = true }, enabled = !busy) {
                    Text(t("Sign out", "خروج"), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            when {
                !account.loggedIn -> TextButton(onClick = onDismiss) { Text(t("Cancel", "لغو")) }
                !account.disconnectable -> Unit
                confirmingSignOut -> TextButton(onClick = { confirmingSignOut = false }) { Text(t("Cancel", "لغو")) }
                else -> TextButton(onClick = onDismiss) { Text(t("Close", "بستن")) }
            }
        },
    )
}

/** A connected Hermes provider: its details, a new key, or disconnect (after a confirm). */
@Composable
private fun ConnectedProviderDialog(
    row: ProviderRow,
    account: OAuthProvider?,
    canReplaceKey: Boolean,
    busy: Boolean,
    onReplaceKey: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirming) t("Disconnect ${row.name}?", "اتصال ${row.name} قطع شود؟") else row.name) },
        text = {
            if (confirming) {
                Text(
                    if (account != null) {
                        t("Hermes signs out of this account.", "هرمس از این حساب خارج می‌شود.")
                    } else {
                        t("Its API keys are removed from Hermes.", "کلیدهای API آن از هرمس پاک می‌شوند.")
                    },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
                    listOfNotNull(
                        account?.let { t("Signed in with an account", "با حساب وارد شده") },
                        modelsText(row.modelCount),
                        if (row.isCurrent) t("in use", "در حال استفاده") else null,
                    ).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ")) }
                    Text(
                        text = row.slug,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (canReplaceKey) {
                        TextButton(onClick = onReplaceKey, enabled = !busy) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(t("New API key", "کلید API تازه"), modifier = Modifier.padding(start = HxSpace.sm))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (confirming) onDisconnect() else confirming = true }, enabled = !busy) {
                Text(t("Disconnect", "قطع اتصال"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (confirming) confirming = false else onDismiss() }) {
                Text(if (confirming) t("Cancel", "لغو") else t("Close", "بستن"))
            }
        },
    )
}

/** The key for a Hermes provider: connects it, or replaces the key of a connected one. */
@Composable
private fun ProviderKeyDialog(
    row: ProviderRow,
    keyUrl: String?,
    busy: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                if (row.connected) t("New key for ${row.name}", "کلید تازه برای ${row.name}")
                else t("Connect ${row.name}", "اتصال ${row.name}"),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(row.keyEnv ?: t("API key", "کلید API")) },
                    singleLine = true,
                    enabled = !busy,
                    // Never readable on screen, and kept out of the keyboard's learning.
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                keyUrl?.let { url ->
                    TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                        Text(t("Get a key", "گرفتن کلید"))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }, enabled = key.isNotBlank() && !busy) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(t("Save", "ذخیره"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(t("Cancel", "لغو")) }
        },
    )
}

/** Shows the view model's notices in the Settings snackbar, once each. */
@Composable
private fun ProviderNotices(
    state: ProvidersUiState,
    viewModel: ProvidersViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val notice = state.notice?.let { t(it.en, it.fa) }
    LaunchedEffect(state.notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }
}

@Composable
private fun modelsText(count: Int): String? =
    if (count > 0) t("$count models", "$count مدل") else null

@Composable
private fun LoadingRow(text: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HxSpace.md),
        modifier = Modifier.padding(horizontal = HxSpace.screen, vertical = HxSpace.md),
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        text?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorRow(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = HxSpace.screen, vertical = HxSpace.sm)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(t("Retry", "تلاش دوباره")) }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = HxSpace.screen),
    )
}

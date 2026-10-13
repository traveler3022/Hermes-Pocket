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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.setup.OAuthProvider
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
import com.hermes.android.ui.viewmodel.SignInFlow

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
        item { SectionHeader(stringResource(R.string.add_a_provider)) }
        item {
            SettingsGroup {
                SettingRow(
                    title = stringResource(R.string.api_key_2),
                    subtitle = stringResource(R.string.hermes_providers_or_your_own_openai),
                    icon = Icons.Default.Key,
                    onClick = { onOpen(SettingsSection.PROVIDER_KEYS) },
                )
                GroupDivider()
                SettingRow(
                    title = stringResource(R.string.sign_in_with_account),
                    subtitle = stringResource(R.string.oauth_chatgpt_nous_portal_xai_grok),
                    icon = Icons.Default.AccountCircle,
                    onClick = { onOpen(SettingsSection.PROVIDER_ACCOUNTS) },
                )
            }
        }

        item { SectionHeader(stringResource(R.string.connected_4)) }
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
                                account?.let { stringResource(R.string.account) },
                            ).joinToString(" · ").ifEmpty { null },
                            icon = if (account != null) Icons.Default.AccountCircle else Icons.Default.Key,
                            iconTint = if (row.isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            onClick = { selected = row },
                            trailing = if (row.isCurrent) {
                                { StatusChip(stringResource(R.string.in_use), MaterialTheme.colorScheme.primary) }
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
                HintText(stringResource(R.string.nothing_connected_yet_add_a_provider))
            }
        }

        if (configState.providers.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.custom_servers)) }
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
        item { SectionHeader(stringResource(R.string.your_own_server)) }
        item {
            SettingsGroup {
                SettingRow(
                    title = stringResource(R.string.custom_server),
                    subtitle = stringResource(R.string.any_openai_compatible_endpoint_address),
                    icon = Icons.Default.Dns,
                    onClick = { addingCustom = true },
                )
            }
        }

        item { SectionHeader(stringResource(R.string.hermes_providers)) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_providers)) },
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
            query.isNotBlank() -> item { HintText(stringResource(R.string.no_provider_matches)) }
            else -> item { HintText(stringResource(R.string.every_key_provider_is_already_connected)) }
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
 * Providers → Sign in with account: the desktop's Accounts list. Device-code accounts connect in the
 * phone's browser: Hermes opens the page and connects by itself once the code is confirmed there.
 * The rest sign in with their command in a terminal, then "I've signed in" re-reads the status.
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
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    // Once the code is in, its page opens in the phone's browser, as on the desktop. The code goes on
    // the clipboard too: with the browser in front, the dialog showing it is out of sight.
    LaunchedEffect(state.openUrl) {
        val url = state.openUrl ?: return@LaunchedEffect
        state.signIn?.code?.userCode?.takeIf { it.isNotBlank() }?.let { clipboard.setText(AnnotatedString(it)) }
        runCatching { uriHandler.openUri(url) }
        viewModel.consumeOpenUrl()
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val connected = state.accounts.filter { it.loggedIn }
    val others = state.accounts.filterNot { it.loggedIn }

    fun connect(account: OAuthProvider) = viewModel.startSignIn(account) {
        // Once connected it lives in the Providers list, so go back there.
        selectedId = null
        onOpen(SettingsSection.PROVIDERS)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        if (state.busy != null || (state.accountsLoading && state.accounts.isNotEmpty())) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        item {
            Text(
                text = stringResource(R.string.sign_in_with_a_subscription_instead_of),
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
                        HintText(stringResource(R.string.this_hermes_has_no_sign_in_providers))
                    else ->
                        LoadingRow(stringResource(R.string.checking_sign_ins_this_takes_a_few))
                }
            }
        }
        if (connected.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.connected_5)) }
            item { AccountGroup(connected, onSelect = { selectedId = it.id }) }
        }
        if (others.isNotEmpty()) {
            item {
                SectionHeader(if (connected.isEmpty()) stringResource(R.string.accounts) else stringResource(R.string.other_providers))
            }
            item { AccountGroup(others, onSelect = { selectedId = it.id }) }
        }
    }

    // By id, so a recheck's fresh status shows in the open dialog.
    state.accounts.firstOrNull { it.id == selectedId }?.let { account ->
        val signIn = state.signIn?.takeIf { it.accountId == account.id }
        if (signIn != null) {
            DeviceSignInDialog(
                name = account.name,
                signIn = signIn,
                onRetry = { connect(account) },
                onReopen = { url -> runCatching { uriHandler.openUri(url) } },
                onCancel = {
                    viewModel.cancelSignIn()
                    selectedId = null
                },
            )
        } else {
            AccountDialog(
                account = account,
                busy = state.busy == account.id,
                onConnect = { connect(account) },
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
}

@Composable
private fun AccountGroup(accounts: List<OAuthProvider>, onSelect: (OAuthProvider) -> Unit) {
    SettingsGroup {
        accounts.forEachIndexed { index, account ->
            if (index > 0) GroupDivider()
            SettingRow(
                title = account.name,
                subtitle = if (account.flow == "device_code") {
                    stringResource(R.string.opens_a_sign_in_page_in_your_browser)
                } else {
                    stringResource(R.string.sign_in_once_in_your_terminal_then_come)
                },
                icon = Icons.Default.AccountCircle,
                iconTint = if (account.loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onSelect(account) },
                trailing = if (account.loggedIn) {
                    { StatusChip(stringResource(R.string.connected_6), MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * A signed-out account: Connect in the browser for device-code ones (the terminal stays a fallback),
 * otherwise its command to copy and "I've signed in". A signed-in one: sign out.
 */
@Composable
private fun AccountDialog(
    account: OAuthProvider,
    busy: Boolean,
    onConnect: () -> Unit,
    onRecheck: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmingSignOut by remember { mutableStateOf(false) }
    var useTerminal by remember { mutableStateOf(false) }
    val inBrowser = !account.loggedIn && account.flow == "device_code" && !useTerminal
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    !account.loggedIn -> stringResource(R.string.sign_in_with, account.name)
                    confirmingSignOut -> stringResource(R.string.sign_out_of, account.name)
                    else -> account.name
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                when {
                    inBrowser -> {
                        Text(
                            stringResource(R.string.hermes_opens_the_sign_in_page_in_your, account.name),
                        )
                        TextButton(onClick = { useTerminal = true }) {
                            Text(stringResource(R.string.use_the_terminal_instead))
                        }
                    }
                    !account.loggedIn -> {
                        Text(stringResource(R.string.run_this_in_a_terminal_then_tap_ive))
                        CopyableText(account.cliCommand)
                    }
                    confirmingSignOut -> Text(stringResource(R.string.hermes_forgets_this_sign_in))
                    else -> {
                        Text(stringResource(R.string.connected_7))
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
                inBrowser -> TextButton(onClick = onConnect) { Text(stringResource(R.string.connect)) }
                !account.loggedIn -> TextButton(onClick = onRecheck, enabled = !busy) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.ive_signed_in))
                    }
                }
                !account.disconnectable -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                confirmingSignOut -> TextButton(onClick = onSignOut, enabled = !busy) {
                    Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
                }
                else -> TextButton(onClick = { confirmingSignOut = true }, enabled = !busy) {
                    Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            when {
                !account.loggedIn -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                !account.disconnectable -> Unit
                confirmingSignOut -> TextButton(onClick = { confirmingSignOut = false }) { Text(stringResource(R.string.cancel)) }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
    )
}

/** A device-code sign-in in progress: the code to confirm in the browser while Hermes waits for it. */
@Composable
private fun DeviceSignInDialog(
    name: String,
    signIn: SignInFlow,
    onRetry: () -> Unit,
    onReopen: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val code = signIn.code
    val error = signIn.error
    AlertDialog(
        // Only Cancel ends a sign-in that's still waiting on the browser.
        onDismissRequest = { if (error != null) onCancel() },
        title = { Text(stringResource(R.string.sign_in_with, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.md)) {
                when {
                    error != null -> Text(t(error.en, error.fa), color = MaterialTheme.colorScheme.error)
                    code == null -> WaitingRow(stringResource(R.string.starting_sign_in))
                    else -> {
                        if (code.userCode.isNotBlank()) {
                            Text(
                                stringResource(R.string.we_opened_in_your_browser_enter_this, name),
                            )
                            CopyableText(code.userCode, MaterialTheme.typography.titleLarge)
                        } else {
                            Text(stringResource(R.string.we_opened_in_your_browser_confirm_the, name))
                        }
                        WaitingRow(stringResource(R.string.waiting_for_you_to_confirm))
                    }
                }
            }
        },
        confirmButton = {
            when {
                error != null -> TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
                code != null -> TextButton(onClick = { onReopen(code.url) }) { Text(stringResource(R.string.open_the_page_again)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(if (error != null) stringResource(R.string.close) else stringResource(R.string.cancel)) }
        },
    )
}

/** Monospace text to select, or copy with one tap: a command, a sign-in code. */
@Composable
private fun CopyableText(text: String, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    val clipboard = LocalClipboardManager.current
    Surface(
        shape = RoundedCornerShape(HxRadius.sm),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = HxSpace.md),
        ) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(text = text, style = style, fontFamily = FontFamily.Monospace)
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
            }
        }
    }
}

@Composable
private fun WaitingRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
        title = { Text(if (confirming) stringResource(R.string.disconnect, row.name) else row.name) },
        text = {
            if (confirming) {
                Text(
                    if (account != null) {
                        stringResource(R.string.hermes_signs_out_of_this_account)
                    } else {
                        stringResource(R.string.its_api_keys_are_removed_from_hermes)
                    },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
                    listOfNotNull(
                        account?.let { stringResource(R.string.signed_in_with_an_account) },
                        modelsText(row.modelCount),
                        if (row.isCurrent) stringResource(R.string.in_use_2) else null,
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
                            Text(stringResource(R.string.new_api_key), modifier = Modifier.padding(start = HxSpace.sm))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (confirming) onDisconnect() else confirming = true }, enabled = !busy) {
                Text(stringResource(R.string.disconnect_2), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (confirming) confirming = false else onDismiss() }) {
                Text(if (confirming) stringResource(R.string.cancel) else stringResource(R.string.close))
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
                if (row.connected) stringResource(R.string.new_key_for, row.name)
                else stringResource(R.string.connect_2, row.name),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(row.keyEnv ?: stringResource(R.string.api_key_2)) },
                    singleLine = true,
                    enabled = !busy,
                    // Never readable on screen, and kept out of the keyboard's learning.
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                keyUrl?.let { url ->
                    TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                        Text(stringResource(R.string.get_a_key))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }, enabled = key.isNotBlank() && !busy) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
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
    if (count > 0) stringResource(R.string.models_2, count) else null

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
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
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

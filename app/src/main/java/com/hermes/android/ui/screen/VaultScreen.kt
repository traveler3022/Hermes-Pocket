package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.vault.VaultItem
import com.hermes.android.domain.vault.VaultKind
import com.hermes.android.domain.vault.VaultSource
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.viewmodel.VaultViewModel

/**
 * Settings › Credential vault, after the desktop's panel: what the agent may fill on web
 * pages (logins, cards, addresses — the secrets never come back to the app), and the
 * password managers it can read once unlocked.
 */
@Composable
fun VaultScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf<VaultSource?>(null) }
    var deleting by remember { mutableStateOf<VaultItem?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    // A successful add / unlock closes its dialog.
    LaunchedEffect(state.dialogDone) {
        adding = false
        unlocking = null
    }

    HermesScaffold(
        title = stringResource(R.string.vault_title),
        subtitle = stringResource(R.string.vault_subtitle),
        onBack = onNavigateBack,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = viewModel::load, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
            }
            IconButton(onClick = { viewModel.clearDialogError(); adding = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.vault_add))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading && state.items.isEmpty() && state.managers.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.loadError != null && state.items.isEmpty() && state.managers.isEmpty() -> HermesEmptyState(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.vault_title),
                    caption = state.loadError,
                    actionLabel = stringResource(R.string.reload),
                    onAction = viewModel::load,
                )
                else -> VaultList(
                    state = state,
                    onDelete = { deleting = it },
                    onToggle = viewModel::setManagerEnabled,
                    onUnlock = { viewModel.clearDialogError(); unlocking = it },
                    onLock = viewModel::lock,
                )
            }
        }
    }

    if (adding) {
        AddVaultItemDialog(
            busy = state.busy,
            error = state.dialogError,
            onDismiss = { adding = false },
            onSave = viewModel::add,
        )
    }
    unlocking?.let { source ->
        UnlockManagerDialog(
            source = source,
            busy = state.busy,
            error = state.dialogError,
            onDismiss = { unlocking = null },
            onUnlock = { viewModel.unlock(source, it) },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.vault_delete_title, item.label)) },
            text = { Text(stringResource(R.string.vault_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(item); deleting = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun VaultList(
    state: VaultViewModel.UiState,
    onDelete: (VaultItem) -> Unit,
    onToggle: (VaultSource, Boolean) -> Unit,
    onUnlock: (VaultSource) -> Unit,
    onLock: (VaultSource) -> Unit,
) {
    val managerNames = state.managers.associate { it.name to it.displayName }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, top = HxSpace.sm, bottom = HxSpace.xl),
        verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        item(key = "items-head") {
            SectionHead(stringResource(R.string.vault_items), stringResource(R.string.vault_items_caption))
        }
        if (state.items.isEmpty()) {
            item(key = "items-empty") {
                Text(
                    stringResource(R.string.vault_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.items, key = { "item-" + it.backend + it.id }) { item ->
            VaultItemRow(item, managerNames[item.backend], onDelete)
        }
        item(key = "managers-head") {
            SectionHead(stringResource(R.string.vault_managers), stringResource(R.string.vault_managers_caption))
        }
        items(state.managers, key = { "source-" + it.name }) { source ->
            ManagerRow(source, state.busy, onToggle, onUnlock, onLock)
        }
    }
}

@Composable
private fun SectionHead(title: String, caption: String) {
    Column(modifier = Modifier.padding(top = HxSpace.md)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VaultItemRow(item: VaultItem, managerName: String?, onDelete: (VaultItem) -> Unit) {
    val local = item.backend == "local"
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = HxSpace.inner, top = HxSpace.md, bottom = HxSpace.md, end = HxSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val kind = when (item.kind) {
                    "login" -> stringResource(R.string.vault_kind_login)
                    "payment" -> stringResource(R.string.vault_kind_payment)
                    "address" -> stringResource(R.string.vault_kind_address)
                    else -> item.kind
                }
                // kind · identifier · origin (left out when the label already is the host) · 2FA · source
                val facts = listOfNotNull(
                    kind,
                    item.identifier,
                    item.origin?.takeIf { it.removePrefix("https://").removePrefix("http://") != item.label },
                    if (item.hasOtp) stringResource(R.string.vault_two_factor) else null,
                    if (!local) managerName ?: item.backend else null,
                )
                Text(
                    facts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Items from a password manager are managed there.
            if (local) {
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun ManagerRow(
    source: VaultSource,
    busy: Boolean,
    onToggle: (VaultSource, Boolean) -> Unit,
    onUnlock: (VaultSource) -> Unit,
    onLock: (VaultSource) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = HxSpace.inner, vertical = HxSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(source.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        !source.installed -> stringResource(R.string.vault_manager_not_installed)
                        !source.enabled -> stringResource(R.string.vault_manager_off)
                        source.unlocked -> stringResource(R.string.vault_manager_unlocked)
                        else -> stringResource(R.string.vault_manager_locked)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (source.installed && source.enabled) {
                if (source.unlocked) {
                    TextButton(onClick = { onLock(source) }, enabled = !busy) { Text(stringResource(R.string.vault_lock)) }
                } else {
                    OutlinedButton(onClick = { onUnlock(source) }, enabled = !busy) { Text(stringResource(R.string.vault_unlock)) }
                }
            }
            if (source.installed) {
                Switch(checked = source.enabled, onCheckedChange = { onToggle(source, it) }, enabled = !busy)
            }
        }
    }
}

@Composable
private fun UnlockManagerDialog(
    source: VaultSource,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onUnlock: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    // A refused password is not left in the field.
    LaunchedEffect(error) { if (error != null) password = "" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_unlock_title, source.displayName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                Text(stringResource(R.string.vault_unlock_settings_desc, source.displayName), style = MaterialTheme.typography.bodySmall)
                SecretField(password, { password = it }, stringResource(R.string.vault_master_password))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onUnlock(password) }, enabled = password.isNotEmpty() && !busy) {
                Text(stringResource(R.string.vault_unlock))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The desktop's Add dialog: one form per kind, the server's field names. */
@Composable
private fun AddVaultItemDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (VaultKind, String, String?, Map<String, String>) -> Unit,
) {
    var kind by remember { mutableStateOf(VaultKind.LOGIN) }
    var label by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var idType by remember { mutableStateOf("email") }
    val fields = remember { mutableStateOf(emptyMap<String, String>()) }
    var localError by remember { mutableStateOf<String?>(null) }
    fun field(key: String) = fields.value[key].orEmpty()
    fun setField(key: String, value: String) { fields.value = fields.value + (key to value) }

    val labelRequired = stringResource(R.string.vault_label_required)
    val originInvalid = stringResource(R.string.vault_origin_invalid)
    val missingFields = stringResource(R.string.vault_fields_required)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_add)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                    VaultKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = {
                                Text(
                                    when (k) {
                                        VaultKind.LOGIN -> stringResource(R.string.vault_kind_login)
                                        VaultKind.PAYMENT -> stringResource(R.string.vault_kind_payment)
                                        VaultKind.ADDRESS -> stringResource(R.string.vault_kind_address)
                                    },
                                )
                            },
                        )
                    }
                }
                PlainField(label, { label = it }, stringResource(R.string.vault_label))
                PlainField(origin, { origin = it }, stringResource(R.string.vault_origin), KeyboardType.Uri, "https://example.com")
                when (kind) {
                    VaultKind.LOGIN -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                            listOf(
                                "email" to stringResource(R.string.vault_id_email),
                                "phone" to stringResource(R.string.vault_id_phone),
                                "username" to stringResource(R.string.vault_id_username),
                            ).forEach { (type, text) ->
                                FilterChip(selected = idType == type, onClick = { idType = type }, label = { Text(text) })
                            }
                        }
                        PlainField(
                            field("identifier"), { setField("identifier", it) }, stringResource(R.string.vault_identifier),
                            when (idType) { "email" -> KeyboardType.Email; "phone" -> KeyboardType.Phone; else -> KeyboardType.Text },
                        )
                        SecretField(field("password"), { setField("password", it) }, stringResource(R.string.vault_password))
                        SecretField(field("otp_secret"), { setField("otp_secret", it) }, stringResource(R.string.vault_otp_secret))
                    }
                    VaultKind.PAYMENT -> {
                        SecretField(field("card_number"), { setField("card_number", it) }, stringResource(R.string.vault_card_number), KeyboardType.NumberPassword)
                        PlainField(field("cardholder_name"), { setField("cardholder_name", it) }, stringResource(R.string.vault_card_name))
                        Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                            Box(Modifier.weight(1f)) {
                                PlainField(field("exp_month"), { setField("exp_month", it) }, stringResource(R.string.vault_exp_month), KeyboardType.Number, "MM")
                            }
                            Box(Modifier.weight(1f)) {
                                PlainField(field("exp_year"), { setField("exp_year", it) }, stringResource(R.string.vault_exp_year), KeyboardType.Number, "YYYY")
                            }
                        }
                        SecretField(field("cvc"), { setField("cvc", it) }, stringResource(R.string.vault_cvc), KeyboardType.NumberPassword)
                        PlainField(field("billing_postal_code"), { setField("billing_postal_code", it) }, stringResource(R.string.vault_postal))
                    }
                    VaultKind.ADDRESS -> {
                        PlainField(field("address_line1"), { setField("address_line1", it) }, stringResource(R.string.vault_line1))
                        PlainField(field("address_line2"), { setField("address_line2", it) }, stringResource(R.string.vault_line2))
                        PlainField(field("city"), { setField("city", it) }, stringResource(R.string.vault_city))
                        PlainField(field("state"), { setField("state", it) }, stringResource(R.string.vault_state))
                        PlainField(field("postal_code"), { setField("postal_code", it) }, stringResource(R.string.vault_postal))
                        PlainField(field("country"), { setField("country", it) }, stringResource(R.string.vault_country))
                    }
                }
                (localError ?: error)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    localError = null
                    val cleanOrigin = origin.trim()
                    val secret = vaultSecret(kind, idType, fields.value)
                    val required = when (kind) {
                        VaultKind.LOGIN -> listOf("identifier", "password")
                        VaultKind.PAYMENT -> listOf("card_number", "exp_month", "exp_year", "cvc")
                        VaultKind.ADDRESS -> listOf("address_line1", "city", "postal_code", "country")
                    }
                    localError = when {
                        label.isBlank() -> labelRequired
                        // Every kind is filled only on the origin it was saved for.
                        !isWebOrigin(cleanOrigin) -> originInvalid
                        required.any { secret[it].isNullOrEmpty() } -> missingFields
                        else -> null
                    }
                    if (localError == null) onSave(kind, label.trim(), cleanOrigin, secret)
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The payload as the desktop builds it: trimmed, card number without spaces, empty optionals left out. */
internal fun vaultSecret(kind: VaultKind, idType: String, fields: Map<String, String>): Map<String, String> {
    fun f(key: String) = fields[key].orEmpty()
    return when (kind) {
        VaultKind.LOGIN -> buildMap {
            put("identifier_type", idType)
            put("identifier", f("identifier").trim())
            put("password", f("password"))
            f("otp_secret").trim().takeIf { it.isNotEmpty() }?.let { put("otp_secret", it) }
        }
        VaultKind.PAYMENT -> mapOf(
            "card_number" to f("card_number").filterNot { it.isWhitespace() },
            "cardholder_name" to f("cardholder_name").trim(),
            "exp_month" to f("exp_month").trim(),
            "exp_year" to f("exp_year").trim(),
            "cvc" to f("cvc"),
            "billing_postal_code" to f("billing_postal_code").trim(),
        )
        VaultKind.ADDRESS -> buildMap {
            put("address_line1", f("address_line1").trim())
            put("city", f("city").trim())
            put("postal_code", f("postal_code").trim())
            put("country", f("country").trim())
            f("address_line2").trim().takeIf { it.isNotEmpty() }?.let { put("address_line2", it) }
            f("state").trim().takeIf { it.isNotEmpty() }?.let { put("state", it) }
        }
    }
}

internal fun isWebOrigin(value: String): Boolean {
    val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
    return (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
}

@Composable
private fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Masked, and the keyboard is told it is a password so it neither learns nor suggests it. */
@Composable
private fun SecretField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Password,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

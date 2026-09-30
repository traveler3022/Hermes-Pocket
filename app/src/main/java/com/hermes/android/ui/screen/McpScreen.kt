package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.GroupDivider
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.SectionHeader
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.icons.filled.Dns
import com.hermes.android.ui.icons.filled.Link
import com.hermes.android.ui.icons.filled.Terminal
import com.hermes.android.ui.viewmodel.McpCatalogEntry
import com.hermes.android.ui.viewmodel.McpProbe
import com.hermes.android.ui.viewmodel.McpServer
import com.hermes.android.ui.viewmodel.McpStatus
import com.hermes.android.ui.viewmodel.McpViewModel
import com.hermes.android.ui.viewmodel.canSignIn
import com.hermes.android.ui.viewmodel.statusWith

/**
 * MCP servers — Hermes desktop's MCP page on the gateway's mcp.* RPCs: your servers with a
 * live status each (tap for test, sign-in, API key, remove), the curated catalog, and adding a
 * server by URL or command. The raw mcp_servers editor stays under Advanced.
 *
 * Depends ONLY on [McpViewModel].
 */
@Composable
fun McpScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: McpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    val notice = state.notice?.let { t(it.en, it.fa) }
    LaunchedEffect(state.notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }
    // Sign-in happens in the phone's own browser; the gateway's loopback takes the redirect.
    LaunchedEffect(state.openUrl) {
        val url = state.openUrl ?: return@LaunchedEffect
        viewModel.urlOpened()
        try {
            uriHandler.openUri(url)
        } catch (e: Exception) {
            viewModel.browserMissing()
        }
    }

    var selected by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var catalogPick by remember { mutableStateOf<McpCatalogEntry?>(null) }
    var keyFor by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }

    HermesScaffold(
        title = t("MCP Servers", "سرورهای MCP"),
        subtitle = if (state.servers.isEmpty()) null else {
            t("${state.servers.size} configured", "${state.servers.size} سرور")
        },
        onBack = onNavigateBack,
        actions = {
            IconButton(onClick = { viewModel.load(force = true) }, enabled = !state.isBusy) {
                Icon(Icons.Default.Refresh, contentDescription = t("Refresh", "تازه‌سازی"))
            }
        },
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = t("Add server", "افزودن سرور"))
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@HermesScaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (state.isBusy) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            state.signingIn?.let { name ->
                item { SignInBanner(name, onCancel = viewModel::cancelSignIn) }
            }
            item { SectionHeader(t("Your servers", "سرورهای شما")) }
            if (state.servers.isEmpty()) {
                item {
                    Text(
                        t(
                            "No MCP servers yet. Add one from the catalog below or with +.",
                            "هنوز سرور MCP نداری. از کاتالوگ پایین یا دکمهٔ + اضافه کن.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = HxSpace.screen),
                    )
                }
            } else {
                item {
                    SettingsGroup {
                        state.servers.forEachIndexed { index, server ->
                            if (index > 0) GroupDivider()
                            val probe = state.probes[server.name]
                            val status = server.statusWith(probe)
                            SettingRow(
                                title = server.name,
                                subtitle = listOfNotNull(
                                    statusText(server, status, probe),
                                    server.url ?: server.command,
                                ).joinToString(" · ").ifEmpty { null },
                                icon = if (server.url != null) Icons.Default.Link else Icons.Default.Terminal,
                                iconTint = statusColor(status),
                                onClick = { selected = server.name },
                            )
                        }
                    }
                }
            }
            if (state.catalog.isNotEmpty()) {
                item { SectionHeader(t("Catalog", "کاتالوگ")) }
                item {
                    SettingsGroup {
                        state.catalog.forEachIndexed { index, entry ->
                            if (index > 0) GroupDivider()
                            SettingRow(
                                title = entry.name,
                                subtitle = entry.description.ifBlank { null },
                                icon = Icons.Default.Dns,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                trailing = {
                                    if (entry.installed) {
                                        Text(
                                            t("Added", "اضافه شده"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    } else {
                                        TextButton(
                                            onClick = {
                                                if (entry.requires.isEmpty()) {
                                                    viewModel.addFromCatalog(entry, emptyMap())
                                                } else {
                                                    catalogPick = entry
                                                }
                                            },
                                            enabled = !state.isBusy,
                                        ) { Text(t("Add", "افزودن")) }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { name ->
        state.servers.firstOrNull { it.name == name }?.let { server ->
            ServerDialog(
                server = server,
                probe = state.probes[name],
                busy = state.isBusy || state.signingIn != null,
                onTest = { viewModel.test(name) },
                onSignIn = { selected = null; viewModel.signIn(name) },
                onApiKey = { selected = null; keyFor = name },
                onRemove = { selected = null; removing = name },
                onDismiss = { selected = null },
            )
        }
    }

    if (adding) {
        AddServerDialog(
            onAdd = { name, url, command, args, token ->
                adding = false
                viewModel.addServer(name, url, command, args, token)
            },
            onDismiss = { adding = false },
        )
    }

    catalogPick?.let { entry ->
        CatalogKeysDialog(
            entry = entry,
            onAdd = { keys ->
                catalogPick = null
                viewModel.addFromCatalog(entry, keys)
            },
            onDismiss = { catalogPick = null },
        )
    }

    keyFor?.let { name ->
        ApiKeyDialog(
            name = name,
            onSave = { value, envVar ->
                keyFor = null
                viewModel.setApiKey(name, value, envVar)
            },
            onDismiss = { keyFor = null },
        )
    }

    removing?.let { name ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(t("Remove server?", "سرور حذف شود؟")) },
            text = {
                Text(t(
                    "\"$name\" will be removed from config.yaml.",
                    "«$name» از config.yaml حذف می‌شود.",
                ))
            },
            confirmButton = {
                TextButton(onClick = { removing = null; viewModel.remove(name) }) {
                    Text(t("Remove", "حذف"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(t("Cancel", "انصراف")) }
            },
        )
    }
}

@Composable
private fun SignInBanner(name: String, onCancel: () -> Unit) {
    SettingsGroup(modifier = Modifier.padding(top = HxSpace.md)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HxSpace.inner),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HxSpace.md),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                t(
                    "Finish signing in to $name in your browser, then come back.",
                    "ورود به $name رو توی مرورگر تموم کن و برگرد.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(t("Cancel", "انصراف")) }
        }
    }
}

@Composable
private fun statusText(server: McpServer, status: McpStatus, probe: McpProbe?): String? = when (status) {
    McpStatus.OK -> {
        val tools = probe?.tools?.size ?: 0
        val prompts = probe?.prompts ?: 0
        val resources = probe?.resources ?: 0
        buildList {
            add(t("$tools tools", "$tools ابزار"))
            if (prompts > 0) add(t("$prompts prompts", "$prompts پرامپت"))
            if (resources > 0) add(t("$resources resources", "$resources منبع"))
        }.joinToString(t(", ", "، "))
    }
    McpStatus.PROBING -> t("Connecting…", "در حال اتصال…")
    McpStatus.NEEDS_AUTH -> t("Sign-in needed", "نیاز به ورود")
    McpStatus.ERROR -> t("Error", "خطا")
    McpStatus.OFF -> t("Off", "خاموش")
    McpStatus.UNKNOWN -> when (server.runtimeStatus) {
        "connected" -> t("Loaded · ${server.runtimeTools ?: 0} tools", "لود شده · ${server.runtimeTools ?: 0} ابزار")
        "failed" -> t("Failed to load", "لود نشد")
        else -> null
    }
}

@Composable
private fun statusColor(status: McpStatus): Color = when (status) {
    McpStatus.OK -> Color(0xFF22C55E)
    McpStatus.ERROR -> MaterialTheme.colorScheme.error
    McpStatus.NEEDS_AUTH -> Color(0xFFF59E0B)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ServerDialog(
    server: McpServer,
    probe: McpProbe?,
    busy: Boolean,
    onTest: () -> Unit,
    onSignIn: () -> Unit,
    onApiKey: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val status = server.statusWith(probe)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(server.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                Text(
                    server.url ?: server.command.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                statusText(server, status, probe)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = statusColor(status))
                }
                server.plugin?.let {
                    Text(
                        t("Provided by the plugin $it", "از افزونهٔ $it"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                probe?.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (probe?.ok == true && probe.tools.isNotEmpty()) {
                    Text(
                        probe.tools.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(HxSpace.xs))
                OutlinedButton(
                    onClick = onTest,
                    enabled = !busy && status != McpStatus.PROBING,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(t("Test connection", "تست اتصال")) }
                if (server.canSignIn(status) || (server.auth == "oauth" && server.plugin == null)) {
                    OutlinedButton(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Sign in", "ورود"))
                    }
                }
                if (server.plugin == null) {
                    OutlinedButton(onClick = onApiKey, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Set API key", "تنظیم کلید API"))
                    }
                    OutlinedButton(onClick = onRemove, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Remove", "حذف"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "بستن")) } },
    )
}

@Composable
private fun AddServerDialog(
    onAdd: (name: String, url: String, command: String, args: String, token: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var byUrl by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var args by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && if (byUrl) url.isNotBlank() else command.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Add MCP server", "افزودن سرور MCP")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                    FilterChip(selected = byUrl, onClick = { byUrl = true }, label = { Text(t("URL", "آدرس")) })
                    FilterChip(selected = !byUrl, onClick = { byUrl = false }, label = { Text(t("Command", "فرمان")) })
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace(" ", "_") },
                    label = { Text(t("Name", "نام")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (byUrl) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(t("Server URL", "آدرس سرور")) },
                        placeholder = { Text("https://example.com/mcp") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(t("Bearer token (optional)", "توکن Bearer (اختیاری)")) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text(t("Command", "فرمان")) },
                        placeholder = { Text("npx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = args,
                        onValueChange = { args = it },
                        label = { Text(t("Arguments", "آرگومان‌ها")) },
                        placeholder = { Text("-y @modelcontextprotocol/server-memory") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, if (byUrl) url else "", if (byUrl) "" else command, args, token) },
                enabled = valid,
            ) { Text(t("Add", "افزودن")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "انصراف")) } },
    )
}

@Composable
private fun CatalogKeysDialog(
    entry: McpCatalogEntry,
    onAdd: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember(entry.name) { mutableStateMapOf<String, String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                Text(
                    t("This server needs these keys:", "این سرور این کلیدها رو لازم داره:"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                entry.requires.forEach { key ->
                    OutlinedTextField(
                        value = values[key].orEmpty(),
                        onValueChange = { values[key] = it },
                        label = { Text(key) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(values.toMap()) }) { Text(t("Add", "افزودن")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "انصراف")) } },
    )
}

@Composable
private fun ApiKeyDialog(
    name: String,
    onSave: (value: String, envVar: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf("") }
    var envVar by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("API key for $name", "کلید API برای $name")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(t("Key", "کلید")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = envVar,
                    onValueChange = { envVar = it.uppercase().replace(' ', '_') },
                    label = { Text(t("Variable name (optional)", "نام متغیر (اختیاری)")) },
                    // Hermes' default name (mcp_config._env_key_for_server).
                    placeholder = { Text("MCP_${name.uppercase().replace(Regex("[^A-Za-z0-9_]"), "_").trim('_')}_API_KEY") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value, envVar) }, enabled = value.isNotBlank()) {
                Text(t("Save", "ذخیره"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "انصراف")) } },
    )
}

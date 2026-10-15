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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.messaging.Webhooks.Created
import com.hermes.android.domain.messaging.Webhooks.Draft
import com.hermes.android.domain.messaging.Webhooks.Route
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.icons.filled.ContentCopy
import com.hermes.android.ui.icons.filled.DeleteOutline
import com.hermes.android.ui.viewmodel.WebhooksEvent
import com.hermes.android.ui.viewmodel.WebhooksUiState
import com.hermes.android.ui.viewmodel.WebhooksViewModel
import kotlinx.coroutines.launch

/**
 * Webhook routes of the messaging gateway: the platform switch, each route's URL to POST to,
 * its on/off switch and delete, and a dialog to make one. The built-in Linux runs no messaging
 * gateway, so there the page only explains that.
 */
@Composable
fun WebhooksScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: WebhooksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // LocalResources follows the in-app language; LocalContext would use the device's.
    val resources = LocalResources.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Route?>(null) }

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    is WebhooksEvent.PlatformEnabled -> resources.getString(
                        if (event.needsRestart) R.string.webhooks_enabled_restart else R.string.webhooks_enabled_done,
                    )
                    is WebhooksEvent.Deleted -> resources.getString(R.string.webhooks_deleted, event.name)
                    is WebhooksEvent.Failed -> event.message
                },
            )
        }
    }
    val copy: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.copied)) }
    }

    HermesScaffold(
        title = stringResource(R.string.webhooks_title),
        subtitle = stringResource(R.string.webhooks_subtitle),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (viewModel.available) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
                    }
                }
            }
        },
        floatingActionButton = {
            if (viewModel.available && state.platformEnabled == true) {
                FloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.webhooks_add))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = HxSpace.screen, end = HxSpace.screen,
                top = HxSpace.sm, bottom = HxSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(HxSpace.group),
        ) {
            if (!viewModel.available) {
                item(key = "builtin") { NoteCard(stringResource(R.string.webhooks_builtin_note)) }
                return@LazyColumn
            }
            state.loadError?.let { error ->
                item(key = "error") {
                    Column(verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            if (state.platformEnabled == false) {
                item(key = "platform") { PlatformOffCard(busy = state.busy, onEnable = viewModel::enablePlatform) }
            }
            if (state.platformEnabled == true) {
                if (state.baseUrl.isNotBlank()) {
                    item(key = "base") {
                        Text(
                            stringResource(R.string.webhooks_url_note, state.baseUrl),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.routes.isEmpty() && !state.loading && state.loadError == null) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.webhooks_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Routes are listed even with the platform off: they stay saved and come back with it.
            // Prefixed: a route named "base" or "error" shared its key with the rows above, and
            // two items under one key crash the list.
            items(state.routes, key = { "route:${it.name}" }) { route ->
                RouteCard(
                    route = route,
                    busy = state.busy,
                    onToggle = { viewModel.setEnabled(route, it) },
                    onCopy = { copy(route.url) },
                    onDelete = { deleting = route },
                )
            }
        }
    }

    if (creating) {
        CreateWebhookDialog(
            state = state,
            onDismiss = { creating = false },
            // Stays open until the server made it, so a refused name doesn't lose what was typed.
            onCreate = viewModel::create,
        )
    }
    LaunchedEffect(state.created) {
        if (state.created != null) creating = false
    }
    state.created?.let { created ->
        CreatedDialog(created, onCopy = copy, onDismiss = viewModel::dismissCreated)
    }
    deleting?.let { route ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.webhooks_delete_title, route.name)) },
            text = { Text(stringResource(R.string.webhooks_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.delete(route)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NoteCard(text: String) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(HxSpace.inner),
        )
    }
}

@Composable
private fun PlatformOffCard(busy: Boolean, onEnable: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(HxSpace.inner),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            Text(
                stringResource(R.string.webhooks_platform_off_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.webhooks_platform_off_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onEnable,
                enabled = !busy,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.webhooks_enable)) }
        }
    }
}

@Composable
private fun RouteCard(
    route: Route,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(HxSpace.inner),
            verticalArrangement = Arrangement.spacedBy(HxSpace.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(route.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (route.description.isNotBlank()) {
                        Text(
                            route.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!route.enabled) {
                    StatusChip(
                        label = stringResource(R.string.webhooks_off),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = route.enabled, onCheckedChange = onToggle, enabled = !busy)
            }
            Text(
                stringResource(
                    R.string.webhooks_events_label,
                    route.events.joinToString(", ").ifEmpty { stringResource(R.string.webhooks_events_all) },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (route.deliverOnly) {
                    stringResource(R.string.webhooks_deliver_only_label, route.deliver)
                } else {
                    stringResource(R.string.webhooks_deliver_label, route.deliver)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (route.url.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            route.url,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.webhooks_copy_url))
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onDelete, enabled = !busy) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = HxSpace.xs),
                    )
                }
            }
        }
    }
}

/** The server's own rule: lowercase, spaces become dashes, then `^[a-z0-9][a-z0-9_-]*$`. */
private fun normalizedName(raw: String): String = raw.trim().lowercase().replace(" ", "-")

private val NAME_RULE = Regex("^[a-z0-9][a-z0-9_-]*$")

@Composable
private fun CreateWebhookDialog(
    state: WebhooksUiState,
    onDismiss: () -> Unit,
    onCreate: (Draft) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var events by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var deliver by rememberSaveable { mutableStateOf("log") }
    var chatId by rememberSaveable { mutableStateOf("") }
    var deliverOnly by rememberSaveable { mutableStateOf(false) }

    val cleanName = normalizedName(name)
    val nameOk = NAME_RULE.matches(cleanName)
    // Direct delivery needs a real target; the server refuses it with `log`.
    val realTarget = deliver.trim().let { it.isNotEmpty() && it != "log" }
    val canCreate = nameOk && !state.busy && (!deliverOnly || realTarget)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.webhooks_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.webhooks_field_name)) },
                    supportingText = { Text(stringResource(R.string.webhooks_field_name_hint)) },
                    isError = name.isNotBlank() && !nameOk,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.webhooks_field_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = events,
                    onValueChange = { events = it },
                    label = { Text(stringResource(R.string.webhooks_field_events)) },
                    supportingText = { Text(stringResource(R.string.webhooks_field_events_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.webhooks_field_prompt)) },
                    supportingText = { Text(stringResource(R.string.webhooks_field_prompt_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = deliver,
                    onValueChange = { deliver = it },
                    label = { Text(stringResource(R.string.webhooks_field_deliver)) },
                    placeholder = { Text(stringResource(R.string.webhooks_field_deliver_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (realTarget) {
                    OutlinedTextField(
                        value = chatId,
                        onValueChange = { chatId = it },
                        label = { Text(stringResource(R.string.webhooks_field_chat_id)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deliverOnly, onCheckedChange = { deliverOnly = it })
                        Text(stringResource(R.string.webhooks_field_deliver_only), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        Draft(
                            name = cleanName,
                            description = description,
                            events = events.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                            prompt = prompt,
                            deliver = deliver.trim().ifEmpty { "log" },
                            deliverOnly = deliverOnly && realTarget,
                            deliverChatId = if (realTarget) chatId else "",
                        ),
                    )
                },
                enabled = canCreate,
            ) { Text(stringResource(R.string.webhooks_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Shows the new route's URL and its secret, which the server never returns again. */
@Composable
private fun CreatedDialog(created: Created, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        // Only the button closes it, so the secret isn't lost to a stray tap outside.
        onDismissRequest = {},
        title = { Text(stringResource(R.string.webhooks_created_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                Text(stringResource(R.string.webhooks_created_body), style = MaterialTheme.typography.bodySmall)
                CopyField(label = "URL", value = created.route.url, copyLabel = stringResource(R.string.webhooks_copy_url), onCopy = onCopy)
                if (created.secret.isNotBlank()) {
                    CopyField(
                        label = stringResource(R.string.webhooks_secret),
                        value = created.secret,
                        copyLabel = stringResource(R.string.webhooks_copy_secret),
                        onCopy = onCopy,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun CopyField(label: String, value: String, copyLabel: String, onCopy: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
            IconButton(onClick = { onCopy(value) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = copyLabel)
            }
        }
    }
}

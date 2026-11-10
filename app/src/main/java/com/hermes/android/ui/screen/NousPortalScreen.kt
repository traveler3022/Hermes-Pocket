package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.portal.Connector
import com.hermes.android.domain.portal.ConnectorAccount
import com.hermes.android.domain.portal.PortalStatus
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.viewmodel.NousPortalViewModel

/**
 * The Nous Portal account Hermes is signed in to: its plan and what the Portal serves, and the
 * apps (connectors) the account has connected. Signing in itself is in Settings › Providers.
 */
@Composable
fun NousPortalScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: NousPortalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NousPortalViewModel.Event.OpenUrl -> runCatching { uriHandler.openUri(event.url) }
                is NousPortalViewModel.Event.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    HermesScaffold(
        title = stringResource(R.string.portal_title),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = viewModel::load, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = HxSpace.screen, vertical = HxSpace.sm),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            if (state.loading) item(key = "loading") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            item(key = "status") {
                PortalStatusCard(state.status, state.statusError, state.loading) { url -> runCatching { uriHandler.openUri(url) } }
            }
            item(key = "connectors-title") {
                Text(
                    text = stringResource(R.string.portal_connectors),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = HxSpace.sm),
                )
            }
            when {
                !state.connectorsAvailable -> item(key = "connectors-off") {
                    PortalNote(stringResource(R.string.portal_connectors_off))
                }
                state.connectorsError != null -> item(key = "connectors-error") {
                    PortalNote(state.connectorsError.orEmpty())
                }
                state.connectors.isEmpty() && !state.loading -> item(key = "connectors-empty") {
                    PortalNote(stringResource(R.string.portal_connectors_empty))
                }
                else -> items(state.connectors, key = { "c-" + it.slug }) { connector ->
                    ConnectorCard(
                        connector = connector,
                        accounts = state.accounts.filter { it.connector == connector.slug },
                        connecting = state.connecting == connector.slug,
                        busy = state.connecting != null || state.removing != null,
                        removing = state.removing,
                        onConnect = { viewModel.connect(connector) },
                        onCancel = viewModel::cancelConnect,
                        onRemove = viewModel::removeAccount,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalStatusCard(status: PortalStatus?, error: String?, loading: Boolean, onOpen: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.portal_account), style = MaterialTheme.typography.titleMedium)
            when {
                status == null && loading -> Unit
                status == null -> Text(
                    text = error ?: stringResource(R.string.portal_status_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    val account = when {
                        !status.loggedIn -> stringResource(R.string.portal_signed_out)
                        status.freeTier -> stringResource(R.string.portal_free_tier)
                        else -> status.accountTier?.let { stringResource(R.string.portal_signed_in_tier, it) }
                            ?: stringResource(R.string.portal_signed_in)
                    }
                    Text(account, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (!status.loggedIn) {
                        Text(
                            text = stringResource(R.string.portal_sign_in_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (status.provider.isNotBlank()) {
                        PortalLine(stringResource(R.string.portal_model_provider), status.provider)
                    }
                    status.features.forEach { PortalLine(it.label, it.state) }
                    val manage = status.subscriptionUrl
                    if (status.loggedIn && manage != null) {
                        OutlinedButton(onClick = { onOpen(manage) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.portal_manage_subscription))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PortalNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = HxSpace.sm),
    )
}

@Composable
private fun ConnectorCard(
    connector: Connector,
    accounts: List<ConnectorAccount>,
    connecting: Boolean,
    busy: Boolean,
    removing: String?,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onRemove: (ConnectorAccount) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(connector.name, style = MaterialTheme.typography.titleSmall)
                    val state = when {
                        !connector.enabled -> stringResource(R.string.portal_connector_disabled)
                        connector.connected -> stringResource(R.string.portal_connector_connected)
                        connector.status != null -> connector.status
                        else -> stringResource(R.string.portal_connector_not_connected)
                    }
                    Text(
                        text = state,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (connector.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    connecting -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    }
                    connector.enabled -> TextButton(onClick = onConnect, enabled = !busy) {
                        Text(stringResource(if (connector.connected) R.string.portal_connector_add_account else R.string.connect))
                    }
                }
            }
            if (connector.description.isNotBlank()) {
                Text(
                    text = connector.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (connecting) {
                Text(
                    text = stringResource(R.string.portal_connector_waiting),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            accounts.forEach { account ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = account.alias ?: account.label.ifBlank { account.connectionId },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (removing == account.connectionId) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { onRemove(account) }, enabled = !busy) {
                            Text(stringResource(R.string.portal_connector_disconnect))
                        }
                    }
                }
            }
        }
    }
}

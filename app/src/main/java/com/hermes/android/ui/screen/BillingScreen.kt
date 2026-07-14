package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.BillingViewModel

/**
 * Read-only billing state + auto-reload toggle (billing.state,
 * billing.auto_reload). Charging real money stays desktop/CLI-only — see
 * BillingRepository kdoc. Depends ONLY on [BillingViewModel].
 */
@Composable
fun BillingScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BillingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAutoReloadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    HermesScaffold(
        title = t("Billing", "صورتحساب"),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        val state = uiState.state
        when {
            uiState.isLoading && state == null -> Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            state == null || !state.loggedIn -> Column(modifier = Modifier.padding(padding)) {
                HermesEmptyState(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = t("Not signed in to billing", "به صورتحساب وارد نشدی"),
                    caption = t(
                        "Sign in from the server (hermes login) to see balance and auto-reload",
                        "از سرور وارد شو (hermes login) تا موجودی و شارژ خودکار رو ببینی",
                    ),
                )
            }
            else -> Column(
                modifier = Modifier.padding(padding).fillMaxWidth().padding(HxSpace.screen),
                verticalArrangement = Arrangement.spacedBy(HxSpace.md),
            ) {
                Surface(
                    shape = RoundedCornerShape(HxRadius.md),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(HxSpace.md)) {
                        Text(
                            state.balanceDisplay ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            state.orgName ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(HxRadius.md),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showAutoReloadDialog = true },
                ) {
                    Row(
                        modifier = Modifier.padding(HxSpace.md).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t("Auto-reload", "شارژ خودکار"), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                state.autoReload?.let {
                                    if (it.enabled) {
                                        t(
                                            "On — below ${it.thresholdDisplay}, top up to ${it.reloadToDisplay}",
                                            "روشن — زیر ${it.thresholdDisplay}، شارژ تا ${it.reloadToDisplay}",
                                        )
                                    } else {
                                        t("Off", "خاموش")
                                    }
                                } ?: t("Off", "خاموش"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.autoReload?.enabled ?: false,
                            onCheckedChange = { showAutoReloadDialog = true },
                        )
                    }
                }
            }
        }

        if (showAutoReloadDialog && state != null) {
            AutoReloadDialog(
                current = state.autoReload,
                isSaving = uiState.isSaving,
                onDismiss = { showAutoReloadDialog = false },
                onSave = { enabled, threshold, reloadTo ->
                    viewModel.setAutoReloadEnabled(enabled, threshold, reloadTo)
                    showAutoReloadDialog = false
                },
            )
        }
    }
}

@Composable
private fun AutoReloadDialog(
    current: com.hermes.android.data.BillingRepository.AutoReload?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, threshold: String, reloadTo: String) -> Unit,
) {
    var enabled by remember { mutableStateOf(current?.enabled ?: false) }
    var threshold by remember { mutableStateOf("10") }
    var reloadTo by remember { mutableStateOf("25") }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(t("Auto-reload", "شارژ خودکار")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Enabled", "فعال"), modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text(t("Reload when balance drops below (USD)", "شارژ وقتی موجودی زیر این رفت (دلار)")) },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reloadTo,
                    onValueChange = { reloadTo = it },
                    label = { Text(t("Top up to (USD)", "شارژ تا (دلار)")) },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(enabled, threshold, reloadTo) },
                enabled = !isSaving,
            ) { Text(if (isSaving) t("Saving…", "در حال ذخیره…") else t("Save", "ذخیره")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text(t("Cancel", "انصراف")) }
        },
    )
}

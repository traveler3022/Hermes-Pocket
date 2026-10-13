package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.viewmodel.PlatformConfig
import com.hermes.android.ui.viewmodel.PlatformType
import com.hermes.android.ui.viewmodel.PairingEvent
import com.hermes.android.ui.viewmodel.PairingViewModel
import com.hermes.android.ui.viewmodel.PlatformsViewModel
import com.hermes.android.ui.viewmodel.TelegramSetupViewModel
import com.hermes.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources

/**
 * Messaging Platforms — configure Telegram/Discord/Slack bot tokens.
 * Rebuilt on the design system: neutral tonal cards with a clear
 * connected/not-connected status chip instead of a full-card color flip,
 * and bilingual labels (was hardcoded English).
 *
 * Depends ONLY on [PlatformsViewModel].
 */
@Composable
fun PlatformsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: PlatformsViewModel = hiltViewModel(),
    telegramSetup: TelegramSetupViewModel = hiltViewModel(),
    pairing: PairingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val telegramState by telegramSetup.uiState.collectAsStateWithLifecycle()
    TelegramSetupDialog(telegramState, telegramSetup)
    val pairingState by pairing.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // LocalResources follows the in-app language; LocalContext would use the device's.
    val resources = LocalResources.current

    LaunchedEffect(pairing, resources) {
        pairing.events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    is PairingEvent.Approved -> resources.getString(R.string.pairing_approved_done, event.who)
                    is PairingEvent.Revoked -> resources.getString(R.string.pairing_revoked_done, event.who)
                    PairingEvent.Cleared -> resources.getString(R.string.pairing_cleared)
                    is PairingEvent.Failed -> event.message
                },
            )
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    HermesScaffold(
        title = stringResource(R.string.messaging_platforms),
        subtitle = stringResource(R.string.talk_to_your_agent_from_telegram_discord),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(HxSpace.sm))
                Text(
                    stringResource(R.string.loading_platforms),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
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
                items(uiState.platforms, key = { it.type.name }) { platform ->
                    PlatformCard(
                        platform,
                        viewModel,
                        // The wizard needs the messaging gateway, which the built-in Linux doesn't run.
                        onWizard = if (platform.type == PlatformType.TELEGRAM && telegramSetup.available) {
                            { telegramSetup.start() }
                        } else {
                            null
                        },
                    )
                }
                // Requests come from the messaging gateway, which the built-in Linux doesn't run.
                if (pairing.available) {
                    item(key = "pairing") { PairingSection(pairingState, pairing) }
                }
            }
        }
    }
}

@Composable
private fun PlatformCard(
    platform: PlatformConfig,
    viewModel: PlatformsViewModel,
    onWizard: (() -> Unit)? = null,
) {
    var token by remember(platform.type) { mutableStateOf(platform.botToken) }

    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(HxSpace.inner),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platform.type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = platform.type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(
                    label = if (platform.isConnected) stringResource(R.string.connected_8) else stringResource(R.string.off_2),
                    color = if (platform.isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            onWizard?.let { start ->
                Button(
                    onClick = start,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.connect_your_bot))
                }
            }

            // The wizard replaces the old token field, which never reached the server.
            if (onWizard == null) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.bot_token)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(HxRadius.sm),
                )

                Button(
                    onClick = { viewModel.saveToken(platform.type, token) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.save_token))
                }
            }
        }
    }
}

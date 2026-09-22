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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatusChip
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.PlatformConfig
import com.hermes.android.ui.viewmodel.PlatformsViewModel

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
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    HermesScaffold(
        title = t("Messaging Platforms", "پلتفرم‌های پیام‌رسان"),
        subtitle = t(
            "Talk to your agent from Telegram, Discord, Slack",
            "با ایجنت از تلگرام، دیسکورد و اسلک حرف بزن",
        ),
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
                    t("Loading platforms…", "در حال بارگذاری پلتفرم‌ها…"),
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
                    PlatformCard(platform, viewModel)
                }
            }
        }
    }
}

@Composable
private fun PlatformCard(
    platform: PlatformConfig,
    viewModel: PlatformsViewModel,
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
                    label = if (platform.isConnected) t("connected", "متصل") else t("off", "خاموش"),
                    color = if (platform.isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(t("Bot Token", "توکن بات")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(HxRadius.sm),
            )

            Button(
                onClick = { viewModel.saveToken(platform.type, token) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(t("Save Token", "ذخیره توکن"))
            }
        }
    }
}

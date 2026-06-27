package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.viewmodel.ConfigTab
import com.hermes.android.ui.viewmodel.ConfigViewModel
import com.hermes.android.ui.viewmodel.ModelOption
import com.hermes.android.ui.viewmodel.ToolOption
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.hermes.android.ui.i18n.t

/**
 * Configuration screen — model picker, tool toggles, config viewer.
 *
 * Depends ONLY on [ConfigViewModel] — never on gateway or runtime packages.
 *
 * Reference: Phase 1.5 Rule 1 (Strict Layer Dependency)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPlatforms: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    onNavigateToCron: () -> Unit = {},
    onNavigateToRuntime: () -> Unit = {},
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Settings", "تنظیمات")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                ConfigTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (uiState.selectedTab) {
                ConfigTab.GENERAL -> GeneralTab(
                    state = uiState,
                    viewModel = viewModel,
                    onNavigateToPlatforms = onNavigateToPlatforms,
                    onNavigateToSkills = onNavigateToSkills,
                    onNavigateToCron = onNavigateToCron,
                    onNavigateToRuntime = onNavigateToRuntime,
                )
                ConfigTab.MODELS -> ModelsTab(uiState, viewModel)
                ConfigTab.TOOLS -> ToolsTab(uiState, viewModel)
            }
        }
    }
}

@Composable
private fun GeneralTab(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
    onNavigateToPlatforms: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    onNavigateToCron: () -> Unit = {},
    onNavigateToRuntime: () -> Unit = {},
) {
    if (state.isLoadingConfig) {
        LoadingIndicator("Loading config…")
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Backend & Capabilities",
            style = MaterialTheme.typography.titleMedium,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Active backend",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Provider: ${state.activeProvider ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Model: ${state.activeModel ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        QuickBackendSetup(viewModel)


        // Link to Runtime Setup / Termux Connection
        androidx.compose.material3.Button(
            onClick = onNavigateToRuntime,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Termux & Agent Connection", "اتصال ترموکس و عامل"))
        }

        // Link to Messaging Platforms
        androidx.compose.material3.OutlinedButton(
            onClick = onNavigateToPlatforms,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Messaging Platforms", "پیام‌رسان‌ها"))
        }

        // Link to Skills
        androidx.compose.material3.OutlinedButton(
            onClick = onNavigateToSkills,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Skills Browser", "مهارت‌ها"))
        }

        // Link to Cron Jobs
        androidx.compose.material3.OutlinedButton(
            onClick = onNavigateToCron,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("Cron Scheduler", "زمان‌بندی"))
        }

        Text(
            text = t("Current Configuration", "پیکربندی فعلی"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = state.configYaml.ifEmpty { "(empty)" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun QuickBackendSetup(viewModel: ConfigViewModel) {
    var mimoKey by remember { mutableStateOf("") }
    var mimoBaseUrl by remember { mutableStateOf("https://api.xiaomimimo.com/v1") }
    var mimoModel by remember { mutableStateOf("mimo-v2.5-free") }
    var geminiKey by remember { mutableStateOf("") }
    var geminiModel by remember { mutableStateOf("gemini-2.5-flash") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = t("Add / switch API backend", "افزودن / تغییر بک‌اند API"),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = mimoKey,
                onValueChange = { mimoKey = it },
                label = { Text(t("MiMo API key", "کلید MiMo")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = mimoBaseUrl,
                onValueChange = { mimoBaseUrl = it },
                label = { Text(t("MiMo base URL", "آدرس API میمو")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = mimoModel,
                onValueChange = { mimoModel = it },
                label = { Text(t("MiMo model", "مدل میمو")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            androidx.compose.material3.Button(
                onClick = { viewModel.configureXiaomiBackend(mimoKey, mimoBaseUrl, mimoModel) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(t("Save MiMo backend", "ذخیره بک‌اند MiMo"))
            }

            androidx.compose.material3.HorizontalDivider()

            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it },
                label = { Text(t("Gemini API key", "کلید Gemini")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = geminiModel,
                onValueChange = { geminiModel = it },
                label = { Text(t("Gemini model", "مدل Gemini")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            androidx.compose.material3.OutlinedButton(
                onClick = { viewModel.configureGeminiBackend(geminiKey, geminiModel) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(t("Save Gemini backend", "ذخیره بک‌اند Gemini"))
            }
        }
    }
}

@Composable
private fun ModelsTab(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    if (state.isLoadingModels) {
        LoadingIndicator("Loading models…")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.availableModels, key = { "${it.provider}/${it.modelId}" }) { model ->
            ModelCard(
                model = model,
                viewModel = viewModel,
                isActive = model.provider == state.activeProvider && model.modelId == state.activeModel,
            )
        }
    }
}

@Composable
private fun ModelCard(model: ModelOption, viewModel: ConfigViewModel, isActive: Boolean) {
    var apiKey by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "${model.provider} / ${model.modelId}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (isActive) {
                Text(
                    text = "Active backend",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.selectModel(model) },
                ) {
                    Text("Use this model")
                }
            }
            if (model.requiresApiKey) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.saveApiKey(model.provider, apiKey) },
                ) {
                    Text("Save Key")
                }
            }
        }
    }
}

@Composable
private fun ToolsTab(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    if (state.isLoadingTools) {
        LoadingIndicator("Loading tools…")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.availableTools, key = { it.name }) { tool ->
            ToolRow(tool, viewModel)
        }
    }
}

@Composable
private fun ToolRow(tool: ToolOption, viewModel: ConfigViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (tool.description.isNotBlank()) {
                    Text(
                        text = tool.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${tool.toolCount} tools" + if (tool.tools.isNotEmpty()) ": ${tool.tools.take(6).joinToString(", ")}${if (tool.tools.size > 6) "…" else ""}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                tool.toolset?.let {
                    Text(
                        text = "toolset: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Switch(
                checked = tool.enabled,
                onCheckedChange = { viewModel.toggleTool(tool.name, it) },
            )
        }
    }
}

@Composable
private fun LoadingIndicator(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

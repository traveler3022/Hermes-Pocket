package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import com.hermes.android.ui.icons.filled.Dns
import com.hermes.android.ui.icons.filled.ExpandLess
import com.hermes.android.ui.icons.filled.ExpandMore
import com.hermes.android.ui.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.hermes.android.ui.icons.filled.Language
import com.hermes.android.ui.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import com.hermes.android.ui.icons.filled.Psychology
import com.hermes.android.ui.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import com.hermes.android.ui.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.i18n.AppLanguage
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.theme.ColorTheme
import com.hermes.android.ui.theme.ThemeMode
import com.hermes.android.ui.theme.ThemeModeState
import com.hermes.android.ui.viewmodel.ConfigUiState
import com.hermes.android.ui.viewmodel.ConfigViewModel
import com.hermes.android.ui.viewmodel.CredentialEntry
import com.hermes.android.ui.viewmodel.HermesProviderConfig
import com.hermes.android.ui.viewmodel.ModelOption
import com.hermes.android.ui.viewmodel.ToolOption

@Composable
internal fun MemorySection(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
    onOpenLearning: () -> Unit = {},
) {
    if (state.isLoadingMemory) {
        LoadingIndicator(stringResource(R.string.loading_memory))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(com.hermes.android.ui.design.SettingsPagePadding),
        verticalArrangement = Arrangement.spacedBy(com.hermes.android.ui.design.HxSpace.group),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsLabel(stringResource(R.string.memory_files))
            TextButton(onClick = { viewModel.loadMemory() }) {
                Text(stringResource(R.string.refresh_2))
            }
        }
        LearningEntryCard(onOpenLearning)
        MemoryFileCard(
            icon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = "USER.md",
            content = state.memoryUserMd,
            containerColor = com.hermes.android.ui.design.settingsCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        MemoryFileCard(
            icon = { Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = "MEMORY.md",
            content = state.memoryMd,
            containerColor = com.hermes.android.ui.design.settingsCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        MemoryProvidersPanel(onBuiltInReset = { viewModel.loadMemory() })
    }
}

@Composable
internal fun MemoryFileCard(
    icon: @Composable () -> Unit,
    title: String,
    content: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val displayText = content
        .ifBlank { stringResource(R.string.memory_has_not_been_created_yet) }
        .replace("(not found)", stringResource(R.string.memory_has_not_been_created_yet))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                icon()
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = contentColor,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}


/** Opens the learning graph: the skills the agent made or used and what it remembered, over time. */
@Composable
private fun LearningEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = com.hermes.android.ui.design.settingsCardColor()),
    ) {
        Row(
            modifier = Modifier.padding(com.hermes.android.ui.design.HxSpace.inner),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.learning_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.learning_open_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package com.hermes.android.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.GroupDivider
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.SectionHeader
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.LinuxToolsUiState
import com.hermes.android.ui.viewmodel.LinuxToolsViewModel

/** The built-in Alpine's own tools, as in Aether's Alpine settings: terminal, files, packages. */
@Composable
fun LinuxToolsScreen(
    onNavigateBack: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenDesktop: () -> Unit,
    viewModel: LinuxToolsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // The package list is a page of its own inside this screen; back returns here.
    var showPackages by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showPackages) { showPackages = false }
    val desktopNotInstalled = t(
        "Install “Browser & desktop (VNC)” from Packages first.",
        "اول بستهٔ «مرورگر و دسکتاپ (VNC)» را از «بسته‌ها» نصب کنید.",
    )
    val filesUnavailable = t(
        "No Files app found. Open your file manager and pick “Hermes”.",
        "برنامهٔ Files پیدا نشد. فایل‌منیجر گوشی را باز کنید و «Hermes» را انتخاب کنید.",
    )

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    HermesScaffold(
        title = if (showPackages) t("Packages", "بسته‌ها") else t("Linux", "لینوکس"),
        onBack = { if (showPackages) showPackages = false else onNavigateBack() },
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = viewModel::refresh, enabled = !state.checking) {
                Icon(Icons.Default.Refresh, contentDescription = t("Refresh", "بازخوانی"))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            if (!state.rootfsInstalled) {
                Text(
                    text = t(
                        "The built-in Linux runtime is not installed yet. Install it from the setup wizard.",
                        "محیط لینوکس داخلی هنوز نصب نشده است. آن را از «راه‌اندازی اولیه» نصب کنید.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
                return@Column
            }
            if (showPackages) {
                PackagesPage(state, viewModel)
                return@Column
            }

            Spacer(Modifier.height(12.dp))
            SettingsGroup {
                SettingRow(
                    title = t("Terminal", "ترمینال"),
                    subtitle = t("Shell inside the built-in Linux", "شل داخل لینوکس داخلی"),
                    icon = Icons.Default.Terminal,
                    onClick = onOpenTerminal,
                )
                GroupDivider()
                SettingRow(
                    title = t("Files", "فایل‌ها"),
                    subtitle = t("Browse Linux files in Android's Files app", "مرور فایل‌های لینوکس در برنامهٔ Files اندروید"),
                    icon = Icons.Default.Folder,
                    onClick = { if (!openLinuxFiles(context)) viewModel.showMessage(filesUnavailable) },
                )
                GroupDivider()
                SettingRow(
                    title = t("Browser & desktop", "مرورگر و دسکتاپ"),
                    subtitle = t(
                        "The Chromium the agent browses with, on a VNC screen",
                        "همان Chromium که عامل با آن کار می‌کند، روی یک صفحهٔ VNC",
                    ),
                    icon = Icons.Default.DesktopWindows,
                    onClick = {
                        if (state.installed[LinuxToolsViewModel.DesktopProfileId] == false) {
                            viewModel.showMessage(desktopNotInstalled)
                        } else {
                            onOpenDesktop()
                        }
                    },
                )
                GroupDivider()
                SettingRow(
                    title = t("Packages", "بسته‌ها"),
                    subtitle = t(
                        "${state.installed.count { it.value == true }} of ${viewModel.profiles.size} installed",
                        "${state.installed.count { it.value == true }} از ${viewModel.profiles.size} نصب شده",
                    ),
                    icon = Icons.Default.Inventory2,
                    onClick = { showPackages = true },
                )
            }
        }
    }
}

@Composable
private fun PackagesPage(state: LinuxToolsUiState, viewModel: LinuxToolsViewModel) {
    Spacer(Modifier.height(12.dp))
    SettingsGroup {
        viewModel.profiles.forEachIndexed { index, profile ->
            if (index > 0) GroupDivider()
            val installed = state.installed[profile.id]
            SettingRow(
                title = t(profile.titleEn, profile.titleFa),
                subtitle = profile.packages.joinToString(" "),
                icon = Icons.Default.Inventory2,
                trailing = {
                    when {
                        state.installing == profile.id || (installed == null && state.checking) ->
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        installed == true -> Text(
                            t("Installed", "نصب شده"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        else -> TextButton(
                            onClick = { viewModel.install(profile) },
                            enabled = state.installing == null,
                        ) { Text(t("Install", "نصب")) }
                    }
                },
            )
        }
    }

    if (state.log.isNotEmpty()) {
        SectionHeader(t("Install output", "خروجی نصب"))
        SettingsGroup {
            Text(
                text = state.log.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
    }
}

package com.hermes.android.ui.screen

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.runtime.linux.LinuxDocumentStore
import com.hermes.android.runtime.linux.LinuxDocumentsProvider
import com.hermes.android.ui.design.GroupDivider
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.SectionHeader
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.i18n.t
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val desktopNotInstalled = t(
        "Install “Browser & desktop (VNC)” below first.",
        "اول بستهٔ «مرورگر و دسکتاپ (VNC)» را از پایین همین صفحه نصب کنید.",
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
        title = t("Linux", "لینوکس"),
        onBack = onNavigateBack,
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
                    onClick = { if (!openFiles(context)) viewModel.showMessage(filesUnavailable) },
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
            }

            SectionHeader(t("Packages", "بسته‌ها"))
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
    }
}

/** Opens DocumentsUI on our provider's root; false when no app handles it. */
private fun openFiles(context: Context): Boolean {
    val authority = LinuxDocumentsProvider.authority(context)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(DocumentsContract.buildRootUri(authority, LinuxDocumentStore.RootId), DocumentsContract.Root.MIME_TYPE_ITEM)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

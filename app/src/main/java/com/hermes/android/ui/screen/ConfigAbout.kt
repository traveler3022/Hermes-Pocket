package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.updates.AppRelease
import com.hermes.android.domain.updates.AppUpdates
import com.hermes.android.domain.updates.DownloadProblem
import com.hermes.android.domain.updates.UpdateCheck
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.SectionHeader
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.viewmodel.AboutUiState
import com.hermes.android.ui.viewmodel.AppDownload
import com.hermes.android.ui.viewmodel.AboutViewModel
import com.hermes.android.ui.viewmodel.HermesUpdate

/**
 * What this build is, and whether a newer one has been published.
 *
 * The app is installed by hand from a GitHub release, so nothing tells the user
 * a new build exists — they would have to think to go and look. This asks for
 * them, and shows the release notes so the answer to "should I bother" is on
 * the same screen as the question.
 */
@Composable
internal fun AboutSection(viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    // The installer and the "install unknown apps" switch are system screens; a phone
    // without one must not crash, it falls back to the browser link.
    LaunchedEffect(viewModel) {
        viewModel.launches.collect { intent ->
            runCatching { context.startActivity(intent) }
                .onFailure {
                    timber.log.Timber.w(it, "[Update] could not open ${intent.action}")
                    viewModel.onLaunchFailed()
                }
        }
    }
    // Back from "install unknown apps": install if the user allowed it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = HxSpace.md),
        verticalArrangement = Arrangement.spacedBy(HxSpace.md),
    ) {
        // Two things update here, and they are easy to mistake for one: the app
        // (screens, installed from a GitHub release) and Hermes Agent (the agent
        // itself, inside the built-in Linux). Each gets its own name, version and
        // one line saying what it is, so neither button looks like the other.
        SectionHeader(stringResource(R.string.app_hermes_for_android))
        SettingsGroup {
            SettingRow(
                title = stringResource(R.string.app_version),
                subtitle = state.installedVersion,
                icon = HxIcons.Sparkles,
            )
            SettingRow(
                title = stringResource(R.string.source_code),
                subtitle = stringResource(R.string.open_the_project_on_github),
                icon = HxIcons.ExternalLink,
                onClick = { uriHandler.openUri(AppUpdates.PROJECT_URL) },
            )
        }

        SettingsGroup {
            Column(
                modifier = Modifier.padding(HxSpace.inner),
                verticalArrangement = Arrangement.spacedBy(HxSpace.md),
            ) {
                Text(
                    text = stringResource(R.string.hermes_pocket_is_installed_by_hand_from),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = viewModel::check,
                    enabled = !state.isChecking,
                ) {
                    if (state.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.check_for_app_updates))
                    }
                }

                when (val result = state.result) {
                    null -> Unit

                    is UpdateCheck.UpToDate -> UpdateNote(
                        text = stringResource(R.string.you_are_on_the_newest_release),
                    )

                    // A failed check says so rather than claiming the app is up
                    // to date — silence would be the same shape as good news.
                    is UpdateCheck.Failed -> UpdateNote(
                        text = stringResource(R.string.could_not_check_right_now, result.reason),
                        isProblem = true,
                    )

                    is UpdateCheck.Available -> AvailableRelease(
                        release = result.release,
                        installed = state.installedVersion,
                        download = state.appDownload,
                        isDebugBuild = state.isDebugBuild,
                        installedCommit = state.installedCommit,
                        onDownload = viewModel::downloadUpdate,
                        onCancel = viewModel::cancelDownload,
                        onInstall = viewModel::install,
                        onOpen = { uriHandler.openUri(result.release.pageUrl) },
                    )
                }
            }
        }

        HermesAgentSection(
            state = state,
            onUpdate = viewModel::updateHermes,
        )
    }
}

@Composable
private fun UpdateNote(text: String, isProblem: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isProblem) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun AvailableRelease(
    release: AppRelease,
    installed: String,
    download: AppDownload,
    isDebugBuild: Boolean,
    installedCommit: String,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(HxRadius.sm))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    // A test build has no version of its own; commits tell two apart.
                    text = release.testBuild?.let { "${installedCommit.ifBlank { "?" }} → ${it.commit}" }
                        ?: "$installed → ${release.versionName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            release.publishedAt?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val testBuild = release.testBuild
        Text(
            text = if (testBuild != null) {
                stringResource(
                    R.string.app_update_new_test_build,
                    testBuild.branch ?: "?",
                    testBuild.built ?: "?",
                )
            } else {
                release.title
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (testBuild != null && installedCommit.isBlank()) {
            UpdateNote(text = stringResource(R.string.app_update_commit_unknown))
        }

        // Release notes are markdown on GitHub, and the app already renders
        // markdown — showing them raw would be the one place it did not.
        if (release.notes.isNotBlank() && testBuild == null) {
            HermesMarkdown(
                markdown = release.notes,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }

        // In-app download when the release carries an APK; the release page stays as
        // the fallback for everything this cannot do.
        if (release.apk != null) {
            AppDownloadControls(
                download = download,
                onDownload = onDownload,
                onCancel = onCancel,
                onInstall = onInstall,
            )
        }

        val openRelease: @Composable () -> Unit = {
            Icon(
                HxIcons.ExternalLink,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.open_the_release),
                modifier = Modifier.padding(start = HxSpace.sm),
            )
        }
        if (release.apk != null) {
            OutlinedButton(onClick = onOpen) { openRelease() }
        } else {
            Button(onClick = onOpen) { openRelease() }
        }
        // Secondary: a debug build only follows test builds; the versioned releases are
        // another package and would install beside it.
        if (isDebugBuild) {
            Text(
                text = stringResource(R.string.app_update_debug_build_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppDownloadControls(
    download: AppDownload,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
) {
    when (download) {
        AppDownload.Idle -> Button(onClick = onDownload) {
            Text(stringResource(R.string.app_update_download_and_install))
        }

        is AppDownload.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
            val percent = download.percent
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                UpdateNote(text = stringResource(R.string.app_update_downloading_percent, percent))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                UpdateNote(text = stringResource(R.string.app_update_downloading))
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel_2)) }
        }

        is AppDownload.Ready -> Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
            UpdateNote(
                text = stringResource(
                    if (download.needsPermission) {
                        R.string.app_update_allow_installs
                    } else {
                        R.string.app_update_downloaded
                    },
                ),
                isProblem = download.needsPermission,
            )
            Button(onClick = onInstall) { Text(stringResource(R.string.app_update_install)) }
        }

        is AppDownload.Failed -> Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
            UpdateNote(
                text = stringResource(
                    when (download.problem) {
                        DownloadProblem.Network -> R.string.app_update_failed_network
                        DownloadProblem.NotAnApk -> R.string.app_update_failed_not_apk
                        DownloadProblem.WrongApp -> R.string.app_update_failed_wrong_app
                        DownloadProblem.Storage -> R.string.app_update_failed_storage
                    },
                ),
                isProblem = true,
            )
            Button(onClick = onDownload) { Text(stringResource(R.string.app_update_try_again)) }
        }

        AppDownload.InstallerUnavailable -> UpdateNote(
            text = stringResource(R.string.app_update_installer_unavailable),
            isProblem = true,
        )
    }
}

/** Hermes Agent itself: its version, and an in-place update (built-in Linux, or the server through its dashboard). */
@Composable
private fun HermesAgentSection(state: AboutUiState, onUpdate: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.hermes_agent_the_core))
    SettingsGroup {
        SettingRow(
            title = stringResource(R.string.core_version),
            subtitle = state.hermesVersion ?: "—",
            icon = HxIcons.Terminal,
        )
        Column(
            modifier = Modifier.padding(HxSpace.inner),
            verticalArrangement = Arrangement.spacedBy(HxSpace.md),
        ) {
            Text(
                text = stringResource(R.string.the_agent_that_writes_the_replies_and),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.canUpdateHermes) {
                UpdateNote(
                    text = stringResource(R.string.hermes_runs_outside_the_app_here_termux),
                )
                return@Column
            }
            val update = state.hermesUpdate
            Button(
                onClick = { confirming = true },
                enabled = update !is HermesUpdate.Running,
            ) {
                Text(stringResource(R.string.update_the_hermes_core))
            }
            when (update) {
                HermesUpdate.Idle -> Unit
                is HermesUpdate.Running -> {
                    if (update.percent != null) {
                        LinearProgressIndicator(
                            progress = { update.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (update.message.isNotBlank()) {
                        UpdateNote(text = update.message)
                    } else if (state.hermesOnServer) {
                        UpdateNote(text = stringResource(R.string.server_update_running))
                    }
                }
                is HermesUpdate.Done -> UpdateNote(
                    text = if (update.changed) {
                        stringResource(R.string.hermes_core_updated, update.version)
                    } else {
                        stringResource(R.string.the_core_was_already_the_newest_version)
                    },
                )
                is HermesUpdate.Failed -> UpdateNote(
                    text = stringResource(
                        if (state.hermesOnServer) R.string.server_update_failed else R.string.update_failed_hermes_is_being_started,
                        update.reason,
                    ),
                    isProblem = true,
                )
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.update_the_hermes_core_2)) },
            text = {
                Text(
                    stringResource(
                        if (state.hermesOnServer) R.string.server_update_confirm else R.string.hermes_stops_for_a_few_minutes_while_it,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = false; onUpdate() }) { Text(stringResource(R.string.update)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel_2)) }
            },
        )
    }
}

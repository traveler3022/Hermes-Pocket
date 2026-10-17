package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.updates.UpdateCheck
import com.hermes.android.ui.viewmodel.AboutViewModel
import com.hermes.android.ui.viewmodel.AppDownload

/**
 * On launch, at most once every three days: if a newer build is published, say so over
 * whatever screen is open. Download and install are the About screen's own, in a dialog.
 */
@Composable
fun UpdatePromptDialog(viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    LaunchedEffect(viewModel) { viewModel.autoCheck() }
    // Same as the About screen: system screens may be missing, and coming back from
    // "install unknown apps" carries on with the install.
    LaunchedEffect(viewModel) {
        viewModel.launches.collect { intent ->
            runCatching { context.startActivity(intent) }
                .onFailure {
                    timber.log.Timber.w(it, "[Update] could not open ${intent.action}")
                    viewModel.onLaunchFailed()
                }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    if (!state.showPrompt) return
    val release = (state.result as? UpdateCheck.Available)?.release ?: return
    AlertDialog(
        // A tap outside must not throw away a download under way.
        onDismissRequest = {
            if (state.appDownload !is AppDownload.Downloading) viewModel.dismissPrompt()
        },
        title = { Text(stringResource(R.string.app_update_prompt_title)) },
        text = {
            Box(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AvailableRelease(
                    release = release,
                    installed = state.installedVersion,
                    download = state.appDownload,
                    isDebugBuild = state.isDebugBuild,
                    installedCommit = state.installedCommit,
                    onDownload = viewModel::downloadUpdate,
                    onCancel = viewModel::cancelDownload,
                    onInstall = viewModel::install,
                    onOpen = { uriHandler.openUri(release.pageUrl) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::dismissPrompt) {
                Text(stringResource(R.string.app_update_later))
            }
        },
    )
}

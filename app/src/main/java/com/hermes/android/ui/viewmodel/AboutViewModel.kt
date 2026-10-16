package com.hermes.android.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.updates.ApkDownload
import com.hermes.android.domain.updates.AppUpdates
import com.hermes.android.domain.updates.DownloadProblem
import com.hermes.android.domain.updates.ServerHermesUpdates
import com.hermes.android.domain.updates.UpdateCheck
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.InstallResult
import com.hermes.android.runtime.RuntimeState
import com.hermes.android.runtime.RuntimeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class AboutUiState(
    val installedVersion: String = "",
    val isChecking: Boolean = false,
    /** Null until a check has run — "not checked yet" is not "up to date". */
    val result: UpdateCheck? = null,
    /** True for a debug build: the release installs beside it, as a second app. */
    val isDebugBuild: Boolean = false,
    /** This build's commit (8 hex); empty when the build did not record one. */
    val installedCommit: String = "",
    val appDownload: AppDownload = AppDownload.Idle,
    /** Hermes Agent's own version (`hermes --version`), not this app's. */
    val hermesVersion: String? = null,
    val canUpdateHermes: Boolean = false,
    /** True when the update runs on a server (or Termux) through its dashboard, not in the app. */
    val hermesOnServer: Boolean = false,
    val hermesUpdate: HermesUpdate = HermesUpdate.Idle,
)

/** Fetching the app's release APK and handing it to the system installer. */
sealed class AppDownload {
    object Idle : AppDownload()

    /** [percent] is null while the size is unknown. */
    data class Downloading(val percent: Int?) : AppDownload()

    /** [needsPermission]: the user was sent to "install unknown apps" and has not allowed it. */
    data class Ready(val file: File, val needsPermission: Boolean = false) : AppDownload()
    data class Failed(val problem: DownloadProblem) : AppDownload()

    /** The phone has no installer that takes the APK; only the browser is left. */
    object InstallerUnavailable : AppDownload()
}

/** Updating Hermes Agent: inside the built-in Linux, or on the server through its dashboard. */
sealed class HermesUpdate {
    object Idle : HermesUpdate()
    data class Running(val message: String, val percent: Int?) : HermesUpdate()
    /** [changed] false: the newest version was already installed. */
    data class Done(val version: String, val changed: Boolean) : HermesUpdate()
    data class Failed(val reason: String) : HermesUpdate()
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val releases: AppUpdates,
    private val runtime: HermesRuntime,
    private val serverUpdates: ServerHermesUpdates,
) : ViewModel() {

    /** Remote and Termux run the dashboard, which can run `hermes update` where Hermes is. */
    private val onServer = !runtime.canUpdateHermes && runtime.type in setOf(RuntimeType.REMOTE, RuntimeType.TERMUX)

    private val _uiState = MutableStateFlow(
        AboutUiState(
            installedVersion = releases.installedVersion,
            hermesVersion = runtime.state.value.hermesVersion(),
            canUpdateHermes = runtime.canUpdateHermes || onServer,
            hermesOnServer = onServer,
            isDebugBuild = releases.isDebugBuild,
            installedCommit = releases.installedCommit,
        ),
    )
    val uiState = _uiState.asStateFlow()

    /** System screens to open: the installer, or the "install unknown apps" switch. */
    private val _launches = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val launches: SharedFlow<Intent> = _launches.asSharedFlow()

    private var downloadJob: Job? = null

    /** Set while the user is on the "install unknown apps" screen we sent them to. */
    private var awaitingInstallPermission = false

    /**
     * Asks GitHub what the newest release is.
     *
     * Only ever on request. A check on every open would mean the app calling
     * out to a third party without being asked — cheap in bytes, but not the
     * kind of thing a client for a self-hosted agent should do behind its
     * user's back.
     */
    fun check() {
        if (_uiState.value.isChecking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true)
            val result = releases.check()
            _uiState.value = _uiState.value.copy(isChecking = false, result = result)
        }
    }

    /** Download the available release's APK into the app's cache; cancel with [cancelDownload]. */
    fun downloadUpdate() {
        val apk = (_uiState.value.result as? UpdateCheck.Available)?.release?.apk ?: return
        if (downloadJob?.isActive == true) return
        _uiState.update { it.copy(appDownload = AppDownload.Downloading(null)) }
        downloadJob = viewModelScope.launch {
            var lastPercent = -1
            val job = coroutineContext[Job]
            val result = releases.download(apk) { read, total ->
                // A read already under way when Cancel was tapped must not bring the bar back.
                if (job?.isActive != true) return@download
                val percent = if (total > 0) ((read * 100) / total).toInt().coerceIn(0, 100) else null
                if (percent == null || percent != lastPercent) {
                    lastPercent = percent ?: -1
                    _uiState.update { it.copy(appDownload = AppDownload.Downloading(percent)) }
                }
            }
            _uiState.update {
                it.copy(
                    appDownload = when (result) {
                        is ApkDownload.Ready -> AppDownload.Ready(result.file)
                        is ApkDownload.Failed -> AppDownload.Failed(result.problem)
                    },
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update { it.copy(appDownload = AppDownload.Idle) }
    }

    /**
     * Open the system installer for the downloaded APK. Without "install unknown apps"
     * for this app, open that switch first; [onResumed] carries on when the user is back.
     */
    fun install() {
        val ready = _uiState.value.appDownload as? AppDownload.Ready ?: return
        if (releases.canInstallPackages()) {
            awaitingInstallPermission = false
            _uiState.update { it.copy(appDownload = ready.copy(needsPermission = false)) }
            _launches.tryEmit(releases.installerIntent(ready.file))
        } else {
            awaitingInstallPermission = true
            _launches.tryEmit(releases.installPermissionIntent())
        }
    }

    /** The screen is back in front; maybe from the "install unknown apps" switch. */
    fun onResumed() {
        if (!awaitingInstallPermission) return
        awaitingInstallPermission = false
        val ready = _uiState.value.appDownload as? AppDownload.Ready ?: return
        if (releases.canInstallPackages()) {
            install()
        } else {
            _uiState.update { it.copy(appDownload = ready.copy(needsPermission = true)) }
        }
    }

    /** Neither the installer nor the permission screen could be opened. */
    fun onLaunchFailed() {
        awaitingInstallPermission = false
        _uiState.update { it.copy(appDownload = AppDownload.InstallerUnavailable) }
    }

    /** Update Hermes Agent in place; the runtime stops and restarts it around the swap. */
    fun updateHermes() {
        if (_uiState.value.hermesUpdate is HermesUpdate.Running) return
        if (onServer) {
            updateOnServer()
            return
        }
        val before = _uiState.value.hermesVersion
        _uiState.update { it.copy(hermesUpdate = HermesUpdate.Running("", 0)) }
        viewModelScope.launch {
            val result = runtime.updateHermes { progress ->
                _uiState.update { it.copy(hermesUpdate = HermesUpdate.Running(progress.message, progress.percent)) }
            }
            _uiState.update { state ->
                when (result) {
                    is InstallResult.Success -> {
                        val version = result.info.hermesVersion ?: before.orEmpty()
                        state.copy(
                            hermesVersion = version,
                            hermesUpdate = HermesUpdate.Done(version, changed = version != before),
                        )
                    }
                    is InstallResult.Failure -> state.copy(hermesUpdate = HermesUpdate.Failed(result.reason))
                    else -> state.copy(hermesUpdate = HermesUpdate.Idle)
                }
            }
        }
    }

    /**
     * Server path: check, start, then poll the action until its receipt has a verdict. The
     * update restarts Hermes (and maybe the dashboard), so failed polls are waited out.
     */
    private fun updateOnServer() {
        _uiState.update { it.copy(hermesUpdate = HermesUpdate.Running("", null)) }
        viewModelScope.launch {
            val outcome = try {
                runServerUpdate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[About] Server update failed")
                HermesUpdate.Failed(e.message ?: e.javaClass.simpleName)
            }
            _uiState.update { state ->
                if (outcome is HermesUpdate.Done) state.copy(hermesVersion = outcome.version, hermesUpdate = outcome)
                else state.copy(hermesUpdate = outcome)
            }
        }
    }

    private suspend fun runServerUpdate(): HermesUpdate {
        val check = serverUpdates.check()
        check.currentVersion?.let { v -> _uiState.update { it.copy(hermesVersion = v) } }
        if (!check.canApply) {
            return HermesUpdate.Failed(check.message ?: check.updateCommand ?: "this install can't update itself")
        }
        if (!check.updateAvailable && check.behind == 0) {
            return HermesUpdate.Done(check.currentVersion.orEmpty(), changed = false)
        }
        val actionId = when (val start = serverUpdates.start()) {
            is ServerHermesUpdates.Start.Refused -> return HermesUpdate.Failed(start.message)
            is ServerHermesUpdates.Start.Started -> start.actionId
        }
        val deadline = System.currentTimeMillis() + SERVER_UPDATE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(SERVER_POLL_MS)
            val status = try {
                serverUpdates.status(actionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.d(e, "[About] Update status not reachable yet")
                continue
            }
            when (status) {
                is ServerHermesUpdates.Status.Running ->
                    _uiState.update { it.copy(hermesUpdate = HermesUpdate.Running(status.lastLine.orEmpty(), null)) }
                is ServerHermesUpdates.Status.Succeeded ->
                    return HermesUpdate.Done(status.version ?: check.currentVersion.orEmpty(), changed = true)
                is ServerHermesUpdates.Status.Failed -> return HermesUpdate.Failed(status.reason)
            }
        }
        return HermesUpdate.Failed("no answer from the server for 30 minutes")
    }

    private companion object {
        const val SERVER_POLL_MS = 3_000L
        const val SERVER_UPDATE_TIMEOUT_MS = 30 * 60_000L
    }
}

private fun RuntimeState.hermesVersion(): String? = when (this) {
    is RuntimeState.Running -> info.hermesVersion
    is RuntimeState.Installed -> info.hermesVersion
    is RuntimeState.Detected -> info.hermesVersion
    else -> null
}

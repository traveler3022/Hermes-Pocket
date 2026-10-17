package com.hermes.android.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.ops.Checkpoints
import com.hermes.android.domain.ops.DebugShare
import com.hermes.android.domain.ops.Hooks
import com.hermes.android.domain.ops.OpsAction
import com.hermes.android.domain.ops.ServerOps
import com.hermes.android.domain.ops.ShellHook
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Settings › Maintenance: Hermes' doctor, security audit and other diagnostics, backup and
 * restore, the debug report, rollback checkpoints and shell hooks, on the machine Hermes runs on.
 */
@HiltViewModel
class OpsViewModel @Inject constructor(
    private val ops: ServerOps,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** One background command's run, as shown in the output sheet. */
    data class Run(
        val action: OpsAction,
        val running: Boolean = true,
        val exitCode: Int? = null,
        val lines: List<String> = emptyList(),
        val error: String? = null,
    ) {
        val succeeded: Boolean get() = !running && error == null && exitCode == 0
    }

    data class UiState(
        val checkpoints: Checkpoints? = null,
        val hooks: Hooks? = null,
        val loading: Boolean = true,
        val loadError: String? = null,
        /** The command whose output is open; null when the sheet is closed. */
        val run: Run? = null,
        /** The last backup's path on the server, once `hermes backup` finished. */
        val backupArchive: String? = null,
        val downloadingBackup: Boolean = false,
        val sharing: Boolean = false,
        val debugShare: DebugShare? = null,
        val hookBusy: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var runJob: Job? = null

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            var error: String? = null
            val checkpoints = try {
                ops.checkpoints()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Ops] checkpoints failed")
                error = e.message ?: e.javaClass.simpleName
                null
            }
            val hooks = try {
                ops.hooks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Ops] hooks failed")
                error = error ?: e.message ?: e.javaClass.simpleName
                null
            }
            _uiState.update { it.copy(checkpoints = checkpoints, hooks = hooks, loading = false, loadError = error) }
        }
    }

    /** Runs [action] and follows its output until it ends. */
    fun run(action: OpsAction) {
        if (_uiState.value.run?.running == true) return
        follow(action) { ops.start(action) }
    }

    fun backup() {
        if (_uiState.value.run?.running == true) return
        _uiState.update { it.copy(backupArchive = null) }
        var archive: String? = null
        follow(OpsAction.BACKUP, onSuccess = { _uiState.update { it.copy(backupArchive = archive) } }) {
            archive = ops.startBackup()
        }
    }

    fun downloadBackup() {
        val archive = _uiState.value.backupArchive ?: return
        if (_uiState.value.downloadingBackup) return
        _uiState.update { it.copy(downloadingBackup = true) }
        viewModelScope.launch {
            try {
                val savedTo = ops.downloadBackup(archive)
                _messages.tryEmit(savedTo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Ops] backup download failed")
                _messages.tryEmit(e.message ?: e.javaClass.simpleName)
            } finally {
                _uiState.update { it.copy(downloadingBackup = false) }
            }
        }
    }

    /** Restores the backup zip the user picked; the screen has already asked for confirmation. */
    fun restore(uri: Uri) {
        if (_uiState.value.run?.running == true) return
        follow(OpsAction.RESTORE, onSuccess = { load() }) {
            val file = copyToCache(uri)
            try {
                ops.startRestore(file)
            } finally {
                file.delete()
            }
        }
    }

    fun pruneCheckpoints() {
        if (_uiState.value.run?.running == true) return
        follow(OpsAction.CHECKPOINTS_PRUNE, onSuccess = { load() }) { ops.start(OpsAction.CHECKPOINTS_PRUNE) }
    }

    /** Uploads the redacted debug report; the screen has already asked for confirmation. */
    fun shareDebugReport() {
        if (_uiState.value.sharing) return
        _uiState.update { it.copy(sharing = true, debugShare = null) }
        viewModelScope.launch {
            try {
                val share = ops.debugShare()
                _uiState.update { it.copy(debugShare = share) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Ops] debug share failed")
                _messages.tryEmit(e.message ?: e.javaClass.simpleName)
            } finally {
                _uiState.update { it.copy(sharing = false) }
            }
        }
    }

    fun dismissDebugShare() {
        _uiState.update { it.copy(debugShare = null) }
    }

    fun addHook(event: String, command: String, matcher: String?, approve: Boolean) {
        hookAction { ops.addHook(event.trim(), command.trim(), matcher?.trim(), approve) }
    }

    fun removeHook(hook: ShellHook) {
        hookAction { ops.removeHook(hook.event, hook.command) }
    }

    /** Closes the output sheet; a command still running goes on on the server. */
    fun closeRun() {
        runJob?.cancel()
        _uiState.update { it.copy(run = null) }
    }

    private fun hookAction(block: suspend () -> Unit) {
        if (_uiState.value.hookBusy) return
        _uiState.update { it.copy(hookBusy = true) }
        viewModelScope.launch {
            try {
                block()
                val hooks = ops.hooks()
                _uiState.update { it.copy(hooks = hooks) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Ops] hook change failed")
                _messages.tryEmit(e.message ?: e.javaClass.simpleName)
            } finally {
                _uiState.update { it.copy(hookBusy = false) }
            }
        }
    }

    /** Starts a command with [start], then reads its output every second or so until it ends. */
    private fun follow(action: OpsAction, onSuccess: () -> Unit = {}, start: suspend () -> Unit) {
        runJob?.cancel()
        _uiState.update { it.copy(run = Run(action)) }
        runJob = viewModelScope.launch {
            try {
                start()
                var polls = 0
                while (true) {
                    delay(POLL_MS)
                    val status = ops.status(action)
                    _uiState.update { state ->
                        state.copy(run = state.run?.copy(running = status.running, exitCode = status.exitCode, lines = status.lines))
                    }
                    if (!status.running) break
                    if (++polls > MAX_POLLS) {
                        _uiState.update { state -> state.copy(run = state.run?.copy(running = false)) }
                        break
                    }
                }
                if (_uiState.value.run?.succeeded == true) onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Ops] %s failed", action.key)
                _uiState.update { state ->
                    state.copy(run = state.run?.copy(running = false, error = e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    private suspend fun copyToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "ops-uploads").apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString())
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the file")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        file
    }

    private companion object {
        const val POLL_MS = 1_200L

        /** About twenty minutes; a backup of a large home can take a while. */
        const val MAX_POLLS = 1_000
    }
}

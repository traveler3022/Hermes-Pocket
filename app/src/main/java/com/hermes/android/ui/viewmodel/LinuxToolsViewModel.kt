package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.runtime.linux.LinuxDesktop
import com.hermes.android.runtime.linux.ProotEnvironment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One of Aether's Alpine package profiles (`AlpineRuntime.AlpinePackageProfiles`). */
data class PackageProfile(
    val id: String,
    val titleEn: String,
    val titleFa: String,
    val packages: List<String>,
    val verify: String,
    /** Runs after `apk add`, e.g. an npm install the profile also needs. */
    val postInstall: String? = null,
)

data class LinuxToolsUiState(
    val rootfsInstalled: Boolean = false,
    /** Profile id → installed; absent until the first check finishes. */
    val installed: Map<String, Boolean> = emptyMap(),
    val checking: Boolean = false,
    val installing: String? = null,
    val log: List<String> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class LinuxToolsViewModel @Inject constructor(
    private val environment: ProotEnvironment,
) : ViewModel() {

    private val _state = MutableStateFlow(LinuxToolsUiState(rootfsInstalled = environment.isRootfsInstalled))
    val state: StateFlow<LinuxToolsUiState> = _state.asStateFlow()

    val profiles: List<PackageProfile> = Profiles

    fun refresh() {
        val installed = environment.isRootfsInstalled
        _state.update { it.copy(rootfsInstalled = installed) }
        if (!installed || _state.value.checking) return
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            val result = runCatching { environment.run(checkScript()) }
            val statuses = result.getOrNull()?.output.orEmpty().lineSequence()
                .filter { it.startsWith(STATUS_PREFIX) }
                .associate { line ->
                    val (id, status) = line.removePrefix(STATUS_PREFIX).split(' ', limit = 2)
                    id to (status == "ok")
                }
            _state.update {
                it.copy(
                    checking = false,
                    installed = statuses,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    /** Aether's `installPackageProfile`: `apk add`, then trust the verify command over apk's exit code. */
    fun install(profile: PackageProfile) {
        if (_state.value.installing != null) return
        viewModelScope.launch {
            val command = "apk add --no-cache --no-chown ${profile.packages.joinToString(" ")}"
            _state.update { it.copy(installing = profile.id, log = listOf("$ $command"), error = null) }
            try {
                var result = environment.run(command) { line -> appendLog(line) }
                profile.postInstall?.let { post ->
                    appendLog("$ $post")
                    result = environment.run(post) { line -> appendLog(line) }
                }
                // apk can exit 1 on Android ("failed to write database") after installing everything.
                val ok = result.ok || environment.run(profile.verify).ok
                _state.update {
                    it.copy(
                        installed = it.installed + (profile.id to ok),
                        error = if (ok) null else result.output.lines().takeLast(3).joinToString("\n"),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Install failed") }
            } finally {
                _state.update { it.copy(installing = null) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun showMessage(message: String) = _state.update { it.copy(error = message) }

    suspend fun terminalLaunchSpec(): Result<ProotEnvironment.TerminalLaunchSpec> = withContext(Dispatchers.IO) {
        runCatching {
            check(environment.isRootfsInstalled) { "Install the built-in Linux runtime first." }
            environment.terminalLaunchSpec()
        }
    }

    private fun appendLog(line: String) {
        if (line.isBlank()) return
        _state.update { it.copy(log = (it.log + line).takeLast(LOG_LINES)) }
    }

    private fun checkScript(): String = Profiles.joinToString("\n") { profile ->
        "if ( ${profile.verify} ) >/dev/null 2>&1; then echo '$STATUS_PREFIX${profile.id} ok'; " +
            "else echo '$STATUS_PREFIX${profile.id} missing'; fi"
    }

    companion object {
        const val DesktopProfileId = "desktop"
        private const val STATUS_PREFIX = "HERMES2_PROFILE "
        private const val LOG_LINES = 12

        private val Profiles = listOf(
            PackageProfile(
                "python", "Python tools", "ابزارهای پایتون",
                listOf("python3", "py3-pip", "py3-virtualenv"),
                "python3 --version && pip3 --version && virtualenv --version",
            ),
            PackageProfile(
                "node", "Node.js", "Node.js",
                listOf("nodejs", "npm"),
                "node --version && npm --version",
            ),
            PackageProfile(
                "git_search", "Git & code search", "Git و جستجوی کد",
                listOf("git", "ripgrep", "fd"),
                "git --version && rg --version && fd --version",
            ),
            PackageProfile(
                "ssh", "SSH client", "کلاینت SSH",
                listOf("openssh-client"),
                "ssh -V",
            ),
            PackageProfile(
                DesktopProfileId, "Browser & desktop (VNC)", "مرورگر و دسکتاپ (VNC)",
                LinuxDesktop.Packages,
                LinuxDesktop.VerifyCommand,
                postInstall = LinuxDesktop.PostInstall,
            ),
        )
    }
}

package com.hermes.android.runtime

import android.content.Context
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.linux.ProotLinuxRuntime
import com.hermes.android.runtime.termux.TermuxBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Persisted user choice between the Termux and built-in Linux runtimes. */
@Singleton
class RuntimeSelection @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("runtime_selection", Context.MODE_PRIVATE)
    private val _selected = MutableStateFlow(load(context))
    val selected: StateFlow<RuntimeType> = _selected.asStateFlow()

    fun select(type: RuntimeType) {
        require(type in Selectable) { "Runtime $type is not selectable" }
        prefs.edit().putString(KEY, type.name).apply()
        _selected.value = type
    }

    private fun load(context: Context): RuntimeType {
        prefs.getString(KEY, null)
            ?.let { saved -> runCatching { RuntimeType.valueOf(saved) }.getOrNull() }
            ?.takeIf { it in Selectable }
            ?.let { return it }
        // Existing Termux users keep Termux; fresh installs get the built-in runtime.
        val termuxSetUp = context.getSharedPreferences("hermes_runtime", Context.MODE_PRIVATE)
            .getBoolean("installed", false)
        return if (termuxSetUp) RuntimeType.TERMUX else RuntimeType.PROOT_LINUX
    }

    companion object {
        val Selectable = setOf(RuntimeType.PROOT_LINUX, RuntimeType.TERMUX)
        private const val KEY = "selected_runtime"
    }
}

/** Routes every [HermesRuntime] call to whichever runtime the user selected. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SwitchableHermesRuntime @Inject constructor(
    private val selection: RuntimeSelection,
    private val termux: TermuxBridge,
    private val linux: ProotLinuxRuntime,
    private val gatewayClient: GatewayClient,
) : HermesRuntime {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val active: HermesRuntime get() = runtimeFor(selection.selected.value)

    private fun runtimeFor(type: RuntimeType): HermesRuntime =
        if (type == RuntimeType.TERMUX) termux else linux

    fun select(type: RuntimeType) {
        val previous = active
        if (selection.selected.value == type) return
        selection.select(type)
        scope.launch {
            // The gateway client treats "already connected" as success regardless of URL.
            gatewayClient.disconnect()
            if (previous === linux) {
                runCatching { linux.stopGateway() }.onFailure { Timber.w(it, "[Runtime] Stopping Linux gateway failed") }
            }
        }
    }

    override val type: RuntimeType get() = active.type

    override val state: StateFlow<RuntimeState> = selection.selected
        .flatMapLatest { runtimeFor(it).state }
        .stateIn(scope, SharingStarted.Eagerly, active.state.value)

    override val installProgress: StateFlow<InstallProgress?> = selection.selected
        .flatMapLatest { runtimeFor(it).installProgress }
        .stateIn(scope, SharingStarted.Eagerly, active.installProgress.value)

    override suspend fun detect(): DetectionResult = active.detect()
    override suspend fun checkInstallPrerequisites(): PrerequisiteResult = active.checkInstallPrerequisites()
    override suspend fun install(progressEmitter: ProgressEmitter): InstallResult = active.install(progressEmitter)
    override suspend fun verify(): VerifyResult = active.verify()
    override suspend fun startGateway(): GatewayHandle = active.startGateway()
    override suspend fun stopGateway(): StopResult = active.stopGateway()
    override suspend fun fetchLogs() = active.fetchLogs()
    override suspend fun runDoctor(): String = active.runDoctor()
    override suspend fun isHealthy(): Boolean = active.isHealthy()
    override fun getWebSocketUrl(): String = active.getWebSocketUrl()
    override fun launchHostApp(): Boolean = active.launchHostApp()
    override fun getInstallInstructions(): InstallInstructions? = active.getInstallInstructions()
}

package com.hermes.android.runtime

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton facade for accessing the active [HermesRuntime].
 *
 * ## Why a Manager?
 *
 * The Android UI and business logic should NOT inject a concrete runtime
 * implementation directly — they inject this manager and read
 * [runtime]. When the active runtime implementation is swapped, only
 * the Hilt binding in [com.hermes.android.di.RuntimeModule] changes;
 * every consumer of [HermesRuntimeManager] keeps working unchanged.
 *
 * ## Switching
 *
 * [selectRuntime] switches between Termux and the built-in Linux; [runtime] is the
 * [SwitchableHermesRuntime] router, so it always reaches the selected one.
 *
 * Reference: ADR-001 (migration adapter), ADR-009 (production embedded Python)
 */
@Singleton
class HermesRuntimeManager @Inject constructor(
    /**
     * The currently-bound runtime. Injected by Hilt — see
     * [com.hermes.android.di.RuntimeModule] for the binding.
     */
    private val boundRuntime: HermesRuntime,
    private val router: SwitchableHermesRuntime,
    selection: RuntimeSelection,
) {

    /** Runtime the user picked (Termux or built-in Linux); persisted across launches. */
    val selectedRuntime: StateFlow<RuntimeType> = selection.selected

    /** False on a fresh install until the user picks Termux or built-in Linux. */
    val runtimeChosen: StateFlow<Boolean> = selection.hasChosen

    fun selectRuntime(type: RuntimeType) = router.select(type)

    /** The active runtime instance. */
    val runtime: HermesRuntime get() = boundRuntime

    /** Pass-through for the runtime's state flow. */
    val state: StateFlow<RuntimeState> get() = boundRuntime.state

    /** Pass-through for install progress. */
    val installProgress: StateFlow<InstallProgress?> get() = boundRuntime.installProgress

    /** Convenience: the runtime type currently bound. */
    val runtimeType: RuntimeType get() = boundRuntime.type
}

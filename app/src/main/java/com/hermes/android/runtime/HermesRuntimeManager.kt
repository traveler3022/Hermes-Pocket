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
 * ## Future extension
 *
 * The manager may eventually support runtime switching at runtime
 * (e.g. user chooses between "Migration adapter (development)" and "Embedded (beta)").
 * For now it just holds the single bound implementation.
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
) {

    /** The active runtime instance. */
    val runtime: HermesRuntime get() = boundRuntime

    /** Pass-through for the runtime's state flow. */
    val state: StateFlow<RuntimeState> get() = boundRuntime.state

    /** Pass-through for install progress. */
    val installProgress: StateFlow<InstallProgress?> get() = boundRuntime.installProgress

    /** Convenience: the runtime type currently bound. */
    val runtimeType: RuntimeType get() = boundRuntime.type
}

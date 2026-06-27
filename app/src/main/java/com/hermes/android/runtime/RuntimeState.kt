package com.hermes.android.runtime

/**
 * Lifecycle state of a [HermesRuntime].
 *
 * State machine:
 * ```
 * NotDetected ──detect()──► Detecting ──┬──► Detected ──install()──► Installing ──┬──► Installed
 *                                       │                                          │
 *                                       └──► Error                                └──► Error
 * ```
 *
 * UI observes [HermesRuntime.state] and renders accordingly.
 */
sealed class RuntimeState {

    /** Initial state — runtime has not been probed yet. */
    object NotDetected : RuntimeState()

    /** Detection in progress. */
    object Detecting : RuntimeState()

    /**
     * Runtime detected but not yet installed/initialized.
     * [info] contains what was discovered (e.g. host-app version, Python version).
     */
    data class Detected(val info: RuntimeInfo) : RuntimeState()

    /** Installation in progress. See [HermesRuntime.installProgress] for details. */
    object Installing : RuntimeState()

    /** Runtime is installed and ready to start the gateway. */
    data class Installed(val info: RuntimeInfo) : RuntimeState()

    /** Gateway is running. */
    data class Running(val info: RuntimeInfo, val gateway: GatewayHandle) : RuntimeState()

    /** An error occurred. [message] is user-facing; [cause] is the underlying exception (optional). */
    data class Error(val message: String, val cause: Throwable? = null) : RuntimeState()
}

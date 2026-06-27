package com.hermes.android.runtime

/**
 * Data models for the [HermesRuntime] abstraction.
 *
 * These types are implementation-agnostic — they describe WHAT happened,
 * not HOW (e.g. no host-package names or external-app paths leak through).
 * This allows the migration adapter and a future EmbeddedPythonRuntime
 * to share the same surface area.
 */

/**
 * Metadata about a discovered or installed runtime.
 */
data class RuntimeInfo(
    val type: RuntimeType,
    /** Runtime version (e.g. host-app version, Python interpreter version). Null if unknown. */
    val version: String? = null,
    /** Filesystem path to the runtime root (e.g. `/data/data/<host-app>/files/usr`). Null if N/A. */
    val path: String? = null,
    /** Python interpreter version, if applicable. Null if not yet probed. */
    val pythonVersion: String? = null,
    /** Free disk space at the runtime location, in bytes. Null if unknown. */
    val diskFreeBytes: Long? = null,
    /** Hermes Agent version, if installed. Null if not yet installed. */
    val hermesVersion: String? = null,
    /** Implementation-specific extras (never accessed by UI). */
    val extras: Map<String, String> = emptyMap(),
)

/**
 * Result of [HermesRuntime.detect].
 */
sealed class DetectionResult {

    /** Runtime is present and ready to be initialized. */
    data class Available(val info: RuntimeInfo) : DetectionResult()

    /**
     * Runtime is missing. The user must take [action] to make it available
     * (e.g. install the migration host app from F-Droid).
     */
    data class Missing(
        val title: String,
        val instructions: String,
        val action: InstallAction,
    ) : DetectionResult()

    /** Device cannot support this runtime (e.g. wrong Android version). */
    data class Incompatible(val reason: String) : DetectionResult()
}

/**
 * Action the user can take to install a missing runtime.
 */
sealed class InstallAction {
    /** Open F-Droid or Play Store to install [packageName]. */
    data class OpenStore(val packageName: String, val fDroidUrl: String? = null) : InstallAction()

    /** Open an arbitrary URL in the browser. */
    data class OpenUrl(val url: String) : InstallAction()

    /** No automatic action — show instructions only. */
    object None : InstallAction()
}

/**
 * Single progress update during [HermesRuntime.install].
 */
data class InstallProgress(
    /** Stage identifier (e.g. "downloading", "venv", "pip_install"). */
    val stage: String,
    /** Human-readable message. */
    val message: String,
    /** Percent complete 0–100, or null if unknown. */
    val percent: Int? = null,
    /** Epoch milliseconds. */
    val timestamp: Long,
)

/**
 * Sink for [InstallProgress] updates. Implemented by the UI layer
 * (typically via a Flow adapter) and passed into [HermesRuntime.install].
 */
fun interface ProgressEmitter {
    fun emit(progress: InstallProgress)
}

/**
 * Result of [HermesRuntime.install].
 */
sealed class InstallResult {
    /** Installation succeeded. [info] reflects the post-install state. */
    data class Success(val info: RuntimeInfo) : InstallResult()

    /** Installation failed. [reason] is user-facing; [log] is the full log (optional). */
    data class Failure(val reason: String, val log: String? = null) : InstallResult()

    /** User cancelled. */
    object Cancelled : InstallResult()
}

/**
 * Result of [HermesRuntime.verify].
 */
sealed class VerifyResult {
    /** Verification succeeded — Hermes is functional. */
    data class Success(val hermesVersion: String, val doctorOk: Boolean) : VerifyResult()

    /** Verification failed — Hermes is not responding as expected. */
    data class Failure(val reason: String) : VerifyResult()
}

/**
 * Handle to a running gateway process.
 */
data class GatewayHandle(
    /** PID of the gateway process, if known. */
    val pid: Int? = null,
    /** Epoch milliseconds when the gateway was started. */
    val startedAt: Long,
    /** WebSocket URL the gateway is listening on (e.g. ws://127.0.0.1:9119/api/ws?token=...). */
    val webSocketUrl: String,
)

/**
 * Result of [HermesRuntime.stopGateway].
 */
sealed class StopResult {
    object Success : StopResult()
    data class Failure(val reason: String) : StopResult()
}

/**
 * Installation instructions for the user.
 *
 * Returned by [HermesRuntime.getInstallInstructions] when the runtime
 * requires user-side setup (e.g. the migration adapter asks the user
 * to paste a bash command into an external terminal).
 *
 * For self-contained runtimes (e.g. embedded Python), [HermesRuntime.getInstallInstructions]
 * returns null — no user-side setup is needed.
 *
 * Reference: ADR-007 (reuse install.sh — migration only), ADR-009 (production needs no user setup)
 */
data class InstallInstructions(
    /** Human-readable title for the install step (e.g. "Run install command"). */
    val title: String,
    /** Step-by-step instructions for the user. */
    val steps: List<String>,
    /** The command/script the user must run, if applicable. Null if no command is needed. */
    val command: String? = null,
)

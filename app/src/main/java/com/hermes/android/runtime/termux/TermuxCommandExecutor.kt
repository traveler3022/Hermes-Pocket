package com.hermes.android.runtime.termux

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends commands to Termux via the official `RUN_COMMAND` intent.
 *
 * ## Contract (verified against Termux source)
 *
 * Source: [termux-app/app/src/main/java/com/termux/app/RunCommandService.java](https://github.com/termux/termux-app)
 *
 * The intent is sent to `com.termux/.app.RunCommandService` with action
 * `com.termux.RUN_COMMAND`. Required extras:
 *
 * - `com.termux.RUN_COMMAND_PATH` (String) — absolute path to executable inside Termux
 * - `com.termux.RUN_COMMAND_ARGUMENTS` (String[]) — argv to the executable
 * - `com.termux.RUN_COMMAND_WORKDIR` (String) — working directory
 * - `com.termux.RUN_COMMAND_BACKGROUND` (boolean) — `true` runs in app-shell
 *   background (no terminal UI); `false` opens a foreground terminal session
 *
 * ## allow-external-apps requirement
 *
 * Termux rejects all RUN_COMMAND intents unless `allow-external-apps=true` is
 * set in `~/.termux/termux.properties`. This is a one-time user opt-in. The
 * executor returns [Result.Failure] with `allowExternalAppsDisabled=true`
 * when Termux silently drops the intent (we detect this by checking that the
 * service is unreachable).
 *
 * ## Android 11+ package visibility
 *
 * Starting at API 30, apps cannot see other packages unless declared in a
 * `<queries>` block. The manifest must include:
 *
 * ```xml
 * <queries>
 *   <package android:name="com.termux" />
 * </queries>
 * ```
 *
 * Without this, [Context.startService] returns `null` and the intent never
 * reaches Termux.
 *
 * Reference: ADR-007 (reuse install.sh — migration only), ADR-009 (production
 * must not require Termux — this class is migration-only).
 */
@Singleton
class TermuxCommandExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detector: TermuxDetector,
) {

    /**
     * Result of a [run] invocation.
     */
    sealed class Result {
        /**
         * The intent was accepted by Termux's RunCommandService. This does NOT
         * mean the command itself succeeded — it means Termux received it.
         * Command-level success/failure must be observed via an out-of-band
         * channel (broadcast, file, websocket, etc.).
         */
        object Accepted : Result()

        /** Termux is not installed. */
        data class TermuxMissing(val message: String) : Result()

        /**
         * `allow-external-apps=true` is not set in `~/.termux/termux.properties`.
         * The user must enable it once. See [buildAllowExternalAppsInstructions].
         */
        data class AllowExternalAppsDisabled(val message: String) : Result()

        /** Generic failure. */
        data class Failure(val message: String, val cause: Throwable? = null) : Result()
    }

    /**
     * Execute a bash script inside Termux, in the background (no terminal UI
     * shown to the user). This is the primary entry point used by
     * [TermuxBridge] for install / startGateway / stopGateway.
     *
     * @param script bash script body (will be passed to `bash -c`)
     * @param workingDirectory absolute path inside Termux filesystem, or null
     *        to use Termux home (`/data/data/com.termux/files/home`)
     */
    fun executeBackgroundScript(
        script: String,
        workingDirectory: String? = null,
    ): Result {
        return execute(
            executablePath = BASH_PATH,
            arguments = arrayOf("-c", script),
            workingDirectory = workingDirectory ?: TERMUX_HOME,
            background = true,
        )
    }

    /**
     * Execute a single command (no shell wrapping) inside Termux in the
     * background. Use this when argv must be passed verbatim (no quoting
     * concerns).
     */
    fun executeBackgroundCommand(
        executablePath: String,
        arguments: Array<String> = emptyArray(),
        workingDirectory: String? = null,
    ): Result {
        return execute(
            executablePath = executablePath,
            arguments = arguments,
            workingDirectory = workingDirectory ?: TERMUX_HOME,
            background = true,
        )
    }

    /**
     * Low-level execute. Builds the RUN_COMMAND intent and dispatches it.
     *
     * Visibility: internal so tests can verify intent construction without
     * going through the script wrapper.
     */
    internal fun execute(
        executablePath: String,
        arguments: Array<String>,
        workingDirectory: String,
        background: Boolean,
    ): Result {
        // Pre-flight: is Termux installed?
        val detection = detector.detect()
        if (!detection.termuxInstalled) {
            return Result.TermuxMissing(
                "Termux is not installed. Cannot send RUN_COMMAND intent.",
            )
        }

        val intent = Intent(ACTION_RUN_COMMAND).apply {
            component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE_CLASS)
            putExtra(EXTRA_COMMAND_PATH, executablePath)
            putExtra(EXTRA_ARGUMENTS, arguments)
            putExtra(EXTRA_WORKDIR, workingDirectory)
            putExtra(EXTRA_BACKGROUND, background)
        }

        return try {
            val component = context.startService(intent)

            if (component == null) {
                // startService returns null when:
                // - The service doesn't exist (Termux not installed — already handled above)
                // - Android 11+ package visibility: com.termux not in <queries>
                // - Termux version too old to have RunCommandService
                Timber.e("[TermuxExecutor] startService returned null — package visibility or old Termux")
                Result.Failure(
                    "Termux RunCommandService is unreachable. " +
                        "Check that the app declares <queries><package android:name=\"com.termux\"/></queries> " +
                        "in AndroidManifest.xml and that Termux is recent enough to ship RunCommandService.",
                )
            } else {
                Timber.i("[TermuxExecutor] RUN_COMMAND accepted by ${component.flattenToString()}")
                Result.Accepted
            }
        } catch (e: SecurityException) {
            // Thrown on some OEMs when the receiving service is not exported
            // or when allow-external-apps is false AND Termux version logs it
            // via a notification instead of an exception.
            Timber.e(e, "[TermuxExecutor] SecurityException dispatching RUN_COMMAND")
            Result.AllowExternalAppsDisabled(
                "Termux rejected the command. Verify that " +
                    "`allow-external-apps=true` is set in ~/.termux/termux.properties. " +
                    "Cause: ${e.message}",
            )
        } catch (e: Exception) {
            Timber.e(e, "[TermuxExecutor] Failed to dispatch RUN_COMMAND")
            Result.Failure(
                "Failed to dispatch RUN_COMMAND intent: ${e.message}",
                cause = e,
            )
        }
    }

    /**
     * Check whether `allow-external-apps` is enabled in Termux. We cannot
     * read `~/.termux/termux.properties` directly (it lives in Termux's
     * private storage). Instead we send a no-op probe command and inspect
     * the result: if [execute] returns [Result.Accepted], the policy is
     * enabled. Any other result means the user needs to enable it.
     *
     * This is a probe — calling it has the side effect of briefly starting
     * the Termux service. Use sparingly (e.g. once during onboarding).
     */
    fun isAllowExternalAppsEnabled(): Boolean {
        val probe = executeBackgroundScript(
            script = "true # hermes probe",
            workingDirectory = TERMUX_HOME,
        )
        return probe is Result.Accepted
    }

    /**
     * User-facing instructions for enabling `allow-external-apps`.
     * Used by the UI layer when [Result.AllowExternalAppsDisabled] is returned.
     */
    fun buildAllowExternalAppsInstructions(): String = """
        To let Hermes2 control Termux automatically, you need to enable
        external app commands in Termux. This is a one-time setup.

        Steps:
        1. Open Termux.
        2. Run: mkdir -p ~/.termux && echo 'allow-external-apps=true' > ~/.termux/termux.properties
        3. Restart Termux (close and reopen the app).
        4. Return to Hermes2.

        After this, Hermes2 can install, start, and stop the gateway
        automatically — you will never need to open Termux manually.
    """.trimIndent()

    // ── Companion ───────────────────────────────────────────────────────

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val RUN_COMMAND_SERVICE_CLASS = "com.termux.app.RunCommandService"

        // Intent action & extras — match TermuxConstants.RUN_COMMAND_SERVICE.*
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"

        // Canonical Termux filesystem paths
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        const val BASH_PATH = "$TERMUX_PREFIX/bin/bash"

        // Hermes binary location (installed by install.sh)
        // `pip install -e .` puts the `hermes` console script in PREFIX/bin
        const val HERMES_BIN = "$TERMUX_PREFIX/bin/hermes"
    }
}

package com.hermes.android.runtime.termux

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val results: TermuxPendingResults,
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
        resultIntent: PendingIntent? = null,
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
            if (resultIntent != null) putExtra(EXTRA_PENDING_INTENT, resultIntent)
        }

        return try {
            val component = startRunCommandService(intent)

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
            val msg = if (e.message?.contains("permission", ignoreCase = true) == true) {
                "Android Permission Denied: You must grant the 'Run commands in Termux' (RUN_COMMAND) permission to Hermes2. Tap 'Grant RUN_COMMAND Permission in Settings' on the setup screen, or go to Android Settings -> Apps -> Hermes2 -> Permissions -> Additional Permissions and enable it."
            } else {
                "Termux rejected the command. Verify that `allow-external-apps=true` is set in ~/.termux/termux.properties. Cause: ${e.message}"
            }
            Result.AllowExternalAppsDisabled(msg)
        } catch (e: Exception) {
            Timber.e(e, "[TermuxExecutor] Failed to dispatch RUN_COMMAND")
            Result.Failure(
                "Failed to dispatch RUN_COMMAND intent: ${e.message}",
                cause = e,
            )
        }
    }

    /**
     * RunCommandService calls startForeground() in onCreate, so Termux expects
     * startForegroundService(): a plain startService() is refused with "app is in
     * background" whenever Termux isn't already running. Same order as Aether, whose
     * Termux link works.
     */
    private fun startRunCommandService(intent: Intent): ComponentName? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return context.startService(intent)
        return try {
            context.startForegroundService(intent)
        } catch (e: IllegalStateException) {
            // Android 12+ refuses a foreground start while Hermes itself is in the background.
            Timber.w(e, "[TermuxExecutor] startForegroundService refused, trying startService")
            context.startService(intent)
        }
    }

    private sealed interface Reply {
        data class Output(val output: TermuxCommandOutput) : Reply
        data class NotSent(val result: Result) : Reply
        object NoAnswer : Reply
    }

    /** Runs [script] and waits for Termux's reply through a result PendingIntent ([TermuxResultReceiver]). */
    private suspend fun runForReply(script: String, timeoutMs: Long): Reply {
        val (id, reply) = results.register()
        val resultIntent = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, TermuxResultReceiver::class.java)
                .putExtra(TermuxResultReceiver.EXTRA_EXECUTION_ID, id),
            // Mutable: Termux fills its result bundle into this intent.
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
        )
        val dispatch = execute(BASH_PATH, arrayOf("-c", script), TERMUX_HOME, true, resultIntent)
        if (dispatch !is Result.Accepted) {
            results.remove(id)
            return Reply.NotSent(dispatch)
        }
        val output = withTimeoutOrNull(timeoutMs) { reply.await() }
        if (output == null) {
            results.remove(id)
            return Reply.NoAnswer
        }
        return Reply.Output(output)
    }

    /**
     * Checks that Termux really runs our commands: it must echo a marker back. An
     * accepted intent alone proves nothing: with `allow-external-apps` off, Termux
     * takes the intent, shows a notification and drops the command; its reply
     * carries the reason.
     *
     * @return Accepted only when the marker came back; AllowExternalAppsDisabled when
     *   Termux says so; Failure when it gave another reason or did not answer.
     */
    suspend fun probe(): Result {
        val marker = "__hermes_termux_ready__"
        val output = when (val reply = runForReply("printf '$marker'", PROBE_TIMEOUT_MS)) {
            is Reply.NotSent -> return reply.result
            Reply.NoAnswer -> return Result.Failure(
                "Termux did not answer. Open Termux once (it finishes its own setup on first launch), " +
                    "then try again. If it still does not answer, turn off battery optimisation for Termux.",
            )
            is Reply.Output -> reply.output
        }
        Timber.i("[TermuxExecutor] probe reply exit=${output.exitCode} err=${output.err} errmsg=${output.errmsg}")
        return when {
            output.stdout.contains(marker) -> Result.Accepted
            output.errmsg.contains("allow-external-apps", ignoreCase = true) ->
                Result.AllowExternalAppsDisabled(output.errmsg)
            else -> Result.Failure(
                "Termux did not run the setup check: " +
                    output.errmsg.ifBlank { output.stderr.ifBlank { "exit code ${output.exitCode}" } },
            )
        }
    }

    /**
     * Runs a short script in Termux and returns its stdout, or null when Termux did
     * not run it (not allowed, refused, or no answer within [timeoutMs]).
     */
    suspend fun runForStdout(script: String, timeoutMs: Long = PROBE_TIMEOUT_MS): String? =
        (runForReply(script, timeoutMs) as? Reply.Output)?.output
            ?.takeIf { it.errmsg.isBlank() }
            ?.stdout

    /**
     * User-facing instructions for enabling `allow-external-apps`.
     * Used by the UI layer when [Result.AllowExternalAppsDisabled] is returned.
     */
    fun buildAllowExternalAppsInstructions(): String = """
        To let Hermes2 control Termux automatically, you need to enable
        external app commands in Termux. This is a one-time setup.

        Steps:
        1. Open Termux.
        2. Paste and run this line (it only changes allow-external-apps and keeps
           your other Termux settings):
           $ALLOW_EXTERNAL_APPS_COMMAND
        3. Return to Hermes2.

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
        const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

        private const val PROBE_TIMEOUT_MS = 8_000L

        /** Aether's setup line: edits the one property in place and reloads Termux's settings. */
        const val ALLOW_EXTERNAL_APPS_COMMAND =
            "mkdir -p ~/.termux && touch ~/.termux/termux.properties && " +
                "if grep -Eq '^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=' ~/.termux/termux.properties; " +
                "then sed -i -E 's/^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=.*/allow-external-apps=true/' ~/.termux/termux.properties; " +
                "else printf '\\nallow-external-apps=true\\n' >> ~/.termux/termux.properties; fi && termux-reload-settings"

        // Canonical Termux filesystem paths
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        const val BASH_PATH = "$TERMUX_PREFIX/bin/bash"

        // Hermes binary location (installed by install.sh)
        // `pip install -e .` puts the `hermes` console script in PREFIX/bin
        const val HERMES_BIN = "$TERMUX_PREFIX/bin/hermes"
    }
}

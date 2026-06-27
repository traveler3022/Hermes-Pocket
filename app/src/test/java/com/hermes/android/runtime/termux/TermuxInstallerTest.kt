package com.hermes.android.runtime.termux

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TermuxInstaller] — pure JVM (no Robolectric).
 *
 * Verifies that the generated install script:
 * - Sets the correct receiver package name (so broadcast permissions match)
 * - Uses install.sh's `--stage --json` API (per ADR-007)
 * - Reports progress via `am broadcast` (per TermuxInstallProgressReceiver)
 * - Verifies hermes --version and hermes doctor
 * - Reports Python version (Fix S2F01)
 * - Sends COMPLETE broadcast on success and ERROR on failure
 */
class TermuxInstallerTest {

    private lateinit var context: Context
    private lateinit var installer: TermuxInstaller
    private val fakePackageName = "com.hermes.android.debug"

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.packageName } returns fakePackageName
        installer = TermuxInstaller(context)
    }

    @Test
    fun `generateInstallScript contains receiver package name`() {
        val script = installer.generateInstallScript()
        assertNotNull(script)
        assertTrue(
            "Script must reference the app package name as RECEIVER",
            script.contains("RECEIVER=\"$fakePackageName\""),
        )
    }

    @Test
    fun `generateInstallScript uses install_sh stage manifest API`() {
        val script = installer.generateInstallScript()
        assertTrue("Script must call install.sh --manifest", script.contains("--manifest"))
        assertTrue("Script must call install.sh --stage", script.contains("--stage"))
        assertTrue("Script must call install.sh --json", script.contains("--json"))
    }

    @Test
    fun `generateInstallScript reports progress via am broadcast`() {
        val script = installer.generateInstallScript()
        assertTrue(
            "Script must broadcast PROGRESS_ACTION",
            script.contains(TermuxInstaller.BroadcastAction.PROGRESS.action),
        )
        assertTrue(
            "Script must broadcast COMPLETE_ACTION",
            script.contains(TermuxInstaller.BroadcastAction.COMPLETE.action),
        )
        assertTrue(
            "Script must broadcast ERROR_ACTION",
            script.contains(TermuxInstaller.BroadcastAction.ERROR.action),
        )
    }

    @Test
    fun `generateInstallScript verifies hermes version and doctor`() {
        val script = installer.generateInstallScript()
        assertTrue("Script must run hermes --version", script.contains("hermes --version"))
        assertTrue("Script must run hermes doctor", script.contains("hermes doctor"))
    }

    @Test
    fun `generateInstallScript reports python version`() {
        val script = installer.generateInstallScript()
        // Fix S2F01: detect and broadcast python3 version
        assertTrue("Script must run python3 --version", script.contains("python3 --version"))
        assertTrue("Script must broadcast python_version stage", script.contains("python_version"))
    }

    @Test
    fun `generateInstallScript ends with report_complete`() {
        val script = installer.generateInstallScript()
        assertTrue("Script must end with report_complete", script.trim().endsWith("report_complete"))
    }

    @Test
    fun `generateInstallScript sets up error trap`() {
        val script = installer.generateInstallScript()
        // The trap calls report_error on ERR — this is how failures reach the app
        assertTrue("Script must set up ERR trap", script.contains("trap") && script.contains("ERR"))
        assertTrue("Trap must call report_error", script.contains("report_error"))
    }

    @Test
    fun `generateInstallScript recovers non git repo directory without precreating it`() {
        val script = installer.generateInstallScript()

        assertFalse(
            "Script must not pre-create repo dir before install_sh repository stage",
            script.contains("mkdir -p \"\$HOME/.hermes/hermes-agent\""),
        )
        assertTrue(
            "Script must move a non-git repo path aside before running repository stage",
            script.contains("BROKEN_REPO_DIR=\"\$REPO_DIR.broken-\$(date +%Y%m%d-%H%M%S)\""),
        )
        assertTrue(
            "Script must detect repo path without .git",
            script.contains("[ -e \"\$REPO_DIR\" ] && [ ! -d \"\$REPO_DIR/.git\" ]"),
        )
    }

    @Test
    fun `generateInstallScript handles Android psutil failures without deleting core dependency`() {
        val script = installer.generateInstallScript()

        assertTrue("Script must know the upstream psutil Android workaround", script.contains("install_psutil_android.py"))
        assertTrue("Script must detect Android psutil unsupported errors", script.contains("platform android is not supported"))
        assertTrue(
            "Script must run the psutil shim with the Hermes venv pip, not global python",
            script.contains("--pip \"\$PIP_PYTHON -m pip\""),
        )
        assertFalse(
            "Script must not delete psutil from pyproject.toml; psutil is a core runtime dependency",
            script.contains("psutil==7.2.2"),
        )
    }

    @Test
    fun `generateInstallScript follows upstream manifest and non interactive stages`() {
        val script = installer.generateInstallScript()

        assertTrue("Script must parse stage names from install_sh manifest", script.contains("s['name']"))
        assertTrue("Script must pass --non-interactive to stage runs", script.contains("--non-interactive"))
        assertFalse(
            "Script must not hard-code a partial stage list that skips setup and gateway accounting",
            script.contains("for STAGE in prerequisites repository venv python-deps node-deps path config complete"),
        )
    }

    @Test
    fun `generateInstallScript ensures dashboard web dependencies for Android websocket`() {
        val script = installer.generateInstallScript()

        assertTrue("Script must check dashboard dependencies", script.contains("dashboard WebSocket dependencies"))
        assertTrue("Script must install the web extra if upstream falls back to baseline termux", script.contains("pip install -e '.[web]' -c constraints-termux.txt"))
    }

    @Test
    fun `generateInstallScript prepares Termux Rust build env for jiter`() {
        val script = installer.generateInstallScript()

        assertTrue("Script must export ANDROID_API_LEVEL for maturin builds", script.contains("ANDROID_API_LEVEL"))
        assertTrue("Script must export CARGO_BUILD_TARGET for Termux Rust builds", script.contains("CARGO_BUILD_TARGET"))
        assertTrue("Script must install LLVM for maturin jiter builds", script.contains(" llvm lld"))
    }

    @Test
    fun `generateInstallScript fails verification when hermes command is missing`() {
        val script = installer.generateInstallScript()

        assertTrue("Script must not report success if hermes binary is missing", script.contains("Hermes command not found after install"))
        assertFalse(
            "Script must not hide a missing hermes command as installed",
            script.contains("--version 2>&1 || echo \"installed\""),
        )
    }

    @Test
    fun `generateInstallScript enables pipefail so stage failures are caught`() {
        val script = installer.generateInstallScript()

        assertTrue("Script must enable pipefail before tee pipelines", script.contains("set -o pipefail"))
    }

    @Test
    fun `generateInstallCommand is alias for generateInstallScript`() {
        // Legacy entry point must return the same script body
        val cmd = installer.generateInstallCommand()
        val script = installer.generateInstallScript()
        // Both must be non-empty and identical
        assertTrue(cmd.isNotEmpty())
        assertTrue(script.isNotEmpty())
        // They should produce the same output
        assertTrue(
            "generateInstallCommand must be an alias for generateInstallScript",
            cmd == script,
        )
    }

    @Test
    fun `generateInstallScript does not contain user-prompt language`() {
        // The script is now executed automatically via RUN_COMMAND.
        // Any "paste this into Termux" language would be misleading.
        val script = installer.generateInstallScript()
        assertFalse(
            "Script must NOT contain 'Paste into Termux' — it's auto-executed now",
            script.contains("Paste into Termux", ignoreCase = true),
        )
    }
}

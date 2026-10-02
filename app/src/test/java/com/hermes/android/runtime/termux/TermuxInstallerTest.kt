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
 * - Installs Hermes's signed Termux APT package (install.sh refuses Termux)
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
    fun `generateInstallScript installs the signed APT package not install_sh`() {
        val script = installer.generateInstallScript()
        assertTrue("Script must install the hermes-agent package", script.contains("apt_retry install hermes-agent"))
        assertTrue("Script must pin the repository key", script.contains("signed-by=\$KEYRING"))
        assertTrue("Script must check the documented key fingerprint", script.contains("C572B5FDD1A29CCFA9A912B6840B0848E139156D"))
        assertFalse("install.sh refuses Termux; the script must not run it", script.contains("hermes_install.sh"))
    }

    @Test
    fun `generateInstallScript falls back to canary while stable is unpublished`() {
        val script = installer.generateInstallScript()
        assertTrue(script.contains("hermes-stable/Release"))
        assertTrue(script.contains("CHANNEL=canary"))
        assertTrue(script.contains("hermes-\$CHANNEL main"))
    }

    @Test
    fun `generateInstallScript refuses Termux builds other than aarch64`() {
        val script = installer.generateInstallScript()
        assertTrue(script.contains("dpkg --print-architecture"))
        assertTrue(script.contains("[ \"\$TERMUX_ARCH\" != \"aarch64\" ]"))
    }

    @Test
    fun `generateInstallScript moves a foreign hermes launcher aside`() {
        // The package's postinst refuses to replace a launcher it does not own.
        val script = installer.generateInstallScript()
        assertTrue(script.contains("../lib/hermes-agent/bin/\$name"))
        assertTrue(script.contains("mv -f \"\$link\" \"\$link.old\""))
    }

    @Test
    fun `generateInstallScript never waits on a dpkg prompt`() {
        val script = installer.generateInstallScript()
        assertTrue(script.contains("DEBIAN_FRONTEND=noninteractive apt-get -y"))
        assertTrue(script.contains("--force-confold"))
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
        // The package brings its own Python; Termux's python3 may not be installed.
        assertTrue(script.contains("lib/hermes-agent/venv/bin/python\" --version"))
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

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

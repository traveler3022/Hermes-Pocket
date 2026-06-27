package com.hermes.android.runtime.termux

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for [TermuxCommandExecutor] that runs on a real Android
 * device (or emulator). Unlike [TermuxCommandExecutorTest] (pure JVM), this
 * test uses the real android.jar — so [Intent.getXxx] methods return actual
 * values, not stub nulls.
 *
 * This test verifies the FULL intent contract:
 * - action is `com.termux.RUN_COMMAND`
 * - component is `com.termux/.app.RunCommandService`
 * - extras match Termux source contract
 *
 * It does NOT require Termux to be installed — we mock the detector so it
 * reports Termux as present, then capture the intent before it is dispatched.
 *
 * Run with: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class TermuxCommandExecutorInstrumentedTest {

    private lateinit var context: Context
    private lateinit var detector: TermuxDetector
    private lateinit var executor: TermuxCommandExecutor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        detector = mockk(relaxed = true)
        executor = TermuxCommandExecutor(context, detector)
    }

    @Test
    fun execute_dispatches_correct_RUN_COMMAND_intent() {
        every { detector.detect() } returns TermuxDetector.Detection(
            termuxInstalled = true,
            termuxVersion = "1.0.0",
            termuxApiInstalled = false,
            termuxBootInstalled = false,
            diskFreeBytes = 1024L * 1024 * 1024,
        )

        // Capture the intent
        val fgSlot = slot<Intent>()
        val svcSlot = slot<Intent>()
        every { context.startForegroundService(capture(fgSlot)) } returns mockk(relaxed = true)
        every { context.startService(capture(svcSlot)) } returns mockk(relaxed = true)

        val script = "echo hello && hermes --version"
        val result = executor.executeBackgroundScript(
            script = script,
            workingDirectory = TermuxCommandExecutor.TERMUX_HOME,
        )

        assertTrue("Expected Accepted, got $result", result is TermuxCommandExecutor.Result.Accepted)

        val intent = if (fgSlot.isCaptured) fgSlot.captured else svcSlot.captured
        assertNotNull("Intent must have been captured", intent)

        // Verify action
        assertEquals(
            "Intent action must be com.termux.RUN_COMMAND",
            TermuxCommandExecutor.ACTION_RUN_COMMAND,
            intent.action,
        )

        // Verify component targets Termux's RunCommandService
        assertEquals(
            "Intent component package must be com.termux",
            TermuxCommandExecutor.TERMUX_PACKAGE,
            intent.component?.packageName,
        )
        assertEquals(
            "Intent component class must be RunCommandService",
            TermuxCommandExecutor.RUN_COMMAND_SERVICE_CLASS,
            intent.component?.className,
        )

        // Verify extras
        assertEquals(
            "EXTRA_COMMAND_PATH must be the Termux bash path",
            TermuxCommandExecutor.BASH_PATH,
            intent.getStringExtra(TermuxCommandExecutor.EXTRA_COMMAND_PATH),
        )

        val args = intent.getStringArrayExtra(TermuxCommandExecutor.EXTRA_ARGUMENTS)
        assertNotNull("EXTRA_ARGUMENTS must be set", args)
        assertEquals("bash -c gets two args: '-c' and the script", 2, args!!.size)
        assertEquals("-c", args[0])
        assertEquals(script, args[1])

        assertEquals(
            "EXTRA_WORKDIR must be Termux home",
            TermuxCommandExecutor.TERMUX_HOME,
            intent.getStringExtra(TermuxCommandExecutor.EXTRA_WORKDIR),
        )

        assertEquals(
            "EXTRA_BACKGROUND must be true for executeBackgroundScript",
            true,
            intent.getBooleanExtra(TermuxCommandExecutor.EXTRA_BACKGROUND, false),
        )
    }

    @Test
    fun executeBackgroundCommand_passes_argv_verbatim() {
        every { detector.detect() } returns TermuxDetector.Detection(
            termuxInstalled = true,
            termuxVersion = "1.0.0",
            termuxApiInstalled = false,
            termuxBootInstalled = false,
            diskFreeBytes = null,
        )
        val fgSlot = slot<Intent>()
        val svcSlot = slot<Intent>()
        every { context.startForegroundService(capture(fgSlot)) } returns mockk(relaxed = true)
        every { context.startService(capture(svcSlot)) } returns mockk(relaxed = true)

        executor.executeBackgroundCommand(
            executablePath = TermuxCommandExecutor.HERMES_BIN,
            arguments = arrayOf("gateway", "start"),
        )

        val intent = if (fgSlot.isCaptured) fgSlot.captured else svcSlot.captured
        assertEquals(
            "EXTRA_COMMAND_PATH must be the hermes binary",
            TermuxCommandExecutor.HERMES_BIN,
            intent.getStringExtra(TermuxCommandExecutor.EXTRA_COMMAND_PATH),
        )
        val args = intent.getStringArrayExtra(TermuxCommandExecutor.EXTRA_ARGUMENTS)
        assertEquals(
            "EXTRA_ARGUMENTS must be the argv verbatim",
            listOf("gateway", "start"),
            args?.toList(),
        )
    }
}

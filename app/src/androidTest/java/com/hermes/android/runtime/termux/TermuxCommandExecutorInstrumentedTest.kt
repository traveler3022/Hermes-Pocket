package com.hermes.android.runtime.termux

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android-runtime contract tests for the production RUN_COMMAND intent builder.
 *
 * These tests deliberately avoid MockK and Android service dispatch. The previous
 * fixture hung on the emulator while initializing Android mocks, before either test
 * completed. The production dispatch path is covered by JVM tests; these tests verify
 * real Android Intent getters against the same builder used by production.
 *
 * Run with: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class TermuxCommandExecutorInstrumentedTest {

    @Test
    fun executeBackgroundScript_builds_explicit_RUN_COMMAND_intent() {
        val script = "echo hello && hermes --version"
        val intent = TermuxCommandExecutor.buildRunCommandIntent(
            executablePath = TermuxCommandExecutor.BASH_PATH,
            arguments = arrayOf("-c", script),
            workingDirectory = TermuxCommandExecutor.TERMUX_HOME,
            background = true,
        )

        assertEquals(TermuxCommandExecutor.ACTION_RUN_COMMAND, intent.action)
        assertExplicitTermuxService(intent)
        assertEquals(
            TermuxCommandExecutor.BASH_PATH,
            intent.getStringExtra(TermuxCommandExecutor.EXTRA_COMMAND_PATH),
        )
        assertEquals(listOf("-c", script), intent.getStringArrayExtra(
            TermuxCommandExecutor.EXTRA_ARGUMENTS,
        )?.toList())
        assertEquals(
            TermuxCommandExecutor.TERMUX_HOME,
            intent.getStringExtra(TermuxCommandExecutor.EXTRA_WORKDIR),
        )
        assertTrue(intent.getBooleanExtra(TermuxCommandExecutor.EXTRA_BACKGROUND, false))
    }

    @Test
    fun executeBackgroundCommand_preserves_argv_verbatim() {
        val argv = arrayOf("gateway", "start", "--label", "a value with spaces")
        val intent = TermuxCommandExecutor.buildRunCommandIntent(
            executablePath = TermuxCommandExecutor.HERMES_BIN,
            arguments = argv,
            workingDirectory = TermuxCommandExecutor.TERMUX_HOME,
            background = true,
        )

        assertEquals(TermuxCommandExecutor.ACTION_RUN_COMMAND, intent.action)
        assertExplicitTermuxService(intent)
        assertEquals(
            TermuxCommandExecutor.HERMES_BIN,
            intent.getStringExtra(TermuxCommandExecutor.EXTRA_COMMAND_PATH),
        )
        assertEquals(
            argv.toList(),
            intent.getStringArrayExtra(TermuxCommandExecutor.EXTRA_ARGUMENTS)?.toList(),
        )
    }

    private fun assertExplicitTermuxService(intent: Intent) {
        assertNotNull("Service intent must be explicit", intent.component)
        assertEquals(TermuxCommandExecutor.TERMUX_PACKAGE, intent.component?.packageName)
        assertEquals(
            TermuxCommandExecutor.RUN_COMMAND_SERVICE_CLASS,
            intent.component?.className,
        )
    }
}

package com.hermes.android.runtime.linux

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * proot field test on a real Android kernel, inside the app's own process (its uid and SELinux
 * domain): installs the built-in Linux with the app's installer, adds packages the way the app
 * does, then runs the stress script from androidTest assets. Results go to logcat, tag PFT.
 *
 * Run with: ./gradlew :app:connectedDebugAndroidTest -Phermes.x86_64=true
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.hermes.android.runtime.linux.ProotFieldTest
 */
@RunWith(AndroidJUnit4::class)
class ProotFieldTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private lateinit var bridge: GuestApkBridge
    private val environment = ProotEnvironment(context, dagger.Lazy { bridge })

    init {
        bridge = GuestApkBridge(environment)
    }

    @Test
    fun prootUnderLoad() = runBlocking {
        assertTrue("proot is not in this build", environment.isSupportedDevice)
        log("abi=${environment.abi}")

        var start = System.currentTimeMillis()
        RootfsInstaller(context, environment).install { log(it) }
        log("RESULT rootfs-install time=${seconds(start)}s")

        start = System.currentTimeMillis()
        val packages = environment.run(
            "apk update -q && apk add -q --no-chown python3 git nodejs npm curl",
            forApk = true,
        ) { log(it) }
        log("RESULT packages rc=${packages.exitCode} time=${seconds(start)}s")
        assertTrue("package install failed:\n${packages.output}", packages.ok)

        copyScript("proot-fieldtest-apk.sh")
        log("-- apk with link2symlink (app installs)")
        environment.run("sh /tmp/proot-fieldtest-apk.sh", forApk = true) { log(it) }
        log("-- apk without link2symlink (agent in chat)")
        environment.run("sh /tmp/proot-fieldtest-apk.sh") { log(it) }

        // apk the way the agent and the terminal run it: GuestApkBridge hands it to the app.
        copyScript("proot-fieldtest-bridge.sh")
        log("== bridge")
        val bridgeRun = environment.run("sh /tmp/proot-fieldtest-bridge.sh") { log(it) }
        val restarted = environment.run("apk add -q jq && apk info -e jq && apk del -q jq") { log(it) }
        log("RESULT bridge-back-after-no-answer rc=${restarted.exitCode}")
        // What the bridge costs: the same add and del, direct (with link2symlink) and through it.
        for (direct in listOf(true, false)) {
            start = System.currentTimeMillis()
            val timed = environment.run("apk add -q jq && apk del -q jq", forApk = direct) { log(it) }
            log("RESULT apk-add-del ${if (direct) "direct" else "bridge"} rc=${timed.exitCode} ms=${System.currentTimeMillis() - start}")
        }

        copyScript("proot-fieldtest.sh")
        log("== suite")
        start = System.currentTimeMillis()
        val result = environment.run("sh /tmp/proot-fieldtest.sh") { log(it) }
        log("RESULT suite rc=${result.exitCode} time=${seconds(start)}s")
        assertTrue("bridge script did not finish", bridgeRun.output.contains("BRIDGE-DONE"))
        assertTrue("field test script did not finish", result.output.contains("DONE"))
    }

    private fun copyScript(name: String) {
        val target = environment.guestFile("/tmp/$name")
        testAssets.open(name).use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    private fun seconds(start: Long) = (System.currentTimeMillis() - start) / 1000

    private fun log(line: String) {
        Log.i(TAG, line)
    }

    private companion object {
        const val TAG = "PFT"
    }
}

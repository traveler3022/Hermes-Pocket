package com.hermes.android.runtime.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** What Termux's RunCommandService sent back for one command. */
data class TermuxCommandOutput(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    /** Termux's own error code; -1 (RESULT_OK) when the command ran. */
    val err: Int,
    /** Why Termux refused or failed to run the command, e.g. allow-external-apps is off. */
    val errmsg: String,
)

/** Commands waiting for Termux's reply, keyed by the id carried in their result PendingIntent. */
@Singleton
class TermuxPendingResults @Inject constructor() {
    private val nextId = AtomicInteger(1000)
    private val pending = HashMap<Int, CompletableDeferred<TermuxCommandOutput>>()

    fun register(): Pair<Int, CompletableDeferred<TermuxCommandOutput>> {
        val id = nextId.incrementAndGet()
        val deferred = CompletableDeferred<TermuxCommandOutput>()
        synchronized(pending) { pending[id] = deferred }
        return id to deferred
    }

    fun complete(id: Int, output: TermuxCommandOutput) {
        synchronized(pending) { pending.remove(id) }?.complete(output)
    }

    fun remove(id: Int) {
        synchronized(pending) { pending.remove(id) }
    }
}

/**
 * Receives the result bundle Termux fills into the PendingIntent passed with RUN_COMMAND
 * (same contract Aether uses). Not exported: the PendingIntent is sent as this app.
 */
@AndroidEntryPoint
class TermuxResultReceiver : BroadcastReceiver() {

    @Inject
    lateinit var results: TermuxPendingResults

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_EXECUTION_ID, -1)
        if (id < 0) return
        val bundle = intent.extras?.getBundle(RESULT_BUNDLE) ?: intent.extras?.firstBundle()
        results.complete(
            id,
            TermuxCommandOutput(
                stdout = bundle?.getString("stdout").orEmpty(),
                stderr = bundle?.getString("stderr").orEmpty(),
                exitCode = bundle?.getInt("exitCode", -1) ?: -1,
                err = bundle?.getInt("err", -1) ?: -1,
                errmsg = bundle?.getString("errmsg").orEmpty(),
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun Bundle.firstBundle(): Bundle? = keySet().firstNotNullOfOrNull { get(it) as? Bundle }

    companion object {
        const val EXTRA_EXECUTION_ID = "com.hermes.android.termux.EXECUTION_ID"

        /** TermuxConstants.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE */
        private const val RESULT_BUNDLE = "result"
    }
}

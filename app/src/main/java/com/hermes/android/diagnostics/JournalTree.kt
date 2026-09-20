package com.hermes.android.diagnostics

import timber.log.Timber

/**
 * Funnels the connection story Timber already tells into [ConnectionJournal].
 *
 * Every line worth keeping is written somewhere already — `[Gateway] disconnected: …`,
 * `[Gateway] reconnect attempt 3 in 4000ms`, `[Stdio] Gateway process exited (137)`.
 * Rather than duplicating a call at each of those sites, this reads them where they are.
 * A tree cannot miss a site somebody forgets to instrument later, and it keeps the
 * diagnosis out of the code being diagnosed.
 *
 * It is deliberately picky. Everything at warning or above is kept whatever it says,
 * because that is where failures live. Below that only the subsystems that can explain a
 * dropped socket are kept, so a chatty UI does not push the one line that mattered out
 * of a capped file.
 *
 * Some lines earn a system snapshot after them: those are the moments where the
 * difference between "the phone slept", "the network moved" and "the agent died" is the
 * whole answer, and none of it is visible in the message itself.
 */
class JournalTree(private val journal: ConnectionJournal) : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val keep = priority >= WARN || TRACKED.any { message.startsWith(it) }
        if (!keep) return

        val level = when (priority) {
            WARN -> "W"
            ERROR -> "E"
            else -> "I"
        }
        val cause = t?.let { " <- ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        journal.note("$level $message$cause")

        if (NEEDS_CONTEXT.any { message.startsWith(it) }) {
            journal.noteContext(message.substringBefore(':').take(60))
        }
    }

    private companion object {
        // android.util.Log levels as literals: Phase 1.5 Rule 8 keeps android.util.Log
        // out of the codebase, and a tree that imported it would be the one exception
        // everybody copies.
        private const val INFO = 4
        private const val WARN = 5
        private const val ERROR = 6

        /** Subsystems whose ordinary chatter is still evidence about the connection. */
        private val TRACKED = listOf(
            "[Gateway]",
            "[Stdio]",
            "[GatewayService]",
            "[Runtime]",
            "[Linux]",
            "[App]",
            "[Chat] Failed",
        )

        /**
         * Lines where the operating system's state at that instant is the diagnosis:
         * the socket going down, the agent process dying, and the service being
         * (re)started or torn down around it.
         */
        private val NEEDS_CONTEXT = listOf(
            "[Gateway] disconnected",
            "[Gateway] WebSocket failure",
            "[Gateway] network available",
            "[Stdio] Gateway process exited",
            "[Stdio] Gateway stdout closed",
            "[GatewayService] onStartCommand",
            "[GatewayService] onDestroy",
        )
    }
}

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
        // Timber has already appended the full stack trace to [message]; take it back off.
        val head = message.substringBefore("\n" + (t?.javaClass?.name ?: "\u0000"))
            .let { if (it.length > MAX_HEAD) it.take(MAX_HEAD) + "… (${it.length} chars)" else it }
        if (t == null) {
            journal.note("$level $head")
        } else {
            val cause = "${t.javaClass.simpleName}: ${t.message}"
            // The same failure from the same place is one fact, however many screens hit it:
            // the first time is written in full, the repeats as one line that points back.
            val signature = "${head.substringBefore(':').take(80)}|${t.javaClass.name}|${t.message?.take(120)}|" +
                t.stackTrace.firstOrNull { it.className.startsWith(APP_PACKAGE) }
            val seen = synchronized(repeats) {
                val n = (repeats[signature] ?: 0) + 1
                repeats[signature] = n
                n
            }
            if (seen == 1) {
                journal.note("$level $head\n${compactStack(t)}\n <- $cause")
            } else {
                journal.note("$level $head <- $cause  (repeat #$seen, stack above)")
            }
        }

        if (NEEDS_CONTEXT.any { message.startsWith(it) }) {
            journal.noteContext(message.substringBefore(':').take(60))
        }
    }

    /** First-seen failures of this process, by signature; bounded so it cannot grow forever. */
    private val repeats = object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 256
    }

    /**
     * The frames that explain a failure: the app's own, plus the few above them that
     * say which library threw. Android's looper and zygote frames under every stack
     * are dropped — they were most of each trace and never the answer.
     */
    private fun compactStack(t: Throwable): String = buildString {
        var cause: Throwable? = t
        var depth = 0
        while (cause != null && depth < 4) {
            if (depth > 0) append("\nCaused by: ${cause.javaClass.name}: ${cause.message}")
            else append("${cause.javaClass.name}: ${cause.message}")
            val frames = cause.stackTrace
            val firstApp = frames.indexOfFirst { it.className.startsWith(APP_PACKAGE) }
            var kept = 0
            var skipped = 0
            frames.forEachIndexed { i, frame ->
                val ours = frame.className.startsWith(APP_PACKAGE)
                val leading = firstApp >= 0 && i < firstApp && i >= firstApp - 3
                val noAppFrames = firstApp < 0 && i < 8
                if ((ours || leading || noAppFrames) && kept < MAX_FRAMES) {
                    append("\n\tat ").append(frame)
                    kept++
                } else {
                    skipped++
                }
            }
            if (skipped > 0) append("\n\t… $skipped framework frames")
            val next: Throwable? = cause.cause
            cause = if (next === cause) null else next
            depth++
        }
    }

    private companion object {
        private const val APP_PACKAGE = "com.hermes."
        private const val MAX_FRAMES = 14
        /** Long enough for a sentence, short enough that one JSON frame cannot fill a screen. */
        private const val MAX_HEAD = 400

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
            "[Turn]",
            "[Lifecycle]",
            "[Memory]",
            "[Crash]",
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

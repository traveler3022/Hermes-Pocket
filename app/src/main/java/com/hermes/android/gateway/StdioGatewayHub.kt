package com.hermes.android.gateway

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the gateway client to a `python -m tui_gateway.entry` process that speaks
 * newline-delimited JSON-RPC over stdin/stdout — the transport Hermes' own TUI uses, and
 * the way Aether talks to its bundled agent (`node bridge.mjs`). No port, token, or
 * web server: the built-in Linux runtime owns the process, so there is nothing to dial.
 *
 * The runtime [attach]es each gateway process; [open] hands the client a [WebSocket]
 * view of it, so every protocol path in [OkHttpGatewayClient] stays the same. Closing
 * that view only detaches the client — the process (and its sessions) keep running.
 */
@Singleton
class StdioGatewayHub @Inject constructor() {

    /** Called when a client opens the transport but no gateway process is running. */
    @Volatile
    var restartHandler: (() -> Unit)? = null

    /** Rootfs directory whose files [readFile] may serve (agent-produced images and downloads). */
    @Volatile
    var fileRoot: java.io.File? = null

    private val lock = Any()
    private val delivery = Executors.newSingleThreadExecutor { Thread(it, "hermes-stdio-gateway").apply { isDaemon = true } }

    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var readyLine: String? = null
    private var readyWaiter = CompletableDeferred<Boolean>()
    private var current: StdioSocket? = null

    val isReady: Boolean get() = synchronized(lock) { process?.isAlive == true && readyLine != null }

    /** Takes over [newProcess]'s stdout; stderr must already be redirected by the caller. */
    fun attach(newProcess: Process, onExit: (Int) -> Unit) {
        synchronized(lock) {
            process = newProcess
            stdin = newProcess.outputStream
            readyLine = null
            if (readyWaiter.isCompleted) readyWaiter = CompletableDeferred()
        }
        Thread({ pump(newProcess, onExit) }, "hermes-stdio-reader").apply { isDaemon = true }.start()
    }

    /** Suspends until the attached process emits `gateway.ready`; false on timeout or exit. */
    suspend fun awaitReady(timeoutMs: Long): Boolean {
        val waiter = synchronized(lock) { if (readyLine != null) return true else readyWaiter }
        return withTimeoutOrNull(timeoutMs) { waiter.await() } ?: false
    }

    /** Forgets the current process (the runtime is stopping it); an attached client reconnects. */
    fun detach() {
        val target = synchronized(lock) {
            process = null
            stdin = null
            readyLine = null
            readyWaiter.complete(false)
            current.also { current = null }
        }
        if (target != null) {
            delivery.execute { target.listener.onFailure(target, IOException("Hermes gateway stopped"), null) }
        }
    }

    /** Reads the `file:` [url] for a download, only if it lies inside [fileRoot]. */
    fun readFile(url: String): ByteArray {
        val root = fileRoot?.canonicalFile ?: throw IOException("No local runtime files available")
        val file = java.io.File(java.net.URI(url)).canonicalFile
        if (!file.path.startsWith(root.path + java.io.File.separator)) throw IOException("File is outside the runtime")
        return file.readBytes()
    }

    /** Returns a socket that stays silent until [activate] — the client must register it first. */
    fun open(listener: WebSocketListener): WebSocket = StdioSocket(listener)

    /** Starts delivering frames to [socket]: replays `gateway.ready` if the process is already up. */
    fun activate(socket: WebSocket) {
        val stdio = socket as? StdioSocket ?: return
        val (ready, alive) = synchronized(lock) {
            current = stdio
            readyLine to (process?.isAlive == true)
        }
        if (!alive) {
            Timber.i("[Stdio] No gateway process — asking the runtime to start one")
            restartHandler?.invoke()
            return
        }
        delivery.execute {
            stdio.listener.onOpen(stdio, stdio.response)
            if (ready != null) stdio.listener.onMessage(stdio, ready)
        }
    }

    private fun pump(source: Process, onExit: (Int) -> Unit) {
        try {
            source.inputStream.bufferedReader().forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val target = synchronized(lock) {
                    if (process !== source) return@forEachLine
                    if (line.contains("\"gateway.ready\"")) {
                        readyLine = line
                        readyWaiter.complete(true)
                    }
                    current
                }
                if (target != null) {
                    delivery.execute {
                        if (line.contains("\"gateway.ready\"")) target.listener.onOpen(target, target.response)
                        target.listener.onMessage(target, line)
                    }
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "[Stdio] Gateway stdout closed")
        }
        val exitCode = runCatching { source.waitFor() }.getOrDefault(-1)
        val target = synchronized(lock) {
            if (process !== source) return
            process = null
            stdin = null
            readyLine = null
            readyWaiter.complete(false)
            current
        }
        Timber.w("[Stdio] Gateway process exited ($exitCode)")
        onExit(exitCode)
        if (target != null) {
            delivery.execute {
                target.listener.onFailure(target, IOException("Hermes gateway process exited ($exitCode)"), null)
            }
        }
    }

    private fun write(text: String): Boolean {
        val out = synchronized(lock) { stdin } ?: return false
        return try {
            synchronized(out) {
                out.write((text + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (e: IOException) {
            Timber.w(e, "[Stdio] Write to gateway failed")
            false
        }
    }

    private inner class StdioSocket(val listener: WebSocketListener) : WebSocket {
        private val request = Request.Builder().url(PLACEHOLDER_HTTP_URL).build()
        val response: Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(101)
            .message("stdio")
            .build()

        override fun request(): Request = request
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean = synchronized(lock) { current === this } && write(text)
        override fun send(bytes: ByteString): Boolean = send(bytes.utf8())

        override fun close(code: Int, reason: String?): Boolean {
            val wasCurrent = synchronized(lock) { (current === this).also { if (it) current = null } }
            if (wasCurrent) delivery.execute { listener.onClosed(this, code, reason.orEmpty()) }
            return wasCurrent
        }

        override fun cancel() {
            close(1001, "cancelled")
        }
    }

    companion object {
        /** URL the built-in runtime reports; [OkHttpGatewayClient] routes it here. */
        const val URL = "stdio://hermes-linux"
        private const val PLACEHOLDER_HTTP_URL = "http://127.0.0.1/stdio"

        fun handles(url: String): Boolean = url.startsWith("stdio://")
    }
}

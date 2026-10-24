package com.hermes.android.gateway

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket

/**
 * The phone's own `http://127.0.0.1:<port>/callback` for an OAuth redirect. A remote gateway's
 * loopback sits on the server, where the phone's browser can never reach it, so the app takes
 * the redirect itself and relays code/state over RPC (`client_redirect_uri` +
 * `mcp.servers.oauth.callback`, as Hermes desktop does with a remote backend).
 */
class OAuthLoopbackReceiver : Closeable {

    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))

    val redirectUri: String = "http://127.0.0.1:${socket.localPort}/callback"

    /** Waits for the browser to hit /callback and returns its query parameters. */
    suspend fun await(): Map<String, String> = withContext(Dispatchers.IO) {
        var params: Map<String, String>? = null
        while (params == null) {
            socket.accept().use { client ->
                client.soTimeout = 10_000
                val requestLine = client.getInputStream().bufferedReader().readLine().orEmpty()
                val target = requestLine.split(' ').getOrNull(1).orEmpty()
                val uri = Uri.parse("http://127.0.0.1$target")
                val isCallback = requestLine.startsWith("GET ") && uri.path == "/callback"
                val body = if (isCallback) DONE_PAGE else "Not found"
                client.getOutputStream().apply {
                    val bytes = body.toByteArray()
                    write((
                        "HTTP/1.1 ${if (isCallback) "200 OK" else "404 Not Found"}\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                        ).toByteArray())
                    write(bytes)
                    flush()
                }
                if (isCallback) {
                    params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
                }
            }
        }
        checkNotNull(params)
    }

    /** Also unblocks a pending [await]. */
    override fun close() {
        runCatching { socket.close() }
    }

    private companion object {
        const val DONE_PAGE = "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>" +
            "<body style=\"font-family:sans-serif;text-align:center;padding:48px 16px\">" +
            "<h2>Signed in</h2><p>You can return to Hermes Pocket.</p>" +
            "<h2 dir=\"rtl\">ورود انجام شد</h2><p dir=\"rtl\">به Hermes Pocket برگردید.</p></body></html>"
    }
}

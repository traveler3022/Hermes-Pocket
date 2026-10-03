package com.hermes.android.runtime.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What the server's public `/api/status` says about signing in. */
data class ServerAuthInfo(
    val version: String?,
    val authRequired: Boolean,
    /** The server offers app sign-in (system browser + PKCE). Older servers don't. */
    val nativeSignIn: Boolean,
)

/** One sign-in in flight: the page to open in the browser, and the listener its redirect comes back to. */
class PendingSignIn internal constructor(
    val serverUrl: String,
    val authorizeUrl: String,
    internal val listener: ServerSocket,
    internal val verifier: String,
    internal val state: String,
) : AutoCloseable {
    override fun close() {
        runCatching { listener.close() }
    }
}

/**
 * Signs the app in to a Hermes server the way the server asks native apps to (RFC 8252): the
 * server's own login page opens in the browser, the result comes back to a one-shot listener on
 * 127.0.0.1, and the app trades it for tokens. The password is typed into the server's page, never
 * into the app.
 *
 * Every WebSocket connection then gets its own ticket ([mintTicketBlocking]): single-use and dead
 * after 30 s, so the socket URL carries nothing that works twice.
 */
@Singleton
class RemoteAuth @Inject constructor(
    private val store: RemoteTokenStore,
) {
    // Its own client: the shared one runs RemoteAuthInterceptor, which calls back in here.
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val refreshLock = Any()

    private val _session = MutableStateFlow(store.load())

    /** The current sign-in, or null. */
    val session: StateFlow<RemoteTokens?> = _session.asStateFlow()

    /** True when the stored sign-in belongs to the server at [serverUrl]. */
    fun isSignedInTo(serverUrl: String): Boolean {
        val base = remoteHttpBase(serverUrl) ?: return false
        return _session.value?.serverUrl == base
    }

    /** The signed-in tokens when [url] points at the server they belong to, else null. */
    fun tokensFor(url: HttpUrl): RemoteTokens? {
        val tokens = _session.value ?: return null
        val base = tokens.serverUrl.toHttpUrlOrNull() ?: return null
        val sameOrigin = url.scheme == base.scheme && url.host == base.host && url.port == base.port
        return tokens.takeIf { sameOrigin && url.encodedPath.startsWith(base.encodedPath.trimEnd('/')) }
    }

    /** Reads the server's public `/api/status`. Throws [IOException] when it can't be reached. */
    suspend fun probe(serverUrl: String): ServerAuthInfo = withContext(Dispatchers.IO) {
        val base = remoteHttpBase(serverUrl) ?: throw IOException("Not a server address: $serverUrl")
        val request = Request.Builder().url("$base/api/status").header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The server answered HTTP ${response.code}")
            val o = runCatching { Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject }.getOrNull()
                ?: throw IOException("That address answered, but not like a Hermes server")
            val flows = (o["auth_flows"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
            ServerAuthInfo(
                version = (o["version"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                authRequired = (o["auth_required"] as? JsonPrimitive)?.booleanOrNull ?: false,
                nativeSignIn = "native_pkce" in flows,
            )
        }
    }

    /** Opens the one-shot listener on 127.0.0.1 and builds the server's sign-in page URL for the browser. */
    suspend fun beginSignIn(serverUrl: String): PendingSignIn = withContext(Dispatchers.IO) {
        val base = remoteHttpBase(serverUrl) ?: throw IOException("Not a server address: $serverUrl")
        val listener = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 4)
        }
        val verifier = randomToken(32)
        val state = randomToken(16)
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val authorizeUrl = "$base/auth/native/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("redirect_uri", "http://127.0.0.1:${listener.localPort}/callback")
            .addQueryParameter("state", state)
            .build()
            .toString()
        PendingSignIn(base, authorizeUrl, listener, verifier, state)
    }

    /**
     * Waits for the browser to come back with the one-time code, then trades it for tokens and
     * keeps them. Throws on timeout, on a refusal from the server, or when the coroutine is cancelled.
     */
    suspend fun completeSignIn(pending: PendingSignIn, timeoutMs: Long = SIGN_IN_TIMEOUT_MS): RemoteTokens =
        withContext(Dispatchers.IO) {
            pending.use {
                val code = awaitCode(pending, timeoutMs)
                exchange(pending.serverUrl, code, pending.verifier)
            }
        }

    /** Forgets the sign-in on this phone. */
    fun signOut() {
        store.clear()
        _session.value = null
    }

    /** An access token for [tokens]' server, refreshed first when it is about to lapse. */
    fun accessTokenBlocking(tokens: RemoteTokens): String {
        val now = System.currentTimeMillis() / 1000
        if (tokens.expiresAt > 0 && now >= tokens.expiresAt - EXPIRY_SKEW_S) {
            refreshBlocking(tokens.accessToken)?.let { return it.accessToken }
        }
        return tokens.accessToken
    }

    /**
     * Trades the refresh token for a new pair. [stale] is the access token the caller saw fail; when
     * another caller already replaced it, that newer pair is returned instead of refreshing twice.
     * Null when the sign-in is over (the server said so, and it is dropped here) or the server is unreachable.
     */
    fun refreshBlocking(stale: String): RemoteTokens? {
        synchronized(refreshLock) {
            val current = _session.value ?: return null
            if (current.accessToken != stale) return current
            if (current.refreshToken.isBlank()) return null
            val body = buildJsonObject {
                put("refresh_token", current.refreshToken)
                put("provider", current.provider)
            }.toString().toRequestBody(JSON)
            val request = Request.Builder().url("${current.serverUrl}/auth/native/refresh").post(body).build()
            return try {
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> parseTokens(current.serverUrl, text)?.also { keep(it) }
                        response.code == 401 -> {
                            Timber.w("[RemoteAuth] The server ended the sign-in")
                            signOut()
                            null
                        }
                        else -> null
                    }
                }
            } catch (e: IOException) {
                Timber.w(e, "[RemoteAuth] Refresh failed")
                null
            }
        }
    }

    /**
     * A fresh ticket for ONE WebSocket connection to [tokens]' server: single-use, valid 30 s.
     * Null when the server won't issue one (signed out, unreachable).
     */
    fun mintTicketBlocking(tokens: RemoteTokens): String? {
        var access = accessTokenBlocking(tokens)
        repeat(2) { attempt ->
            val request = Request.Builder()
                .url("${tokens.serverUrl}/api/auth/ws-ticket")
                .header("Authorization", "Bearer $access")
                .post("{}".toRequestBody(JSON))
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val o = runCatching { Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject }.getOrNull()
                        return (o?.get("ticket") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    }
                    if (response.code != 401 || attempt > 0) {
                        Timber.w("[RemoteAuth] No connection ticket: HTTP ${response.code}")
                        return null
                    }
                }
            } catch (e: IOException) {
                Timber.w(e, "[RemoteAuth] Could not get a connection ticket")
                return null
            }
            access = refreshBlocking(access)?.accessToken ?: return null
        }
        return null
    }

    private suspend fun awaitCode(pending: PendingSignIn, timeoutMs: Long): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        pending.listener.soTimeout = ACCEPT_POLL_MS
        var code: String? = null
        while (code == null) {
            currentCoroutineContext().ensureActive()
            if (System.currentTimeMillis() > deadline) throw IOException("Sign-in timed out")
            val socket = try {
                pending.listener.accept()
            } catch (e: SocketTimeoutException) {
                null
            }
            if (socket != null) code = socket.use { readCallback(it, pending) }
        }
        return checkNotNull(code)
    }

    /** The code from one request on the listener, or null when it isn't the redirect this sign-in waits for. */
    private fun readCallback(socket: Socket, pending: PendingSignIn): String? {
        socket.soTimeout = 5_000
        val requestLine = socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine().orEmpty()
        val target = requestLine.split(' ').getOrNull(1).orEmpty()
        val url = "http://127.0.0.1$target".toHttpUrlOrNull()
        val out = socket.getOutputStream()
        if (url == null || url.encodedPath != "/callback") {
            respond(out, 404, PAGE_FAILED)
            return null
        }
        // Only the browser this sign-in opened knows the state; anything else is ignored.
        if (url.queryParameter("state") != pending.state) {
            respond(out, 400, PAGE_FAILED)
            return null
        }
        url.queryParameter("error")?.let { error ->
            respond(out, 400, PAGE_FAILED)
            throw IOException("The server refused the sign-in: ${url.queryParameter("error_description") ?: error}")
        }
        val code = url.queryParameter("code")
        if (code.isNullOrBlank()) {
            respond(out, 400, PAGE_FAILED)
            throw IOException("The server sent no sign-in code")
        }
        respond(out, 200, PAGE_DONE)
        return code
    }

    private fun exchange(base: String, code: String, verifier: String): RemoteTokens {
        val body = buildJsonObject {
            put("code", code)
            put("code_verifier", verifier)
        }.toString().toRequestBody(JSON)
        val request = Request.Builder().url("$base/auth/native/token").post(body).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("The server refused the sign-in code (HTTP ${response.code})")
            val tokens = parseTokens(base, text) ?: throw IOException("The server's sign-in answer was not understood")
            keep(tokens)
            Timber.i("[RemoteAuth] Signed in to $base as ${tokens.userId} (${tokens.provider})")
            return tokens
        }
    }

    private fun keep(tokens: RemoteTokens) {
        store.save(tokens)
        _session.value = tokens
    }

    private fun parseTokens(base: String, text: String): RemoteTokens? {
        val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val access = o.str("access_token").ifEmpty { return null }
        val expires = o["expires_at"] as? JsonPrimitive
        return RemoteTokens(
            serverUrl = base,
            accessToken = access,
            refreshToken = o.str("refresh_token"),
            expiresAt = expires?.longOrNull ?: expires?.doubleOrNull?.toLong() ?: 0L,
            provider = o.str("provider"),
            userId = o.str("user_id"),
        )
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    private fun respond(out: OutputStream, status: Int, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            404 -> "Not Found"
            else -> "Bad Request"
        }
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(body)
        out.flush()
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val SIGN_IN_TIMEOUT_MS = 5 * 60_000L
        const val ACCEPT_POLL_MS = 1_000
        const val EXPIRY_SKEW_S = 60L

        const val PAGE_DONE = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Hermes</title></head><body style="font-family:sans-serif;text-align:center;padding:3em 1em"><h2>&#10003; Signed in</h2><p>You can go back to the Hermes app.</p><p dir="rtl">وارد شدید. به اپ هرمس برگردید.</p></body></html>"""
        const val PAGE_FAILED = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Hermes</title></head><body style="font-family:sans-serif;text-align:center;padding:3em 1em"><h2>Sign-in did not finish</h2><p>Go back to the Hermes app and try again.</p><p dir="rtl">ورود کامل نشد. به اپ هرمس برگردید و دوباره امتحان کنید.</p></body></html>"""
    }
}

/**
 * The server's HTTP base from whatever was typed: `https://host:port`, a `wss://` gateway URL,
 * with or without `/api/ws` or a trailing slash. No scheme means `http://`. Null when it isn't a URL.
 */
internal fun remoteHttpBase(input: String): String? {
    var s = input.trim()
    if (s.isEmpty()) return null
    if (!s.contains("://")) s = "http://$s"
    s = when {
        s.startsWith("wss://", ignoreCase = true) -> "https://" + s.substring(6)
        s.startsWith("ws://", ignoreCase = true) -> "http://" + s.substring(5)
        else -> s
    }
    val url = s.toHttpUrlOrNull() ?: return null
    val path = url.encodedPath.removeSuffix("/").removeSuffix("/api/ws").removeSuffix("/")
    return url.newBuilder()
        .encodedPath(path.ifEmpty { "/" })
        .query(null)
        .fragment(null)
        .build()
        .toString()
        .removeSuffix("/")
}

/** The gateway socket of the server at [httpBase]: same origin, `ws(s)://…/api/ws`. */
internal fun remoteWebSocketUrl(httpBase: String): String =
    httpBase.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/ws"

private val secureRandom = SecureRandom()

private fun randomToken(bytes: Int): String = b64url(ByteArray(bytes).also { secureRandom.nextBytes(it) })

private fun b64url(bytes: ByteArray): String =
    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

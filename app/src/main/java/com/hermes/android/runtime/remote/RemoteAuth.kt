package com.hermes.android.runtime.remote

import kotlinx.coroutines.Dispatchers
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
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What the server's public `/api/status` says about signing in. */
data class ServerAuthInfo(
    val version: String?,
    val authRequired: Boolean,
    /** The server offers app sign-in (PKCE code for native apps). Older servers don't. */
    val nativeSignIn: Boolean,
)

/**
 * Signs the app in to a Hermes server with the Hermes username and password typed into the app,
 * through the server's own native-app flow: `/auth/native/authorize` (PKCE), then
 * `/auth/password-login`, whose answer carries a one-time code the app trades at
 * `/auth/native/token`. No browser is involved, so it works over the in-app Tailscale tunnel that a
 * browser can't use. The login is kept encrypted ([RemoteTokenStore]) so that when the server ends
 * the sign-in the app signs in again by itself instead of asking.
 *
 * Every WebSocket connection then gets its own ticket ([mintTicketBlocking]): single-use and dead
 * after 30 s, so the socket URL carries nothing that works twice.
 */
@Singleton
class RemoteAuth @Inject constructor(
    private val store: RemoteTokenStore,
    private val tailnet: TailnetRoute,
) {
    // Its own client: the shared one runs RemoteAuthInterceptor, which calls back in here.
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .proxySelector(tailnet.proxySelector)
        .proxyAuthenticator(tailnet.proxyAuthenticator)
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

    /**
     * Reads the server's public `/api/status`, bringing the Tailscale tunnel up first for a tailnet
     * address. Throws [IOException] with a reason the user can act on when it can't be reached.
     */
    suspend fun probe(serverUrl: String): ServerAuthInfo {
        val base = encryptedBase(serverUrl)
        tailnet.ensureUp(base.toHttpUrl().host)
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url("$base/api/status").header("Accept", "application/json").build()
            val call = try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw reachError(base, e)
            }
            call.use { response ->
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
    }

    /**
     * Signs in to the server at [serverUrl] with its Hermes [username] and [password], keeps the
     * tokens and the login, and returns the tokens. Throws [IOException] with the reason on failure.
     */
    suspend fun signInWithPassword(serverUrl: String, username: String, password: String): RemoteTokens {
        val base = encryptedBase(serverUrl)
        tailnet.ensureUp(base.toHttpUrl().host)
        return withContext(Dispatchers.IO) {
            val tokens = try {
                passwordLoginBlocking(base, username, password)
            } catch (e: IOException) {
                throw reachError(base, e)
            }
            store.saveCredentials(RemoteCredentials(base, username, password))
            tokens
        }
    }

    /** Forgets the sign-in and the kept login on this phone. */
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
     * When the server has ended the sign-in, the kept login signs in again; without one (or when it
     * no longer works) the sign-in is dropped. Null when signed out or the server is unreachable.
     */
    fun refreshBlocking(stale: String): RemoteTokens? {
        synchronized(refreshLock) {
            val current = _session.value ?: return null
            if (current.accessToken != stale) return current
            if (current.refreshToken.isBlank()) return signInAgainBlocking(current)
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
                            signInAgainBlocking(current)
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

    /** With the kept login for [ended]'s server, signs in again; otherwise drops the sign-in. */
    private fun signInAgainBlocking(ended: RemoteTokens): RemoteTokens? {
        val login = store.loadCredentials()?.takeIf { it.serverUrl == ended.serverUrl }
        if (login == null) {
            signOut()
            return null
        }
        return try {
            passwordLoginBlocking(login.serverUrl, login.username, login.password).also {
                Timber.i("[RemoteAuth] Signed in again with the kept login")
            }
        } catch (e: WrongLoginException) {
            // The password changed on the server: asking is the only way on.
            Timber.w("[RemoteAuth] The kept login no longer works")
            signOut()
            null
        } catch (e: IOException) {
            Timber.w(e, "[RemoteAuth] Could not sign in again yet")
            null
        }
    }

    /**
     * The server's native-app flow with a username and password, all from the app:
     * `/auth/native/authorize` starts a PKCE sign-in (its cookie carries the broker state),
     * `/auth/password-login` checks the password and answers with the loopback URL holding a
     * one-time code, and `/auth/native/token` trades the code for tokens. The loopback address is
     * only a value the server checks; the app reads the code from the answer and listens nowhere.
     */
    private fun passwordLoginBlocking(base: String, username: String, password: String): RemoteTokens {
        val provider = passwordProviderBlocking(base)
        val verifier = randomToken(32)
        val state = randomToken(16)
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val client = http.newBuilder()
            .cookieJar(SessionCookies())
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val authorize = "$base/auth/native/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("provider", provider)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("redirect_uri", LOOPBACK_REDIRECT)
            .addQueryParameter("state", state)
            .build()
        client.newCall(Request.Builder().url(authorize).build()).execute().use { response ->
            if (response.code !in 300..399) throw IOException("The server did not start the sign-in (HTTP ${response.code})")
        }

        val login = buildJsonObject {
            put("provider", provider)
            put("username", username)
            put("password", password)
            put("next", "")
        }.toString().toRequestBody(JSON)
        val next = client.newCall(Request.Builder().url("$base/auth/password-login").post(login).build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            when (response.code) {
                200 -> Unit
                401 -> throw WrongLoginException()
                429 -> throw IOException("Too many tries. Wait a minute, then try again.")
                else -> throw IOException("The server refused the sign-in (HTTP ${response.code})")
            }
            val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            o?.str("next").orEmpty()
        }
        val redirect = next.toHttpUrlOrNull() ?: throw IOException("The server's sign-in answer was not understood")
        if (redirect.queryParameter("state") != state) throw IOException("The server's sign-in answer did not match this sign-in")
        val code = redirect.queryParameter("code")?.takeIf { it.isNotBlank() }
            ?: throw IOException("The server sent no sign-in code")
        return exchange(base, code, verifier)
    }

    /** The server's username/password provider (`basic` on a normal self-hosted server). */
    private fun passwordProviderBlocking(base: String): String {
        val request = Request.Builder().url("$base/api/auth/providers").header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The server lists no sign-in options (HTTP ${response.code})")
            val o = runCatching { Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject }.getOrNull()
            val providers = (o?.get("providers") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            return providers.firstOrNull { (it["supports_password"] as? JsonPrimitive)?.booleanOrNull == true }
                ?.str("name")?.takeIf { it.isNotEmpty() }
                ?: throw IOException(
                    "This server has no username and password login. Set HERMES_DASHBOARD_BASIC_AUTH_USERNAME " +
                        "and _PASSWORD on the server (see How to set up your server).",
                )
        }
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

    /** [e] from reaching [base], said so the user knows what to fix. */
    private fun reachError(base: String, e: IOException): IOException {
        if (e is WrongLoginException) return e
        val host = base.toHttpUrl().host
        val message = e.message.orEmpty()
        return when {
            // The in-app tunnel refused or could not reach the device (see tsbridge proxyConn).
            "CONNECT: 502" in message || "CONNECT: 403" in message -> IOException(
                "Can't reach $host inside Tailscale. Check that the server is online in Tailscale and that " +
                    "tailscale serve is on (see How to set up your server).",
                e,
            )
            e is UnknownHostException && host.endsWith(".ts.net") -> IOException(
                "Can't find $host. Sign in to Tailscale above with the same account as the server.",
                e,
            )
            else -> e
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

    private companion object {
        val JSON = "application/json".toMediaType()
        const val EXPIRY_SKEW_S = 60L

        /** RFC 8252 loopback redirect the server requires; nothing listens on it (see [passwordLoginBlocking]). */
        const val LOOPBACK_REDIRECT = "http://127.0.0.1:47123/callback"
    }
}

/** The server said the username or password is wrong. */
class WrongLoginException : IOException("Wrong username or password")

/** Cookies for one sign-in: the server's PKCE cookie has to come back on the password request. */
private class SessionCookies : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.removeAll { old -> cookies.any { it.name == old.name } }
        this.cookies += cookies
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
}

/**
 * The server's HTTP base from whatever was typed: `https://host:port`, a `wss://` gateway URL,
 * with or without `/api/ws` or a trailing slash. No scheme means `https://`. Null when it isn't a URL.
 */
internal fun remoteHttpBase(input: String): String? {
    var s = input.trim()
    if (s.isEmpty()) return null
    if (!s.contains("://")) s = "https://$s"
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

/**
 * [remoteHttpBase] for a server the app may talk to: `https://` only. Hermes itself serves plain
 * HTTP, so the encryption has to come from in front of it (Tailscale Serve); a cleartext address
 * would carry the password, the sign-in and every chat readable.
 */
internal fun encryptedBase(input: String): String {
    val base = remoteHttpBase(input) ?: throw IOException("Not a server address: $input")
    if (!base.startsWith("https://")) {
        throw IOException("Only encrypted https:// addresses are allowed. Use your server's Tailscale Serve address (https://…ts.net).")
    }
    return base
}

/** The gateway socket of the server at [httpBase]: same origin, `ws(s)://…/api/ws`. */
internal fun remoteWebSocketUrl(httpBase: String): String =
    httpBase.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/ws"

private val secureRandom = SecureRandom()

private fun randomToken(bytes: Int): String = b64url(ByteArray(bytes).also { secureRandom.nextBytes(it) })

private fun b64url(bytes: ByteArray): String =
    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

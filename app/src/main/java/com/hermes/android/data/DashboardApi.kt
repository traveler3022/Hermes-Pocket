package com.hermes.android.data

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.ProfileScope
import com.hermes.android.gateway.StdioGatewayHub
import com.hermes.android.runtime.HermesRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A few routes of the `hermes dashboard` REST API, on every runtime.
 *
 * Remote and Termux run the dashboard: the call goes over HTTP to the host the WebSocket
 * talks to (remote sign-in is added by the shared client's interceptor, Termux carries
 * its session token in the WebSocket URL). The built-in Linux has no web server, so its
 * gateway answers `android.dashboard` with the same routers in-process
 * (runtime/linux/ProotLinuxRuntime `GATEWAY_SCRIPT`), and only for the paths it allows.
 *
 * Every call carries the app's selected profile as `?profile=`, as the dashboard's own
 * pages do.
 */
@Singleton
class DashboardApi @Inject constructor(
    private val runtime: HermesRuntime,
    private val gateway: GatewayClient,
    private val profiles: ProfileScope,
    private val json: Json,
    okHttpClient: OkHttpClient,
) {
    private val http = okHttpClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    /** For calls that install packages on the server (memory provider setup). */
    private val slowHttp = http.newBuilder()
        .readTimeout(10, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    /**
     * [method] on [path] (`/api/...`), with [body] as JSON. Returns the answer's JSON;
     * throws [IOException] with the server's `detail` when it refuses.
     */
    suspend fun call(method: String, path: String, body: JsonObject? = null, slow: Boolean = false): JsonElement {
        val scoped = withProfile(path)
        return if (StdioGatewayHub.handles(runtime.getWebSocketUrl())) {
            viaGateway(method, scoped, body, slow)
        } else {
            viaHttp(method, scoped, body, slow)
        }
    }

    private fun withProfile(path: String): String {
        val profile = profiles.active.value ?: return path
        return path + (if ('?' in path) "&" else "?") + "profile=" + URLEncoder.encode(profile, "UTF-8")
    }

    private suspend fun viaGateway(method: String, path: String, body: JsonObject?, slow: Boolean): JsonElement {
        val params = buildMap<String, JsonElement> {
            put("method", JsonPrimitive(method))
            put("path", JsonPrimitive(path))
            if (body != null) put("body", body)
        }
        // Callers expect IOException (TelegramSetupRepository falls back on it), as over HTTP.
        val reply = try {
            gateway.request(
                METHOD,
                params,
                timeoutMs = if (slow) 10 * 60_000L else 120_000L,
                trackSession = false,
            )
        } catch (e: GatewayException) {
            throw IOException(e.rpcMessage ?: e.message, e)
        }
        val result = reply as? JsonObject ?: throw IOException("No answer from Hermes")
        val status = (result["status"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        val answer = result["body"] ?: JsonNull
        if (status !in 200..299) throw IOException(detailOf(answer) ?: "Hermes answered $status")
        return answer
    }

    private suspend fun viaHttp(method: String, path: String, body: JsonObject?, slow: Boolean): JsonElement =
        withContext(Dispatchers.IO) {
            val (base, token) = endpoint()
            val requestBody = body?.toString()?.toRequestBody(JSON_TYPE)
                ?: if (method == "POST" || method == "PUT") "{}".toRequestBody(JSON_TYPE) else null
            val request = Request.Builder()
                .url(base + path)
                .header("Accept", "application/json")
                .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
                .method(method, requestBody)
                .build()
            (if (slow) slowHttp else http).newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val parsed = runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)
                if (!response.isSuccessful) {
                    // A route this Hermes doesn't have answers FastAPI's bare "Not Found".
                    val detail = detailOf(parsed)?.takeIf { it != "Not Found" }
                    throw IOException(
                        detail ?: if (response.code == 404) OLD_HERMES else "Hermes answered HTTP ${response.code}",
                    )
                }
                parsed
            }
        }

    /** ws(s)://host:port/api/ws?token=T → (http(s)://host:port, T). */
    private fun endpoint(): Pair<String, String> {
        val ws = runtime.getWebSocketUrl()
        if (ws.isBlank()) throw IOException("Not connected to Hermes")
        val url = ws.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://").toHttpUrl()
        val token = url.queryParameter("token").orEmpty()
        val path = url.encodedPath.removeSuffix("/").removeSuffix("/api/ws")
        val base = url.newBuilder().encodedPath(path.ifEmpty { "/" }).query(null).build().toString().trimEnd('/')
        return base to token
    }

    /** FastAPI puts the reason in `detail`: a string, or a list of field errors. */
    private fun detailOf(answer: JsonElement): String? {
        val detail = (answer as? JsonObject)?.get("detail") ?: return null
        return (detail as? JsonPrimitive)?.contentOrNull ?: detail.toString().take(300)
    }

    private companion object {
        const val METHOD = "android.dashboard"
        const val OLD_HERMES = "This Hermes doesn't have it yet. Update Hermes on the server."
        val JSON_TYPE = "application/json".toMediaType()
    }
}

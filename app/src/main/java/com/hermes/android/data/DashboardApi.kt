package com.hermes.android.data

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.ProfileScope
import com.hermes.android.gateway.StdioGatewayHub
import com.hermes.android.runtime.HermesRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okio.BufferedSink

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

    /** True on the built-in Linux: there is no HTTP server, files are on this phone's disk. */
    val inProcess: Boolean get() = StdioGatewayHub.handles(runtime.getWebSocketUrl())

    /** The file the built-in Linux keeps at [guestPath], or null when it isn't ready. */
    fun phoneFile(guestPath: String): File? = runtime.hostFileForGuestPath(guestPath)

    /** GETs [path] over HTTP into [target] (remote and Termux only), streamed, not held in memory. */
    suspend fun download(path: String, target: File) = withContext(Dispatchers.IO) {
        val (base, token) = endpoint()
        val request = Request.Builder()
            .url(base + withProfile(path))
            .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
            .get()
            .build()
        slowHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                val parsed = runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)
                throw IOException(detailOf(parsed) ?: "Hermes answered HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Hermes sent nothing")
            target.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
    }

    /**
     * For a chat attachment's upload: no call limit, as a big file on a slow uplink takes as
     * long as it takes. A stalled one still ends on the write timeout.
     */
    private val uploadHttp = http.newBuilder()
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    /**
     * Streams [open]'s bytes ([size], or -1 when unknown) to `/api/files/upload-stream` as the
     * server file [serverPath] (remote and Termux only), calling [onProgress] as they go out.
     * Returns the answer's JSON. Cancelling the caller cancels the call.
     */
    suspend fun uploadStream(
        serverPath: String,
        fileName: String,
        size: Long,
        open: () -> InputStream,
        onProgress: (Long, Long) -> Unit,
    ): JsonElement = withContext(Dispatchers.IO) {
        val (base, token) = endpoint()
        val filePart = object : RequestBody() {
            override fun contentType() = OCTET_TYPE
            override fun contentLength() = if (size > 0) size else -1L
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                var sent = 0L
                open().use { input ->
                    val buffer = ByteArray(UPLOAD_BUFFER_BYTES)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        sink.write(buffer, 0, n)
                        sent += n
                        onProgress(sent, size)
                    }
                }
            }
        }
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, filePart)
            .addFormDataPart("path", serverPath)
            .addFormDataPart("overwrite", "false")
            .build()
        val request = Request.Builder()
            .url(base + withProfile("/api/files/upload-stream"))
            .header("Accept", "application/json")
            .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
            .post(form)
            .build()
        val call = uploadHttp.newCall(request)
        // A blocking write does not see cancellation; cancelling the call ends it.
        val cancelHook = coroutineContext.job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                val text = response.body?.string().orEmpty()
                val parsed = runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)
                if (!response.isSuccessful) {
                    val detail = detailOf(parsed)?.takeIf { it != "Not Found" }
                    throw IOException(
                        detail ?: if (response.code == 404) OLD_HERMES else "Hermes answered HTTP ${response.code}",
                    )
                }
                parsed
            }
        } finally {
            cancelHook.dispose()
        }
    }

    /**
     * POSTs [file] as the form's `file` part, with [fields] beside it, to [path] over HTTP
     * (remote and Termux only). Returns the answer's JSON.
     */
    suspend fun upload(path: String, file: File, fileName: String, fields: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val (base, token) = endpoint()
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, file.asRequestBody(OCTET_TYPE))
                .apply { fields.forEach { (key, value) -> addFormDataPart(key, value) } }
                .build()
            val request = Request.Builder()
                .url(base + withProfile(path))
                .header("Accept", "application/json")
                .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
                .post(form)
                .build()
            slowHttp.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val parsed = runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)
                if (!response.isSuccessful) {
                    val detail = detailOf(parsed)?.takeIf { it != "Not Found" }
                    throw IOException(
                        detail ?: if (response.code == 404) OLD_HERMES else "Hermes answered HTTP ${response.code}",
                    )
                }
                parsed
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
        val OCTET_TYPE = "application/octet-stream".toMediaType()
        const val UPLOAD_BUFFER_BYTES = 64 * 1024
    }
}

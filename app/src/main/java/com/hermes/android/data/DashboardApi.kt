package com.hermes.android.data

import com.hermes.android.gateway.StdioGatewayHub
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.linux.ProotEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `hermes dashboard` REST API for things the gateway has no RPC for (toolset providers and
 * keys, messaging platforms). Termux and remote runtimes call the web server their WebSocket
 * talks to; on a remote server the shared client's RemoteAuthInterceptor swaps in its own token.
 * The built-in Linux has no web server, so there the same routers run in-process under
 * FastAPI's TestClient in a short script — the dashboard's own handlers either way.
 */
@Singleton
class DashboardApi @Inject constructor(
    private val runtime: HermesRuntime,
    okHttpClient: OkHttpClient,
    private val linux: ProotEnvironment,
) {
    private val viaStdio: Boolean get() = StdioGatewayHub.handles(runtime.getWebSocketUrl())
    private val http = okHttpClient.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** [method] [path] (with its query) and a JSON [body]; the answer's JSON, or an IOException with the server's reason. */
    suspend fun call(method: String, path: String, body: JsonObject? = null): JsonElement =
        if (viaStdio) callInRootfs(method, path, body) else callServer(method, path, body)

    private suspend fun callServer(method: String, path: String, body: JsonObject?): JsonElement = withContext(Dispatchers.IO) {
        val (baseUrl, token) = dashboardEndpoint()
        val requestBody = body?.toString()?.toRequestBody(JsonMedia)
            ?: if (method == "POST" || method == "PUT") "{}".toRequestBody(JsonMedia) else null
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .method(method, requestBody)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)
            if (!response.isSuccessful) throw IOException(detailOf(parsed) ?: "Hermes answered HTTP ${response.code}")
            parsed
        }
    }

    private suspend fun callInRootfs(method: String, path: String, body: JsonObject?): JsonElement = withContext(Dispatchers.IO) {
        val script = linux.guestFile(SCRIPT_PATH)
        script.parentFile?.mkdirs()
        script.writeText(SCRIPT)
        val args = buildJsonObject {
            put("method", method)
            put("path", path)
            if (body != null) put("body", body)
        }
        var last: JsonObject? = null
        val result = linux.run(
            command = "cd \"\$HERMES_HOME/hermes-agent\" && \"\$HERMES_HOME/hermes-agent/venv/bin/python\" -u $SCRIPT_PATH",
            extraEnv = mapOf("HERMES_HOME" to "/root/.hermes", "PYTHONPATH" to "/root/.hermes/hermes-agent", "HERMES2_ARGS" to args.toString()),
            onLine = { line ->
                if (line.startsWith(RESULT_MARKER)) {
                    runCatching { json.parseToJsonElement(line.removePrefix(RESULT_MARKER)).jsonObject }.getOrNull()?.let { last = it }
                }
            },
        )
        val answer = last ?: throw IOException("Dashboard helper failed: ${result.output.takeLast(300)}")
        val status = (answer["status"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        val parsed = answer["body"] ?: JsonNull
        if (status !in 200..299) throw IOException(detailOf(parsed) ?: "Hermes answered $status")
        parsed
    }

    /** FastAPI puts the reason in `detail`: a string, or a list of validation errors. */
    private fun detailOf(parsed: JsonElement): String? {
        val detail = (parsed as? JsonObject)?.get("detail") ?: return null
        return (detail as? JsonPrimitive)?.contentOrNull ?: detail.toString().take(300)
    }

    /** ws://host:port/api/ws?token=T → (http://host:port, T). */
    private fun dashboardEndpoint(): Pair<String, String> {
        val ws = runtime.getWebSocketUrl()
        val httpUrl = ws.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://").toHttpUrl()
        val token = httpUrl.queryParameter("token").orEmpty()
        val base = httpUrl.newBuilder().encodedPath("/").query(null).build().toString().trimEnd('/')
        return base to token
    }

    private companion object {
        val JsonMedia = "application/json".toMediaType()
        const val RESULT_MARKER = "HERMES2_RESULT "
        const val SCRIPT_PATH = "/root/.hermes/hermes2_dashboard.py"

        // The dashboard routers the app uses, mounted on a bare app (no auth: nothing listens).
        // Prints one `HERMES2_RESULT {"status", "body"}` line; anything else on stdout is noise.
        val SCRIPT = """
            import json, os, sys

            def emit(obj):
                print("$RESULT_MARKER" + json.dumps(obj, default=str), flush=True)

            try:
                args = json.loads(os.environ.get("HERMES2_ARGS") or "{}")
                from fastapi import FastAPI
                from fastapi.testclient import TestClient
                from hermes_cli.web_routers import config_env, messaging, tools
                app = FastAPI()
                for module in (tools, config_env, messaging):
                    app.include_router(module.router)
                kwargs = {"json": args["body"]} if "body" in args else {}
                response = TestClient(app).request(args["method"], args["path"], **kwargs)
                try:
                    body = response.json()
                except Exception:
                    body = {"detail": response.text[:300]}
                emit({"status": response.status_code, "body": body})
            except Exception as e:
                emit({"status": 500, "body": {"detail": str(e)}})
        """.trimIndent()
    }
}

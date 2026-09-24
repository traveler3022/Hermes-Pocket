package com.hermes.android.data

import android.content.Context
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.StdioGatewayHub
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.linux.ProotEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
import kotlinx.serialization.json.contentOrNull

/** A provider offered in first-run setup; [envVar] is where Hermes reads its key. */
data class SetupProvider(
    val slug: String,
    val name: String,
    val envVar: String,
    val keyUrl: String,
    val needsBaseUrl: Boolean = false,
)

data class KeyCheck(val ok: Boolean, val reachable: Boolean, val message: String, val models: List<String> = emptyList())

sealed interface DefaultModelResult {
    data object Applied : DefaultModelResult
    data class NeedsConfirm(val message: String) : DefaultModelResult
}

/**
 * First-run provider setup. Termux and remote runtimes use the `hermes dashboard` REST API
 * (the server their WebSocket talks to). The built-in Linux runtime has no web server — its
 * gateway is a stdio child process — so there the key is probed from the app, saved and listed
 * through gateway JSON-RPC, and the default model is written by a small script run in the
 * rootfs. Unlike `config.set model`, none of this needs a chat session.
 */
@Singleton
class ProviderSetupRepository @Inject constructor(
    private val runtime: HermesRuntime,
    okHttpClient: OkHttpClient,
    private val gateway: GatewayClient,
    private val linux: ProotEnvironment,
) {
    private val viaStdio: Boolean get() = StdioGatewayHub.handles(runtime.getWebSocketUrl())

    private val http = okHttpClient.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    val providers: List<SetupProvider> = listOf(
        SetupProvider("openrouter", "OpenRouter", "OPENROUTER_API_KEY", "https://openrouter.ai/keys"),
        SetupProvider("anthropic", "Anthropic", "ANTHROPIC_API_KEY", "https://console.anthropic.com/settings/keys"),
        SetupProvider("openai-api", "OpenAI", "OPENAI_API_KEY", "https://platform.openai.com/api-keys"),
        SetupProvider("gemini", "Google Gemini", "GEMINI_API_KEY", "https://aistudio.google.com/apikey"),
        SetupProvider("deepseek", "DeepSeek", "DEEPSEEK_API_KEY", "https://platform.deepseek.com/api_keys"),
        SetupProvider("xai", "xAI Grok", "XAI_API_KEY", "https://console.x.ai"),
        SetupProvider("xiaomi", "Xiaomi MiMo", "XIAOMI_API_KEY", "https://platform.xiaomimimo.com"),
        SetupProvider("zai", "Z.AI / GLM", "GLM_API_KEY", "https://z.ai/manage-apikey/apikey-list"),
        SetupProvider("kimi-coding", "Kimi / Moonshot", "KIMI_API_KEY", "https://platform.moonshot.ai/console/api-keys"),
        SetupProvider("nvidia", "NVIDIA NIM", "NVIDIA_API_KEY", "https://build.nvidia.com"),
        SetupProvider("custom", "Custom (OpenAI-compatible)", "OPENAI_BASE_URL", "", needsBaseUrl = true),
    )

    /** Live-probes the key (or, for custom endpoints, the base URL) before it is saved. */
    suspend fun checkKey(provider: SetupProvider, apiKey: String, baseUrl: String = ""): KeyCheck {
        if (viaStdio) return probeKey(provider, apiKey, baseUrl)
        val body = if (provider.needsBaseUrl) {
            buildJsonObject { put("key", "OPENAI_BASE_URL"); put("value", baseUrl); put("api_key", apiKey) }
        } else {
            buildJsonObject { put("key", provider.envVar); put("value", apiKey) }
        }
        val result = call("POST", "/api/providers/validate", body)
        return KeyCheck(
            ok = result.bool("ok"),
            reachable = result.bool("reachable"),
            message = result.text("message").orEmpty(),
            models = (result["models"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
        )
    }

    /** Custom endpoints store their key with the model assignment instead of in .env. */
    suspend fun saveKey(provider: SetupProvider, apiKey: String) {
        if (provider.needsBaseUrl) return
        if (viaStdio) {
            // Same write as the dashboard's PUT /api/env (model.save_key rejects registry-less
            // providers such as OpenRouter), then have the running gateway re-read .env.
            val saved = runSetupScript("save_key", buildJsonObject { put("env_var", provider.envVar); put("value", apiKey) })
            if (!saved.bool("ok")) throw IOException(saved.text("detail") ?: "Could not save the key")
            rpc("reload.env", buildJsonObject {})
            return
        }
        call("PUT", "/api/env", buildJsonObject { put("key", provider.envVar); put("value", apiKey) })
    }

    suspend fun models(provider: SetupProvider): List<String> {
        val result = if (viaStdio) rpc("model.options", buildJsonObject {}) else call("GET", "/api/model/options", null)
        val row = (result["providers"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.text("slug") == provider.slug }
            ?: return emptyList()
        return (row["models"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    }

    suspend fun setDefaultModel(
        provider: SetupProvider,
        model: String,
        apiKey: String = "",
        baseUrl: String = "",
        confirmed: Boolean = false,
    ): DefaultModelResult {
        if (viaStdio) {
            val args = buildJsonObject {
                put("provider", provider.slug)
                put("model", model)
                put("base_url", if (provider.needsBaseUrl) baseUrl else "")
                put("api_key", if (provider.needsBaseUrl) apiKey else "")
                put("confirm", confirmed)
            }
            return applyDefaultModel(runSetupScript("set_model", args))
        }
        val body = buildJsonObject {
            put("scope", "main")
            put("provider", provider.slug)
            put("model", model)
            if (provider.needsBaseUrl) {
                put("base_url", baseUrl)
                put("api_key", apiKey)
            }
            if (confirmed) put("confirm_expensive_model", true)
        }
        return applyDefaultModel(call("POST", "/api/model/set", body))
    }

    private fun applyDefaultModel(result: JsonObject): DefaultModelResult {
        if (result.bool("confirm_required")) {
            return DefaultModelResult.NeedsConfirm(result.text("confirm_message") ?: "This model is expensive. Use it anyway?")
        }
        if (!result.bool("ok")) throw IOException(result.text("detail") ?: "Could not save the model")
        return DefaultModelResult.Applied
    }

    /** True once config.yaml names a concrete provider (i.e. setup already happened elsewhere). */
    suspend fun hasConfiguredModel(): Boolean {
        if (viaStdio) return runSetupScript("has_model", buildJsonObject {}).bool("configured")
        val info = call("GET", "/api/model/info", null)
        val provider = info.text("provider")
        return !provider.isNullOrBlank() && provider != "auto"
    }

    private suspend fun call(method: String, path: String, body: JsonObject?): JsonObject = withContext(Dispatchers.IO) {
        val (baseUrl, token) = dashboardEndpoint()
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .method(method, body?.toString()?.toRequestBody(JsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (!response.isSuccessful) {
                val detail = parsed?.text("detail") ?: text.take(200)
                throw IOException("Hermes answered HTTP ${response.code}: $detail")
            }
            parsed ?: JsonObject(emptyMap())
        }
    }

    private suspend fun rpc(method: String, params: JsonObject): JsonObject {
        gateway.connect(url = StdioGatewayHub.URL)
        return gateway.request(method, params.toMap()) as? JsonObject ?: JsonObject(emptyMap())
    }

    /** Runs [SETUP_SCRIPT] in the rootfs; arguments travel in the environment, not the command line. */
    private suspend fun runSetupScript(command: String, args: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val script = linux.guestFile(SETUP_SCRIPT_PATH)
        script.parentFile?.mkdirs()
        script.writeText(SETUP_SCRIPT)
        val result = linux.run(
            command = "cd \"\$HERMES_HOME/hermes-agent\" && \"\$HERMES_HOME/hermes-agent/venv/bin/python\" -u $SETUP_SCRIPT_PATH $command",
            extraEnv = mapOf("HERMES_HOME" to "/root/.hermes", "PYTHONPATH" to "/root/.hermes/hermes-agent", "HERMES2_ARGS" to args.toString()),
        )
        val line = result.output.lineSequence().lastOrNull { it.startsWith(RESULT_MARKER) }
            ?: throw IOException("Setup helper failed: ${result.output.takeLast(300)}")
        json.parseToJsonElement(line.removePrefix(RESULT_MARKER)).jsonObject
    }

    /** Probes a key (or custom endpoint) directly, mirroring the dashboard's /api/providers/validate. */
    private suspend fun probeKey(provider: SetupProvider, apiKey: String, baseUrl: String): KeyCheck = withContext(Dispatchers.IO) {
        val unknown = KeyCheck(ok = true, reachable = false, message = "")
        val request = if (provider.needsBaseUrl) {
            val url = baseUrl.trimEnd('/') + "/models"
            Request.Builder().url(url.toHttpUrl()).apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
        } else {
            val (url, bearer) = CredentialProbes[provider.envVar] ?: return@withContext unknown
            if (provider.envVar == "GEMINI_API_KEY" && apiKey.startsWith("AQ.")) return@withContext unknown
            val builder = url.toHttpUrl().newBuilder()
            if (!bearer) builder.addQueryParameter("key", apiKey)
            Request.Builder().url(builder.build()).apply { if (bearer) header("Authorization", "Bearer $apiKey") }
        }.header("Accept", "application/json").build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: Exception) {
            val where = if (provider.needsBaseUrl) request.url.toString() else "the provider"
            return@withContext KeyCheck(ok = false, reachable = false, message = "Could not reach $where.")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            when {
                provider.needsBaseUrl -> {
                    val models = modelIds(text)
                    if (models.isEmpty() && !it.isSuccessful) {
                        KeyCheck(false, true, "${request.url} answered HTTP ${it.code}.")
                    } else {
                        KeyCheck(true, true, "", models)
                    }
                }
                it.code == 401 || it.code == 403 ->
                    KeyCheck(false, true, "That API key was rejected. Double-check it and try again.")
                it.code == 429 || it.isSuccessful -> KeyCheck(true, true, "")
                else -> KeyCheck(false, true, "Provider returned HTTP ${it.code} for this key.")
            }
        }
    }

    /** OpenAI `/models` shape: `{"data":[{"id":…}]}`. */
    private fun modelIds(body: String): List<String> {
        val data = runCatching { json.parseToJsonElement(body).jsonObject["data"] as? JsonArray }.getOrNull() ?: return emptyList()
        return data.mapNotNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull }
    }

    /** ws://host:port/api/ws?token=T → (http://host:port, T). */
    private fun dashboardEndpoint(): Pair<String, String> {
        val ws = runtime.getWebSocketUrl()
        val httpUrl = ws.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://").toHttpUrl()
        val token = httpUrl.queryParameter("token").orEmpty()
        val base = httpUrl.newBuilder().encodedPath("/").query(null).build().toString().trimEnd('/')
        return base to token
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.contentOrNull == "true"

    private companion object {
        val JsonMedia = "application/json".toMediaType()
        const val RESULT_MARKER = "HERMES2_RESULT "
        const val SETUP_SCRIPT_PATH = "/root/.hermes/hermes2_setup.py"

        /** env var → (probe URL, key sent as bearer header rather than `?key=`). */
        val CredentialProbes = mapOf(
            "OPENROUTER_API_KEY" to ("https://openrouter.ai/api/v1/key" to true),
            "OPENAI_API_KEY" to ("https://api.openai.com/v1/models" to true),
            "XAI_API_KEY" to ("https://api.x.ai/v1/models" to true),
            "GEMINI_API_KEY" to ("https://generativelanguage.googleapis.com/v1beta/models" to false),
        )

        // Same code path as the dashboard's POST /api/model/set and GET /api/model/info, minus the
        // web server. Prints one `HERMES2_RESULT {json}` line; anything else on stdout is noise.
        val SETUP_SCRIPT = """
            import json, os, sys

            def emit(obj):
                print("$RESULT_MARKER" + json.dumps(obj), flush=True)

            def save_key(args):
                from hermes_cli.credential_lifecycle import save_provider_env_credential
                save_provider_env_credential(args["env_var"], args["value"])
                emit({"ok": True})

            def has_model(args):
                from hermes_cli.config import load_config
                cfg = load_config().get("model", "")
                provider = cfg.get("provider", "") if isinstance(cfg, dict) else ""
                emit({"configured": bool(provider) and provider != "auto"})

            def set_model(args):
                from hermes_cli.config import load_config
                from hermes_cli.web_server_config import _apply_model_assignment_sync, _prepare_main_assignment
                provider, model = args["provider"], args["model"]
                base_url, api_key = args.get("base_url", ""), args.get("api_key", "")
                if not args.get("confirm"):
                    try:
                        from hermes_cli.model_selection_guards import combined_selection_warning
                        warning = combined_selection_warning(model, provider=provider, base_url=base_url)
                    except Exception:
                        warning = None
                    if warning is not None:
                        return emit({"ok": False, "confirm_required": True, "confirm_message": warning.message})
                prepared = _prepare_main_assignment(load_config(), provider, model, base_url, api_key)
                _apply_model_assignment_sync("main", provider, model, "", base_url, api_key, prepared=prepared)
                emit({"ok": True})

            try:
                {"has_model": has_model, "save_key": save_key, "set_model": set_model}[sys.argv[1]](json.loads(os.environ.get("HERMES2_ARGS") or "{}"))
            except Exception as e:
                emit({"ok": False, "detail": str(getattr(e, "detail", None) or e)})
        """.trimIndent()
    }
}

/** Whether first-run setup (runtime + provider) has been completed. */
@Singleton
class SetupState @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("hermes_setup", Context.MODE_PRIVATE)

    // Users who already set Hermes up through Termux shouldn't be sent back through setup.
    val isComplete: Boolean
        get() = prefs.getBoolean(KEY_COMPLETE, false) ||
            context.getSharedPreferences("hermes_runtime", Context.MODE_PRIVATE).getBoolean("installed", false)

    fun markComplete() = prefs.edit().putBoolean(KEY_COMPLETE, true).apply()

    private companion object {
        const val KEY_COMPLETE = "complete"
    }
}

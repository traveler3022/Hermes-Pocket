package com.hermes.android.data

import android.content.Context
import com.hermes.android.runtime.HermesRuntime
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
 * First-run provider setup over the `hermes dashboard` REST API (the same server the
 * app's WebSocket talks to). Unlike `config.set model`, these endpoints work before any
 * chat session exists, and they apply to every runtime (built-in Linux, Termux, remote).
 */
@Singleton
class ProviderSetupRepository @Inject constructor(
    private val runtime: HermesRuntime,
    okHttpClient: OkHttpClient,
) {
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
            models = (result["models"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
        )
    }

    /** Custom endpoints store their key with the model assignment instead of in .env. */
    suspend fun saveKey(provider: SetupProvider, apiKey: String) {
        if (provider.needsBaseUrl) return
        call("PUT", "/api/env", buildJsonObject { put("key", provider.envVar); put("value", apiKey) })
    }

    suspend fun models(provider: SetupProvider): List<String> {
        val result = call("GET", "/api/model/options", null)
        val row = (result["providers"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.text("slug") == provider.slug }
            ?: return emptyList()
        return (row["models"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
    }

    suspend fun setDefaultModel(
        provider: SetupProvider,
        model: String,
        apiKey: String = "",
        baseUrl: String = "",
        confirmed: Boolean = false,
    ): DefaultModelResult {
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
        val result = call("POST", "/api/model/set", body)
        if (result.bool("confirm_required")) {
            return DefaultModelResult.NeedsConfirm(result.text("confirm_message") ?: "This model is expensive. Use it anyway?")
        }
        if (!result.bool("ok")) throw IOException(result.text("detail") ?: "Could not save the model")
        return DefaultModelResult.Applied
    }

    /** True once config.yaml names a concrete provider (i.e. setup already happened elsewhere). */
    suspend fun hasConfiguredModel(): Boolean {
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

    /** ws://host:port/api/ws?token=T → (http://host:port, T). */
    private fun dashboardEndpoint(): Pair<String, String> {
        val ws = runtime.getWebSocketUrl()
        val httpUrl = ws.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://").toHttpUrl()
        val token = httpUrl.queryParameter("token").orEmpty()
        val base = httpUrl.newBuilder().encodedPath("/").query(null).build().toString().trimEnd('/')
        return base to token
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.content == "true"

    private companion object {
        val JsonMedia = "application/json".toMediaType()
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

package com.hermes.android.data

import com.hermes.android.domain.tools.ToolsetConfig
import com.hermes.android.domain.tools.ToolsetEnvVar
import com.hermes.android.domain.tools.ToolsetProvider
import com.hermes.android.domain.tools.ToolsetSetup
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The dashboard's per-toolset endpoints (`/api/tools/toolsets/{name}/config|provider|env`, and
 * DELETE `/api/env`), the same ones Hy4ri/hermes-mobile's toolset page calls. The gateway has no RPC for them.
 */
@Singleton
class ToolsetSetupRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : ToolsetSetup {

    override suspend fun config(toolset: String): ToolsetConfig =
        parseToolsetConfig(toolset, dashboard.call("GET", "/api/tools/toolsets/${toolset.urlPart()}/config"))

    override suspend fun selectProvider(toolset: String, provider: String): Boolean {
        val result = dashboard.call(
            "PUT",
            "/api/tools/toolsets/${toolset.urlPart()}/provider",
            buildJsonObject { put("provider", provider) },
        ) as? JsonObject
        return (result?.get("needs_nous_auth") as? JsonPrimitive)?.booleanOrNull == true
    }

    override suspend fun saveKey(toolset: String, key: String, value: String) {
        dashboard.call(
            "PUT",
            "/api/tools/toolsets/${toolset.urlPart()}/env",
            buildJsonObject { put("env", buildJsonObject { put(key, value) }) },
        )
    }

    override suspend fun clearKey(key: String) {
        dashboard.call("DELETE", "/api/env", buildJsonObject { put("key", key) })
    }

    private fun String.urlPart(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

/** GET /api/tools/toolsets/{name}/config. A toolset without backends comes back with an empty list. */
internal fun parseToolsetConfig(toolset: String, result: JsonElement): ToolsetConfig {
    val root = result as? JsonObject
    fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
    fun JsonObject.flag(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull == true
    val providers = (root?.get("providers") as? JsonArray).orEmpty().mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val name = row.text("name") ?: return@mapNotNull null
        ToolsetProvider(
            name = name,
            badge = row.text("badge"),
            tag = row.text("tag"),
            envVars = (row["env_vars"] as? JsonArray).orEmpty().mapNotNull { env ->
                val e = env as? JsonObject ?: return@mapNotNull null
                val key = e.text("key") ?: return@mapNotNull null
                ToolsetEnvVar(key = key, prompt = e.text("prompt"), url = e.text("url"), isSet = e.flag("is_set"))
            },
            isActive = row.flag("is_active"),
            status = row.text("status"),
            requiresNousAuth = row.flag("requires_nous_auth"),
        )
    }
    return ToolsetConfig(
        name = root?.text("name") ?: toolset,
        providers = providers,
        activeProvider = root?.text("active_provider") ?: providers.firstOrNull { it.isActive }?.name,
    )
}

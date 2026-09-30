package com.hermes.android.ui.viewmodel

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

fun parseModelOptions(result: JsonElement): List<ModelOption> {
    return try {
        // Fix F01: build_models_payload (inventory.py:222-226) returns:
        //   {providers: [rows], model: str, provider: str}
        // Each row (model_switch.py:1401-1407) has:
        //   slug, name, is_current, is_user_defined, models: List[str], total_models, source
        // models is a List[str] of model IDs — NOT a list of objects.
        // picker_hints adds: authenticated, auth_type, key_env, warning
        val obj = result as? JsonObject ?: return emptyList()
        val providersArr = obj["providers"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        providersArr.flatMap { providerEl ->
            val providerObj = providerEl as? JsonObject ?: return@flatMap emptyList()
            val slug = providerObj["slug"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
            val providerName = providerObj["name"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
            val models = providerObj["models"] as? kotlinx.serialization.json.JsonArray ?: return@flatMap emptyList()
            models.mapNotNull { modelEl ->
                val modelId = (modelEl as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                ModelOption(
                    provider = slug,
                    modelId = modelId,
                    name = modelId,
                    requiresApiKey = providerObj["authenticated"]?.let { (it as? JsonPrimitive)?.contentOrNull } == "false",
                    providerName = providerName,
                )
            }
        // The pickers key their lists by provider/model, and a repeated key crashes a
        // LazyColumn; the catalogue is merged from several sources on the server.
        }.distinctBy { it.provider to it.modelId }
    } catch (e: Exception) {
        Timber.w(e, "[Config] Failed to parse model options")
        emptyList()
    }
}

/**
 * Provider rows of `model.options` asked with `include_unconfigured` (inventory.py): configured
 * providers plus a skeleton row for every other one. Picker hints mark each row `authenticated`;
 * only the skeletons carry `auth_type` and `key_env`. A server without hints lists only configured
 * providers, so a row missing `authenticated` counts as connected.
 */
fun parseProviderRows(result: JsonElement): List<ProviderRow> {
    val rows = (result as? JsonObject)?.get("providers") as? JsonArray ?: return emptyList()
    return rows.mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        fun text(key: String) = (row[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        fun flag(key: String) = (row[key] as? JsonPrimitive)?.booleanOrNull
        val slug = text("slug") ?: return@mapNotNull null
        ProviderRow(
            slug = slug,
            name = text("name") ?: slug,
            connected = flag("authenticated") ?: true,
            authType = text("auth_type"),
            keyEnv = text("key_env"),
            modelCount = (row["total_models"] as? JsonPrimitive)?.intOrNull
                ?: (row["models"] as? JsonArray)?.size ?: 0,
            isCurrent = flag("is_current") == true,
            isUserDefined = flag("is_user_defined") == true,
        )
    }.distinctBy { it.slug }
}

/**
 * tools.list returns every toolset, but tools.configure only switches CONFIGURABLE_TOOLSETS
 * and plugin toolsets (hermes_cli/tools_config.py). The rest are presets: one per messaging
 * platform (hermes-*) and the bundles below, which filled this list with the same platforms
 * the Plugins page shows. The last five are the ones Hermes desktop hides on top
 * (lib/desktop-toolsets.ts: platform-coupled or internal plumbing).
 */
private val UNLISTED_TOOLSETS = setOf(
    "bot_room", "coding", "debugging", "desktop_ui", "feishu_doc", "feishu_drive",
    "project", "safe", "search", "setup",
    "discord", "discord_admin", "yuanbao", "context_engine", "moa",
)

internal fun isListedToolset(name: String): Boolean =
    !name.startsWith("hermes-") && name !in UNLISTED_TOOLSETS

fun parseToolList(result: JsonElement): List<ToolOption> {
    return try {
        // Fix S5F02: tools.list returns {toolsets: [{name, description, tool_count, enabled, tools}]}
        val obj = result as? JsonObject ?: return emptyList()
        val toolsets = obj["toolsets"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        toolsets.mapNotNull { tsEl ->
            val ts = tsEl as? JsonObject ?: return@mapNotNull null
            val tools = (ts["tools"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?: emptyList()
            ToolOption(
                name = ts["name"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "",
                description = ts["description"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "",
                enabled = ts["enabled"]?.let { (it as? JsonPrimitive)?.contentOrNull } != "false",
                toolset = null,
                toolCount = ts["tool_count"]?.let { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
                    ?: tools.size,
                tools = tools,
            )
        }.filter { isListedToolset(it.name) }
    } catch (e: Exception) {
        emptyList()
    }
}

fun parseConfigSections(result: JsonElement): String {
    return try {
        val obj = result as? JsonObject ?: return "(empty)"
        val sections = obj["sections"] as? kotlinx.serialization.json.JsonArray ?: return "(empty)"
        buildString {
            for (sectionEl in sections) {
                val section = sectionEl as? JsonObject ?: continue
                val title = section["title"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
                appendLine("## $title")
                val rows = section["rows"] as? kotlinx.serialization.json.JsonArray ?: continue
                for (rowEl in rows) {
                    val row = rowEl as? kotlinx.serialization.json.JsonArray ?: continue
                    val label = row.getOrNull(0)?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
                    val value = row.getOrNull(1)?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
                    appendLine("  $label: $value")
                }
                appendLine()
            }
        }
    } catch (e: Exception) {
        "(parse error: ${e.message})"
    }
}

fun parseCredentialEntries(json: String): List<CredentialEntry> {
    return try {
        val arr = kotlinx.serialization.json.Json.parseToJsonElement(json) as? JsonArray
        arr?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            CredentialEntry(
                index = (obj["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
                id = (obj["id"] as? JsonPrimitive)?.contentOrNull,
                label = (obj["label"] as? JsonPrimitive)?.contentOrNull,
                authType = (obj["auth_type"] as? JsonPrimitive)?.contentOrNull,
                tokenPreview = (obj["token_preview"] as? JsonPrimitive)?.contentOrNull ?: "***",
                priority = (obj["priority"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
                lastStatus = (obj["last_status"] as? JsonPrimitive)?.contentOrNull,
                requestCount = (obj["request_count"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
            )
        } ?: emptyList()
    } catch (e: Exception) {
        Timber.w(e, "[Config] Failed to parse credentials")
        emptyList()
    }
}

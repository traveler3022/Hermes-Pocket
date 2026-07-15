package com.hermes.android.ui.viewmodel

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

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
            val slug = providerObj["slug"]?.let { (it as? JsonPrimitive)?.content } ?: ""
            val models = providerObj["models"] as? kotlinx.serialization.json.JsonArray ?: return@flatMap emptyList()
            models.mapNotNull { modelEl ->
                val modelId = (modelEl as? JsonPrimitive)?.content ?: return@mapNotNull null
                ModelOption(
                    provider = slug,
                    modelId = modelId,
                    name = modelId,
                    requiresApiKey = providerObj["authenticated"]?.let { (it as? JsonPrimitive)?.content } == "false",
                )
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "[Config] Failed to parse model options")
        emptyList()
    }
}

fun parseToolList(result: JsonElement): List<ToolOption> {
    return try {
        // Fix S5F02: tools.list returns {toolsets: [{name, description, tool_count, enabled, tools}]}
        val obj = result as? JsonObject ?: return emptyList()
        val toolsets = obj["toolsets"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        toolsets.mapNotNull { tsEl ->
            val ts = tsEl as? JsonObject ?: return@mapNotNull null
            val tools = (ts["tools"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                ?: emptyList()
            ToolOption(
                name = ts["name"]?.let { (it as? JsonPrimitive)?.content } ?: "",
                description = ts["description"]?.let { (it as? JsonPrimitive)?.content } ?: "",
                enabled = ts["enabled"]?.let { (it as? JsonPrimitive)?.content } != "false",
                toolset = null,
                toolCount = ts["tool_count"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() }
                    ?: tools.size,
                tools = tools,
            )
        }
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
                val title = section["title"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                appendLine("## $title")
                val rows = section["rows"] as? kotlinx.serialization.json.JsonArray ?: continue
                for (rowEl in rows) {
                    val row = rowEl as? kotlinx.serialization.json.JsonArray ?: continue
                    val label = row.getOrNull(0)?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val value = row.getOrNull(1)?.let { (it as? JsonPrimitive)?.content } ?: ""
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
                index = (obj["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                id = (obj["id"] as? JsonPrimitive)?.content,
                label = (obj["label"] as? JsonPrimitive)?.content,
                authType = (obj["auth_type"] as? JsonPrimitive)?.content,
                tokenPreview = (obj["token_preview"] as? JsonPrimitive)?.content ?: "***",
                priority = (obj["priority"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                lastStatus = (obj["last_status"] as? JsonPrimitive)?.content,
                requestCount = (obj["request_count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
            )
        } ?: emptyList()
    } catch (e: Exception) {
        Timber.w(e, "[Config] Failed to parse credentials")
        emptyList()
    }
}

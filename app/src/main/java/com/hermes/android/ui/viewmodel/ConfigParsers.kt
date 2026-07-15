package com.hermes.android.ui.viewmodel

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

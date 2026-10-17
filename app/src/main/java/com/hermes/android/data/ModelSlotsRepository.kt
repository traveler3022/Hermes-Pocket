package com.hermes.android.data

import com.hermes.android.domain.config.AuxiliaryModels
import com.hermes.android.domain.config.AuxiliaryTask
import com.hermes.android.domain.config.ModelSlots
import com.hermes.android.domain.config.MoaConfig
import com.hermes.android.domain.config.MoaPreset
import com.hermes.android.domain.config.MoaSlot
import com.hermes.android.domain.config.SlotSave
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ModelSlots] over the dashboard: `GET /api/model/auxiliary`, `POST /api/model/set` with
 * scope "auxiliary", and `GET`/`PUT /api/model/moa`. The built-in Linux serves the same
 * router in-process (android.dashboard).
 */
@Singleton
class ModelSlotsRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : ModelSlots {

    override suspend fun auxiliary(): AuxiliaryModels = parseAuxiliary(dashboard.call("GET", "/api/model/auxiliary"))

    override suspend fun setAuxiliary(task: String, provider: String, model: String, confirmExpensive: Boolean): SlotSave {
        val body = JsonObject(
            mapOf(
                "scope" to JsonPrimitive("auxiliary"),
                "task" to JsonPrimitive(task),
                "provider" to JsonPrimitive(provider),
                "model" to JsonPrimitive(model),
                "confirm_expensive_model" to JsonPrimitive(confirmExpensive),
            ),
        )
        val o = dashboard.call("POST", "/api/model/set", body) as? JsonObject
        if (o?.flag("confirm_required") == true) {
            return SlotSave.ConfirmExpensive(o.text("confirm_message") ?: "This model is expensive.")
        }
        if (o?.flag("ok") == false) throw java.io.IOException(o.text("error") ?: o.text("message") ?: "Hermes refused the model")
        return SlotSave.Saved
    }

    override suspend fun resetAuxiliary() {
        val body = JsonObject(
            mapOf(
                "scope" to JsonPrimitive("auxiliary"),
                "task" to JsonPrimitive("__reset__"),
                "provider" to JsonPrimitive("auto"),
                "model" to JsonPrimitive(""),
            ),
        )
        dashboard.call("POST", "/api/model/set", body)
    }

    override suspend fun moa(): MoaConfig = parseMoa(dashboard.call("GET", "/api/model/moa"))

    override suspend fun saveMoa(config: MoaConfig): MoaConfig =
        parseMoa(dashboard.call("PUT", "/api/model/moa", moaBody(config)))
}

internal fun parseAuxiliary(result: JsonElement): AuxiliaryModels {
    val o = result as? JsonObject ?: return AuxiliaryModels(emptyList(), "", "")
    val tasks = (o["tasks"] as? JsonArray).orEmpty().mapNotNull { row ->
        val r = row as? JsonObject ?: return@mapNotNull null
        val effective = r["effective"] as? JsonObject
        AuxiliaryTask(
            task = r.text("task") ?: return@mapNotNull null,
            provider = r.text("provider") ?: "auto",
            model = r.text("model").orEmpty(),
            baseUrl = r.text("base_url").orEmpty(),
            label = r.text("label"),
            hint = r.text("hint"),
            effectiveProvider = effective?.text("provider"),
            effectiveModel = effective?.text("model"),
        )
    }
    val main = o["main"] as? JsonObject
    return AuxiliaryModels(tasks, main?.text("provider").orEmpty(), main?.text("model").orEmpty())
}

/** Preset keys the save route takes besides the ones [MoaPreset] models. */
private val MOA_PASSTHROUGH = listOf(
    "reference_temperature", "aggregator_temperature", "reference_timeout", "degraded_reference_policy",
)

internal fun parseMoa(result: JsonElement): MoaConfig {
    val o = result as? JsonObject ?: return MoaConfig("default", "", emptyMap())
    val presets = (o["presets"] as? JsonObject).orEmpty().mapNotNull { (name, value) ->
        val p = value as? JsonObject ?: return@mapNotNull null
        name to MoaPreset(
            enabled = p.flag("enabled") != false,
            referenceModels = (p["reference_models"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toSlot() },
            aggregator = (p["aggregator"] as? JsonObject)?.toSlot() ?: MoaSlot("", ""),
            fanout = p.text("fanout") ?: "user_turn",
            extra = MOA_PASSTHROUGH.mapNotNull { key -> p[key]?.let { key to it } }.toMap(),
        )
    }.toMap()
    return MoaConfig(
        defaultPreset = o.text("default_preset") ?: presets.keys.firstOrNull() ?: "default",
        activePreset = o.text("active_preset").orEmpty(),
        presets = presets,
    )
}

internal fun moaBody(config: MoaConfig): JsonObject = JsonObject(
    mapOf(
        "default_preset" to JsonPrimitive(config.defaultPreset),
        "active_preset" to JsonPrimitive(config.activePreset),
        "presets" to JsonObject(
            config.presets.mapValues { (_, preset) ->
                JsonObject(
                    preset.extra + mapOf(
                        "enabled" to JsonPrimitive(preset.enabled),
                        "reference_models" to JsonArray(preset.referenceModels.map { it.toJson(withEnabled = true) }),
                        "aggregator" to preset.aggregator.toJson(withEnabled = false),
                        "fanout" to JsonPrimitive(preset.fanout),
                    ),
                )
            },
        ),
    ),
)

private fun JsonObject.toSlot(): MoaSlot = MoaSlot(
    provider = text("provider").orEmpty(),
    model = text("model").orEmpty(),
    enabled = flag("enabled") != false,
    reasoningEffort = text("reasoning_effort"),
)

private fun MoaSlot.toJson(withEnabled: Boolean): JsonObject = JsonObject(
    buildMap {
        put("provider", JsonPrimitive(provider))
        put("model", JsonPrimitive(model))
        // Sent back as read, so a level set elsewhere isn't wiped by this save.
        put("reasoning_effort", reasoningEffort?.let { JsonPrimitive(it) } ?: JsonNull)
        if (withEnabled) put("enabled", JsonPrimitive(enabled))
    },
)

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

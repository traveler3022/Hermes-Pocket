package com.hermes.android.domain.config

/**
 * The models Hermes uses besides the main one (`hermes_cli/web_routers/models.py`):
 * auxiliary tasks (vision, compression, titles, …) and the Mixture-of-Agents presets.
 * Both are written to config.yaml and apply to new sessions.
 */
interface ModelSlots {

    suspend fun auxiliary(): AuxiliaryModels

    /**
     * Pins [task] to [provider]/[model]; provider "auto" with a blank model hands it back to
     * Hermes' own choice. Asks first when Hermes warns the model is expensive.
     */
    suspend fun setAuxiliary(task: String, provider: String, model: String, confirmExpensive: Boolean): SlotSave

    /** Every task back to "auto". */
    suspend fun resetAuxiliary()

    suspend fun moa(): MoaConfig

    /** Saves the presets; Hermes refuses a preset without a complete reference model and aggregator. */
    suspend fun saveMoa(config: MoaConfig): MoaConfig
}

data class AuxiliaryModels(
    val tasks: List<AuxiliaryTask>,
    val mainProvider: String,
    val mainModel: String,
)

data class AuxiliaryTask(
    val task: String,
    /** "auto" when Hermes picks. */
    val provider: String,
    val model: String,
    val baseUrl: String,
    /** A plugin's own task: its label and hint; null for Hermes' built-in ones. */
    val label: String?,
    val hint: String?,
    /** For a plugin task that follows another slot while unpinned: the route it uses now. */
    val effectiveProvider: String?,
    val effectiveModel: String?,
) {
    val isAuto: Boolean get() = provider.isBlank() || provider == "auto"
}

sealed class SlotSave {
    data object Saved : SlotSave()

    /** Hermes warns about cost; save again with confirmExpensive to go ahead. */
    data class ConfirmExpensive(val message: String) : SlotSave()
}

data class MoaConfig(
    val defaultPreset: String,
    val activePreset: String,
    val presets: Map<String, MoaPreset>,
)

data class MoaPreset(
    val enabled: Boolean,
    val referenceModels: List<MoaSlot>,
    val aggregator: MoaSlot,
    /** `user_turn`, `per_iteration` or `every_n:<N>`. */
    val fanout: String,
    /** The preset's other fields as Hermes sent them, sent back unchanged on save. */
    val extra: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
)

data class MoaSlot(
    val provider: String,
    val model: String,
    val enabled: Boolean = true,
    val reasoningEffort: String? = null,
) {
    val isComplete: Boolean get() = provider.isNotBlank() && model.isNotBlank()
}

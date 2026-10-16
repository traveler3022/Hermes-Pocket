package com.hermes.android.domain.config

/** One key of Hermes' `.env` (API keys and other settings), as the dashboard's Keys page lists it. */
data class EnvKey(
    val key: String,
    val description: String,
    val url: String?,
    /** provider, tool, messaging, setting, custom, … as the server groups it. */
    val category: String,
    val isSet: Boolean,
    /** The server's redacted preview (first and last characters), never the value. */
    val preview: String?,
    val isSecret: Boolean,
    /** A key in no Hermes catalog: one the user added by hand. */
    val custom: Boolean,
)

/** The `.env` keys through the dashboard (`/api/env`): list, show one value, set, remove. */
interface EnvKeys {
    suspend fun list(): List<EnvKey>

    /** The real value of one key; the server allows only a few of these a minute. */
    suspend fun reveal(key: String): String

    suspend fun save(key: String, value: String)

    suspend fun remove(key: String)
}

/** One setting of config.yaml as the dashboard's form shows it; [key] is dotted (`agent.max_turns`). */
data class ConfigField(
    val key: String,
    val type: Type,
    val description: String,
    val category: String,
    /** The choices of a [Type.CHOICE] field. */
    val options: List<String>,
    /** The current value as text: `true`/`false`, a number, or JSON for a list. Empty when unset. */
    val value: String,
) {
    enum class Type { TEXT, NUMBER, FLAG, CHOICE, LIST }
}

data class ConfigForm(val fields: List<ConfigField>, val categoryOrder: List<String>)

data class RawConfig(val yaml: String, val path: String)

/** config.yaml through the dashboard: the schema form (`/api/config/schema` + `/api/config`) or the raw file. */
interface ConfigEditor {
    suspend fun form(): ConfigForm

    /** Saves only [changes] (dotted key → text as in [ConfigField.value]); the rest of the file stays. */
    suspend fun saveFields(fields: List<ConfigField>, changes: Map<String, String>)

    suspend fun raw(): RawConfig

    /** Replaces the whole file; the server refuses text that isn't a YAML mapping. */
    suspend fun saveRaw(yaml: String)
}

package com.hermes.android.data

import com.hermes.android.domain.config.ConfigEditor
import com.hermes.android.domain.config.ConfigField
import com.hermes.android.domain.config.ConfigForm
import com.hermes.android.domain.config.RawConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * config.yaml through the dashboard's Config page routes: the schema-driven form
 * (`/api/config/schema`, `/api/config`) and the raw YAML (`/api/config/raw`).
 */
@Singleton
class ConfigEditorRepository @Inject constructor(
    private val dashboard: DashboardApi,
    private val json: Json,
) : ConfigEditor {

    override suspend fun form(): ConfigForm {
        val schema = dashboard.call("GET", "/api/config/schema")
        val values = dashboard.call("GET", "/api/config")
        return parseConfigForm(schema, values)
    }

    override suspend fun saveFields(fields: List<ConfigField>, changes: Map<String, String>) {
        if (changes.isEmpty()) return
        val types = fields.associate { it.key to it.type }
        // PUT /api/config deep-merges what it is sent over the file, so only the changed keys go.
        val config = nestedConfig(changes.mapValues { (key, text) -> configValue(key, types[key] ?: ConfigField.Type.TEXT, text, json) })
        dashboard.call("PUT", "/api/config", buildJsonObject { put("config", config) })
    }

    override suspend fun raw(): RawConfig {
        val answer = dashboard.call("GET", "/api/config/raw") as? JsonObject
        fun text(key: String) = (answer?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
        return RawConfig(yaml = text("yaml"), path = text("path"))
    }

    override suspend fun saveRaw(yaml: String) {
        dashboard.call("PUT", "/api/config/raw", buildJsonObject { put("yaml_text", yaml) })
    }
}

/** `{fields: {dotted.key: {type, description, category, options}}, category_order}` + the config values. */
internal fun parseConfigForm(schema: JsonElement, values: JsonElement): ConfigForm {
    val root = schema as? JsonObject
    val fields = (root?.get("fields") as? JsonObject).orEmpty().mapNotNull { (key, element) ->
        val field = element as? JsonObject ?: return@mapNotNull null
        fun text(name: String) = (field[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val type = when (text("type")) {
            "boolean" -> ConfigField.Type.FLAG
            "number" -> ConfigField.Type.NUMBER
            "select" -> ConfigField.Type.CHOICE
            "list" -> ConfigField.Type.LIST
            else -> ConfigField.Type.TEXT
        }
        ConfigField(
            key = key,
            type = type,
            description = text("description"),
            category = text("category").ifBlank { "general" },
            options = (field["options"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            value = valueText(lookup(values, key)),
        )
    }
    val order = (root?.get("category_order") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    return ConfigForm(fields, order)
}

/** `a.b.c` in the nested config. */
private fun lookup(values: JsonElement, dotted: String): JsonElement? {
    var node: JsonElement? = values
    for (part in dotted.split('.')) node = (node as? JsonObject)?.get(part) ?: return null
    return node
}

private fun valueText(value: JsonElement?): String = when (value) {
    null, JsonNull -> ""
    is JsonPrimitive -> value.contentOrNull.orEmpty()
    else -> value.toString()
}

/** The text a field holds, as the JSON its schema type wants; throws [IllegalArgumentException] naming the key. */
internal fun configValue(key: String, type: ConfigField.Type, text: String, json: Json): JsonElement {
    val trimmed = text.trim()
    return when (type) {
        ConfigField.Type.FLAG -> JsonPrimitive(trimmed.toBooleanStrictOrNull() ?: throw IllegalArgumentException("$key: true or false"))
        ConfigField.Type.NUMBER -> when {
            trimmed.isEmpty() -> JsonNull
            trimmed.toLongOrNull() != null -> JsonPrimitive(trimmed.toLong())
            trimmed.toDoubleOrNull() != null -> JsonPrimitive(trimmed.toDouble())
            else -> throw IllegalArgumentException("$key: not a number")
        }
        ConfigField.Type.LIST -> when {
            trimmed.isEmpty() -> JsonArray(emptyList())
            trimmed.startsWith("[") -> runCatching { json.parseToJsonElement(trimmed) as JsonArray }
                .getOrElse { throw IllegalArgumentException("$key: not a JSON list") }
            // A plain list of names, one per line or comma separated.
            else -> JsonArray(trimmed.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }.map(::JsonPrimitive))
        }
        ConfigField.Type.TEXT, ConfigField.Type.CHOICE -> JsonPrimitive(text)
    }
}

/** `{"a.b": 1, "a.c": 2}` → `{"a": {"b": 1, "c": 2}}`. */
internal fun nestedConfig(flat: Map<String, JsonElement>): JsonObject {
    val root = mutableMapOf<String, Any>()
    for ((dotted, value) in flat) {
        val parts = dotted.split('.')
        // A key that is both a value and a section (never in Hermes' schema) is dropped, not misplaced.
        var node: MutableMap<String, Any>? = root
        for (part in parts.dropLast(1)) {
            @Suppress("UNCHECKED_CAST")
            node = node?.getOrPut(part) { mutableMapOf<String, Any>() } as? MutableMap<String, Any>
        }
        node?.put(parts.last(), value)
    }
    fun build(map: Map<String, Any>): JsonObject = JsonObject(map.mapValues { (_, v) ->
        @Suppress("UNCHECKED_CAST")
        if (v is JsonElement) v else build(v as Map<String, Any>)
    })
    return build(root)
}

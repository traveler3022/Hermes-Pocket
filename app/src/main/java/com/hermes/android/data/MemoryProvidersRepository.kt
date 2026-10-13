package com.hermes.android.data

import com.hermes.android.domain.memory.MemoryProviders
import com.hermes.android.domain.memory.MemoryProviders.ConfigField
import com.hermes.android.domain.memory.MemoryProviders.FieldKind
import com.hermes.android.domain.memory.MemoryProviders.InstallResult
import com.hermes.android.domain.memory.MemoryProviders.InstallStep
import com.hermes.android.domain.memory.MemoryProviders.Option
import com.hermes.android.domain.memory.MemoryProviders.Provider
import com.hermes.android.domain.memory.MemoryProviders.ProviderState
import com.hermes.android.domain.memory.MemoryProviders.Status
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memory through the dashboard's routes, the ones its Plugins and System pages use:
 * `GET /api/memory`, `GET|PUT /api/memory/providers/{name}/config`, `POST
 * /api/memory/providers/{name}/setup`, `PUT /api/memory/provider`, `POST /api/memory/reset`
 * (`hermes_cli/web_routers/memory_providers.py`, `ops.py`).
 */
@Singleton
class MemoryProvidersRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : MemoryProviders {

    override suspend fun status(): Status = parseMemoryStatus(dashboard.call("GET", "/api/memory"))

    override suspend fun config(name: String): List<ConfigField> =
        parseMemoryConfig(dashboard.call("GET", "/api/memory/providers/${name.urlPart()}/config"))

    override suspend fun saveAndActivate(name: String, values: Map<String, String>) {
        dashboard.call("PUT", "/api/memory/providers/${name.urlPart()}/config", valuesBody(values))
    }

    override suspend fun install(name: String, values: Map<String, String>): InstallResult =
        parseInstallResult(
            dashboard.call("POST", "/api/memory/providers/${name.urlPart()}/setup", valuesBody(values), slow = true),
        )

    override suspend fun turnOff() {
        dashboard.call("PUT", "/api/memory/provider", JsonObject(mapOf("provider" to JsonPrimitive(""))))
    }

    override suspend fun resetBuiltIn(target: String) {
        dashboard.call("POST", "/api/memory/reset", JsonObject(mapOf("target" to JsonPrimitive(target))))
    }

    private fun valuesBody(values: Map<String, String>) =
        JsonObject(mapOf("values" to JsonObject(values.mapValues { JsonPrimitive(it.value) })))

    private fun String.urlPart(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

/** `GET /api/memory`: `{active, providers: [...], builtin_files: {memory, user}}`. */
internal fun parseMemoryStatus(result: JsonElement): Status {
    val root = result as? JsonObject ?: JsonObject(emptyMap())
    val files = root["builtin_files"] as? JsonObject
    return Status(
        active = root.str("active"),
        providers = (root["providers"] as? JsonArray).orEmpty().mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val name = row.str("name").ifBlank { return@mapNotNull null }
            val setup = row["setup"] as? JsonObject
            Provider(
                name = name,
                description = row.str("description"),
                state = when (row.str("status")) {
                    "ready" -> ProviderState.READY
                    "needs_config" -> ProviderState.NEEDS_CONFIG
                    "missing" -> ProviderState.MISSING
                    else -> ProviderState.UNAVAILABLE
                },
                dependenciesInstalled = (setup?.get("dependencies_installed") as? JsonPrimitive)?.booleanOrNull != false,
                externalTools = (setup?.get("external_dependencies") as? JsonArray).orEmpty().mapNotNull {
                    (it as? JsonObject)?.str("install")?.takeIf(String::isNotBlank)
                },
                requiredEnv = (setup?.get("required_env") as? JsonArray).orEmpty().mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                },
            )
        },
        memoryFileBytes = files?.str("memory")?.toLongOrNull() ?: 0,
        userFileBytes = files?.str("user")?.toLongOrNull() ?: 0,
    )
}

/** `GET /api/memory/providers/{name}/config`: `{name, label, fields: [...]}`. */
internal fun parseMemoryConfig(result: JsonElement): List<ConfigField> =
    ((result as? JsonObject)?.get("fields") as? JsonArray).orEmpty().mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val key = row.str("key").ifBlank { return@mapNotNull null }
        ConfigField(
            key = key,
            label = row.str("label").ifBlank { key },
            kind = when (row.str("kind")) {
                "secret" -> FieldKind.SECRET
                "select" -> FieldKind.SELECT
                "boolean" -> FieldKind.BOOLEAN
                "integer" -> FieldKind.INTEGER
                "number" -> FieldKind.NUMBER
                else -> FieldKind.TEXT
            },
            description = row.str("description"),
            placeholder = row.str("placeholder"),
            required = (row["required"] as? JsonPrimitive)?.booleanOrNull == true,
            value = row.str("value"),
            isSet = (row["is_set"] as? JsonPrimitive)?.booleanOrNull == true,
            options = (row["options"] as? JsonArray).orEmpty().mapNotNull { option ->
                when (option) {
                    is JsonObject -> option.str("value").takeIf { it.isNotEmpty() }?.let {
                        Option(it, option.str("label").ifBlank { it })
                    }
                    is JsonPrimitive -> option.contentOrNull?.let { Option(it, it) }
                    else -> null
                }
            },
            url = row.str("url"),
            showWhen = (row["when"] as? JsonObject).orEmpty().mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
            }.toMap(),
        )
    }

/** `POST .../setup`: `{ok, results: [{kind, name, status, command, stdout, stderr}]}`. */
internal fun parseInstallResult(result: JsonElement): InstallResult {
    val root = result as? JsonObject ?: JsonObject(emptyMap())
    return InstallResult(
        ok = (root["ok"] as? JsonPrimitive)?.booleanOrNull == true,
        steps = (root["results"] as? JsonArray).orEmpty().mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            InstallStep(
                name = row.str("command").ifBlank { row.str("name") },
                status = row.str("status"),
                detail = row.str("stderr").ifBlank { row.str("stdout") }.trim().takeLast(600),
            )
        },
    )
}

/** A string, number or boolean as text; null, missing or an object is "". */
private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

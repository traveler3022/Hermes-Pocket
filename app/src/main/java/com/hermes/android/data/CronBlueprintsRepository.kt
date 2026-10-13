package com.hermes.android.data

import com.hermes.android.domain.cron.CronBlueprints
import com.hermes.android.domain.cron.CronBlueprints.Blueprint
import com.hermes.android.domain.cron.CronBlueprints.Field
import com.hermes.android.domain.cron.CronBlueprints.FieldType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blueprints through the dashboard's cron routes (`hermes_cli/web_routers/cron.py`):
 * `GET /api/cron/blueprints` lists them as form schemas, `POST
 * /api/cron/blueprints/instantiate` fills one in and creates the job. Like the dashboard,
 * a job made here delivers to the configured home channel when `deliver` is `origin`.
 */
@Singleton
class CronBlueprintsRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : CronBlueprints {

    override suspend fun list(): List<Blueprint> = parseBlueprints(dashboard.call("GET", "/api/cron/blueprints"))

    override suspend fun create(key: String, values: Map<String, String>) {
        val body = JsonObject(
            mapOf(
                "blueprint" to JsonPrimitive(key),
                "values" to JsonObject(values.mapValues { JsonPrimitive(it.value) }),
            ),
        )
        dashboard.call("POST", "/api/cron/blueprints/instantiate", body)
    }
}

/** Parses `{"blueprints": [...]}` (`blueprint_catalog_entry`); rows without a key are dropped. */
internal fun parseBlueprints(result: JsonElement): List<Blueprint> {
    val rows = ((result as? JsonObject)?.get("blueprints") as? JsonArray).orEmpty()
    return rows.mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val key = row.text("key").ifBlank { return@mapNotNull null }
        Blueprint(
            key = key,
            title = row.text("title").ifBlank { key },
            description = row.text("description"),
            category = row.text("category").ifBlank { "general" },
            scheduleHuman = row.text("scheduleHuman"),
            fields = (row["fields"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toField() },
        )
    }
}

private fun JsonObject.toField(): Field? {
    val name = text("name").ifBlank { return null }
    return Field(
        name = name,
        type = when (text("type")) {
            "time" -> FieldType.TIME
            "enum" -> FieldType.ENUM
            "weekdays" -> FieldType.WEEKDAYS
            else -> FieldType.TEXT
        },
        label = text("label").ifBlank { name },
        help = text("help"),
        default = text("default"),
        options = (this["options"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        optional = (this["optional"] as? JsonPrimitive)?.booleanOrNull == true,
        strict = (this["strict"] as? JsonPrimitive)?.booleanOrNull != false,
    )
}

/** A string or number field as text; null, missing or an object is "". */
private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

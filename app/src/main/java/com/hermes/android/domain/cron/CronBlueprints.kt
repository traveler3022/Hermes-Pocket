package com.hermes.android.domain.cron

/**
 * Hermes' automation blueprints (`cron/blueprint_catalog.py`): ready-made scheduled jobs
 * with a few blanks to fill (time, days, where to deliver, a topic…). Filling one in
 * creates an ordinary cron job.
 */
interface CronBlueprints {

    /** Built-in blueprints plus those of the profile's plugins. */
    suspend fun list(): List<Blueprint>

    /**
     * Creates the job from [key] with [values] (field name → value). Throws with the
     * server's reason when a value is refused (for example a malformed time).
     */
    suspend fun create(key: String, values: Map<String, String>)

    data class Blueprint(
        val key: String,
        val title: String,
        val description: String,
        val category: String,
        /** When it runs with the defaults, in English ("daily at 08:00"). */
        val scheduleHuman: String,
        val fields: List<Field>,
    )

    data class Field(
        val name: String,
        val type: FieldType,
        val label: String,
        val help: String,
        val default: String,
        val options: List<String>,
        val optional: Boolean,
        /** False: [options] are suggestions and any value is accepted. */
        val strict: Boolean,
    )

    enum class FieldType { TIME, ENUM, TEXT, WEEKDAYS }
}

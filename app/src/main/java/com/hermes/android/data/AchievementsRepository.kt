package com.hermes.android.data

import com.hermes.android.domain.achievements.Achievement
import com.hermes.android.domain.achievements.AchievementBoard
import com.hermes.android.domain.achievements.Achievements
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The achievements plugin's routes (plugins/hermes-achievements/dashboard/plugin_api.py):
 * `GET /achievements` answers at once, starting a background scan of the history when its
 * snapshot is old; `POST /rescan` scans now and answers when done. The built-in Linux
 * serves the same router in-process (android.dashboard).
 */
@Singleton
class AchievementsRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : Achievements {

    override suspend fun board(): AchievementBoard = parseAchievementBoard(dashboard.call("GET", "$BASE/achievements"))

    override suspend fun rescan(): AchievementBoard =
        parseAchievementBoard(dashboard.call("POST", "$BASE/rescan", JsonObject(emptyMap()), slow = true))

    private companion object {
        const val BASE = "/api/plugins/hermes-achievements"
    }
}

/** `{achievements: [...], unlocked_count, total_count, error, scan_meta: {status: {state}}}`. */
internal fun parseAchievementBoard(result: JsonElement): AchievementBoard {
    val root = result as? JsonObject ?: JsonObject(emptyMap())
    val items = (root["achievements"] as? JsonArray).orEmpty().mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val id = row.text("id") ?: return@mapNotNull null
        val state = when {
            row.flag("unlocked") -> Achievement.State.UNLOCKED
            row.text("state") == "secret" -> Achievement.State.SECRET
            else -> Achievement.State.DISCOVERED
        }
        Achievement(
            id = id,
            name = row.text("name") ?: id,
            description = row.text("description").orEmpty(),
            category = row.text("category") ?: "Other",
            state = state,
            tier = row.text("tier"),
            nextTier = row.text("next_tier"),
            progress = row.number("progress") ?: 0,
            nextThreshold = row.number("next_threshold"),
            percent = (row.number("progress_pct") ?: 0).toInt().coerceIn(0, 100),
            criteria = row.text("criteria").orEmpty(),
            unlockedAt = row.number("unlocked_at"),
        )
    }
    val status = ((root["scan_meta"] as? JsonObject)?.get("status") as? JsonObject)?.text("state")
    return AchievementBoard(
        items = items,
        unlocked = root.number("unlocked_count")?.toInt() ?: items.count { it.state == Achievement.State.UNLOCKED },
        total = root.number("total_count")?.toInt() ?: items.size,
        scanning = status == "running",
        error = root.text("error"),
    )
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.number(key: String): Long? =
    (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

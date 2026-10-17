package com.hermes.android.domain.achievements

/**
 * Hermes' achievements: badges earned from the session history (tool calls, models used,
 * late-night runs…), from the bundled `hermes-achievements` dashboard plugin. Each profile
 * has its own history, so its own badges.
 */
interface Achievements {

    /** The catalog with progress. While Hermes is still reading the history, [AchievementBoard.scanning] is true. */
    suspend fun board(): AchievementBoard

    /** Reads the whole session history again (slow on a long history). */
    suspend fun rescan(): AchievementBoard
}

data class AchievementBoard(
    val items: List<Achievement>,
    val unlocked: Int,
    val total: Int,
    /** Hermes is (re)reading the session history; the numbers may still grow. */
    val scanning: Boolean,
    /** Why the history could not be read, as Hermes says it. */
    val error: String?,
)

data class Achievement(
    val id: String,
    /** "???" for a secret one not yet seen. */
    val name: String,
    val description: String,
    /** "Agent Autonomy", "Vibe Coding"…, as Hermes names it. */
    val category: String,
    val state: State,
    /** Medal reached; null below the first. */
    val tier: Tier?,
    /** Next medal to reach; null at the top or for a one-step achievement. */
    val nextTier: Tier?,
    val progress: Long,
    val nextThreshold: Long?,
    /** 0–100 toward the next step. */
    val percent: Int,
    /** Hermes' own text of what earns it. */
    val criteria: String,
    /** Epoch seconds of the first unlock. */
    val unlockedAt: Long?,
) {
    enum class State { UNLOCKED, DISCOVERED, SECRET }
}

/** Hermes' medal ladder, lowest first, so the order of the entries is the order of rank. */
enum class Tier(val hermesName: String) {
    COPPER("Copper"),
    SILVER("Silver"),
    GOLD("Gold"),
    DIAMOND("Diamond"),
    OLYMPIAN("Olympian");

    companion object {
        /** Null for a name this app doesn't know, e.g. a rung a newer Hermes added. */
        fun fromHermes(name: String?): Tier? = entries.firstOrNull { it.hermesName.equals(name, ignoreCase = true) }
    }
}

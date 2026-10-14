package com.hermes.android.domain.memory

/**
 * What the agent learned: the skills it made or used (beyond the bundled ones) and its memory
 * entries, each with when it happened. Hermes calls it the learning graph (`/journey` in the
 * TUI). Each profile has its own.
 */
interface LearningJourney {

    suspend fun graph(): LearningGraph

    /** Full text of a node: the SKILL.md of a skill, the entry of a memory. */
    suspend fun detail(id: String): String

    /** Rewrites a node's text. */
    suspend fun edit(id: String, content: String): LearningResult

    /** A skill is archived (restorable with `hermes curator restore`); a memory entry is removed. */
    suspend fun delete(id: String): LearningResult
}

data class LearningGraph(
    val nodes: List<LearningNode>,
    /** Node id → ids it is linked to (skills that share topics, memories that mention a skill). */
    val links: Map<String, Set<String>>,
)

data class LearningNode(
    val id: String,
    val label: String,
    val kind: Kind,
    /** Skill category (devops, creative…), or "memory". */
    val category: String,
    /** Epoch seconds; null when Hermes doesn't know. */
    val timestamp: Long?,
    val useCount: Int,
    /** Skill lifecycle state (active, stale…). */
    val state: String,
    /** "agent", "user", "memory"…, as Hermes writes it. */
    val createdBy: String,
    val pinned: Boolean,
    /** For a memory: "memory" (MEMORY.md) or "profile" (USER.md). */
    val memorySource: String?,
) {
    enum class Kind { SKILL, MEMORY }
}

sealed interface LearningResult {
    /** [message] is Hermes' own (for a skill it names the restore command). */
    data class Done(val message: String) : LearningResult
    data class Failed(val message: String) : LearningResult
}

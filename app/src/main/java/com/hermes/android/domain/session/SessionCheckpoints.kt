package com.hermes.android.domain.session

/** Rollback for a LIVE session: git checkpoints of its cwd, and undoing the last turn. */
interface SessionCheckpoints {

    /** Checkpoints for a LIVE session's cwd; empty when rollback tracking is off. */
    suspend fun checkpoints(liveSessionId: String): List<Checkpoint>

    suspend fun checkpointDiff(liveSessionId: String, hash: String): CheckpointDiff

    /** [filePath] null = full-session restore (rejected server-side mid-turn). */
    suspend fun restoreCheckpoint(liveSessionId: String, hash: String, filePath: String? = null): RestoreResult

    /** Pops the last user+assistant/tool turn from a LIVE session. Returns count removed. */
    suspend fun undoLastTurn(liveSessionId: String): Int
}

data class Checkpoint(val hash: String, val timestamp: String, val message: String)

data class CheckpointDiff(val stat: String, val diff: String, val rendered: String?)

data class RestoreResult(val success: Boolean, val historyRemoved: Int)

package com.hermes.android.domain.session

/** Delegated work (the Task Desk) and subagent control. */
interface AgentTasks {

    /** Flat list of available models across authenticated providers, for the per-task picker. */
    suspend fun availableModels(): List<ModelChoice>

    /** Launches a titled session with one prompt, registered as a task; its live id. */
    suspend fun launchTask(
        title: String,
        prompt: String,
        reasoningEffort: String? = null,
        model: ModelChoice? = null,
    ): String

    /** Live sessions that belong to the Task Desk. */
    suspend fun activeTasks(): List<TaskRow>

    suspend fun taskHistory(): List<TaskHistoryRow>

    suspend fun finishedTasks(): List<TaskHistoryRow>

    suspend fun transcript(sessionId: String): List<TranscriptEntry>

    suspend fun interrupt(liveId: String)

    /** Tears the live worker down; the transcript stays in the server store. */
    suspend fun closeSession(liveId: String)

    suspend fun delegationStatus(): DelegationStatus

    /** Returns the new paused state. */
    suspend fun setDelegationPaused(paused: Boolean): Boolean
}

data class TaskRow(
    val id: String,
    val sessionKey: String,
    val title: String,
    val status: String,
    val preview: String,
    val model: String,
    val messageCount: Int,
    val lastActive: Double,
) {
    val isRunning: Boolean get() = com.hermes.android.gateway.GatewayEventHelpers.isBusySessionStatus(status)
}

/** A finished/idle task from the server's session store (history tab). */
data class TaskHistoryRow(
    val id: String,
    val title: String,
    val preview: String,
    val messageCount: Int,
)

/** One transcript line for the result sheet. */
data class TranscriptEntry(
    val role: String,
    val text: String,
)

/** A selectable model for a new task (provider slug + model id). */
data class ModelChoice(
    val provider: String,
    val modelId: String,
)

data class SubagentRow(
    val subagentId: String,
    val goal: String,
    val model: String,
    val depth: Int,
    val toolCount: Int,
    val status: String,
)

data class DelegationStatus(
    val active: List<SubagentRow>,
    val paused: Boolean,
    val maxSpawnDepth: Int,
    val maxConcurrentChildren: Int,
)

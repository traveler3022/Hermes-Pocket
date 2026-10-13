package com.hermes.android.domain.kanban

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Hermes Kanban on the user's server: a task board shared by every profile. A task assigned to a
 * profile is run by the server's dispatcher as that profile's worker, which reports back on the card.
 */
interface KanbanRepository {
    data class Task(
        val id: String,
        val title: String,
        val body: String,
        val assignee: String?,
        val status: String,
        val priority: Int,
        val createdAt: Long,
        /** The worker's latest hand-off note (a preview on the board, in full on the task). */
        val summary: String,
        val result: String,
        val lastError: String,
        val commentCount: Int,
        /** Done / all of the tasks this one was split into; null when it has none. */
        val progress: Pair<Int, Int>?,
        /** The model this task runs on instead of its profile's; null: the profile's. */
        val modelOverride: String? = null,
        val providerOverride: String? = null,
        /** How hard the worker thinks (`none`, `low` … `ultra`); null: the profile's level. */
        val reasoningEffort: String? = null,
    )

    /** A rough guess by the server's model of what a task will take. */
    data class Estimate(val tokens: Long, val complexity: String?, val rationale: String)

    /** A provider and the models a task can be set to run on. */
    data class ModelProvider(val slug: String, val label: String, val models: List<String>)

    /** One sign of trouble on a task (a worker that keeps dying, a stuck claim…). */
    data class Diagnostic(val severity: String, val title: String, val detail: String)

    /** A task with trouble, and what the server sees wrong with it, worst first. */
    data class TaskDiagnostics(val taskId: String, val title: String, val status: String, val diagnostics: List<Diagnostic>)

    /** A messenger's home channel (Telegram, Discord…) that can get this task's news. */
    data class HomeChannel(val platform: String, val name: String, val subscribed: Boolean)

    /** One of the server's boards (each its own database; the dispatcher runs them all). */
    data class Board(val slug: String, val name: String, val icon: String, val total: Int, val isCurrent: Boolean)

    /**
     * How the server splits and hands out work (`kanban:` in its config.yaml). Empty profile
     * names mean "not set": the server then uses [activeProfile].
     */
    data class Orchestration(
        /** The profile that splits a triage task into steps. */
        val orchestratorProfile: String,
        /** Who gets a step the split could not match to a profile. */
        val defaultAssignee: String,
        /** Split new triage tasks on their own. */
        val autoDecompose: Boolean,
        /** Start the next steps when the ones they wait on finish. */
        val autoPromoteChildren: Boolean,
        val activeProfile: String,
    )

    data class Comment(val author: String, val body: String, val createdAt: Long)

    /** One attempt at a task: opened when a worker claims it, closed when it ends. */
    data class Run(
        val id: Long,
        val profile: String?,
        val status: String,
        val outcome: String?,
        val startedAt: Long,
        /** Null while the worker is still on it. */
        val endedAt: Long?,
        val summary: String,
        val error: String,
    )

    /** A file on the task: the worker's full report is often one (the hand-off note only points to it). */
    data class Attachment(val id: Long, val filename: String, val size: Long, val storedPath: String)

    /** A task this one is linked to, by its title. */
    data class LinkedTask(val id: String, val title: String, val status: String)

    data class Detail(
        val task: Task,
        val comments: List<Comment>,
        val runs: List<Run> = emptyList(),
        val attachments: List<Attachment> = emptyList(),
        /** Tasks this one waits on: it starts once they are done. */
        val parents: List<LinkedTask> = emptyList(),
        /** Tasks that wait on this one. */
        val children: List<LinkedTask> = emptyList(),
    )

    /** The end of the worker's stdout/stderr; [exists] is false until a worker has started. */
    data class Log(val text: String, val exists: Boolean, val truncated: Boolean)

    /** The live worker process of an open run, as the server sees it right now. */
    data class Inspection(
        val alive: Boolean,
        val pid: Long?,
        val cpuPercent: Double?,
        val memoryBytes: Long?,
        /** Why there are no numbers (run ended, process gone, no access). */
        val reason: String,
    )

    /**
     * Turned on in Settings › General ([KanbanSwitch]), and a runtime that has it: a remote
     * server over https (its dashboard), or the built-in Linux, whose gateway then answers
     * `android.kanban` (runtime/linux/LinuxKanban). Termux has neither.
     */
    val available: Boolean

    /** The Settings › General switch, for screens that must follow it live. */
    val switchedOn: StateFlow<Boolean>

    /**
     * The board this app reads and writes, as the dashboard does per tab (`?board=`); null is the
     * server's current board. Kept across restarts, per server ([restoreBoard]).
     */
    val selectedBoard: StateFlow<String?>

    fun selectBoard(slug: String?)

    /**
     * Picks up the board last chosen for the server now connected. One that is gone from
     * [boards] is forgotten (the server would answer 404 for it).
     */
    fun restoreBoard(boards: List<Board>)

    /** Every live board, with its open-task count and which one the server calls current. */
    suspend fun boards(): List<Board>

    /** Adds a board named [name] and returns its slug (the server's id: lowercase latin only). */
    suspend fun createBoard(name: String): String

    /** A remote server streams board changes ([changes]); the phone's own gateway has no socket for it. */
    val hasLiveChanges: Boolean

    /**
     * The server's Kanban event stream (`/events`, a WebSocket of `task_events` rows): emits once
     * when it opens and once per batch of changes. Without a cursor the server starts at the
     * current tail, so nothing old is replayed. Ends with an [IOException] when the socket drops.
     */
    fun changes(): Flow<Unit>

    /** With [includeArchived], an `archived` column comes too. */
    suspend fun board(includeArchived: Boolean = false): Map<String, List<Task>>

    suspend fun task(id: String): Detail

    /** The last [LOG_TAIL_BYTES] of the task's worker log (the server keeps up to 2 MiB). */
    suspend fun log(id: String): Log

    /** CPU and memory of the worker behind an open run. */
    suspend fun inspect(runId: Long): Inspection

    /**
     * Stops a running task's worker and releases its claim: the task goes back to where it
     * came from (usually ready) with a fresh retry budget, so the dispatcher starts it again.
     * The server refuses a task that isn't running.
     */
    suspend fun reclaim(id: String, reason: String)

    /**
     * Gives the task to [profile] (null: nobody). A running task is refused unless
     * [stopWorker] first releases it from the current worker.
     */
    suspend fun reassign(id: String, profile: String?, stopWorker: Boolean)

    /** Profiles a task can go to: every profile on the server plus any already used on the board. */
    suspend fun assignees(): List<String>

    /**
     * Adds a task. With [assignee] it is ready for that profile's worker; without one it goes
     * to triage, where the server's decomposer splits it into tasks for the right profiles
     * (`kanban.auto_decompose`, on by default).
     */
    suspend fun create(title: String, body: String, assignee: String?)

    /**
     * Moves a task: `ready` (also unblocks and reopens a review), `blocked` with [reason],
     * `done`, `archived`. The server refuses moves its rules forbid and says why.
     */
    suspend fun move(id: String, status: String, reason: String? = null)

    suspend fun comment(id: String, text: String)

    suspend fun delete(id: String)

    /**
     * The same change on several tasks: a new [status] (`ready`, `done`), [archive], or a new
     * [assignee] ("" for nobody; null leaves it). Each task is changed on its own, so some can be
     * refused; returns those as id → the server's reason.
     */
    suspend fun bulk(
        ids: Collection<String>,
        status: String? = null,
        assignee: String? = null,
        archive: Boolean = false,
    ): Map<String, String>

    suspend fun orchestration(): Orchestration

    /** Saves all four settings; the server refuses a profile that doesn't exist. */
    suspend fun saveOrchestration(settings: Orchestration): Orchestration

    /** Adds [file] to the task under [name]; the worker reads it from the server's disk. */
    suspend fun upload(taskId: String, file: File, name: String, mimeType: String?)

    suspend fun deleteAttachment(id: Long)

    /** The server model's guess of what the task will take; fails with the server's reason. */
    suspend fun estimate(id: String): Estimate

    /** Providers and models the server can run a task on (the dashboard's override list). */
    suspend fun modelOptions(): List<ModelProvider>

    /**
     * Sets what the task runs on: [model] on [provider] (null model: back to the profile's), and
     * [effort] (null: back to the profile's level; "none" turns thinking off).
     */
    suspend fun setRunSettings(id: String, model: String?, provider: String?, effort: String?)

    /** Tasks on the board with trouble, worst first. */
    suspend fun diagnostics(): List<TaskDiagnostics>

    /** The messengers with a home channel, and whether this task's news goes to each. */
    suspend fun homeChannels(taskId: String): List<HomeChannel>

    suspend fun setHomeChannel(taskId: String, platform: String, on: Boolean)

    /** Writes [slug] to an archive on the server (with its files) and returns that server path. */
    suspend fun exportBoard(slug: String): String

    /**
     * Puts the board archive [file] on the server and imports it as a new board; returns its slug.
     * The server imports from its own disk, so the file goes there first.
     */
    suspend fun importBoard(file: File): String

    /** Makes [childId] wait on [parentId]: it starts once that one is done. The server refuses a cycle. */
    suspend fun link(parentId: String, childId: String)

    suspend fun unlink(parentId: String, childId: String)

    /**
     * Has the server's model write a triage task out in full (title, details) and move it to
     * the queue. Takes a while: it waits on the model. Null when done, else the server's reason.
     */
    suspend fun specify(id: String): String?
}

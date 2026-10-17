package com.hermes.android.domain.ops

import java.io.File

/**
 * The dashboard's maintenance routes (`hermes_cli/web_routers/ops.py` and the diagnostics in
 * `status.py`): CLI commands Hermes runs in the background, whose output is read back from
 * `/api/actions/<name>/status`, plus a few direct reads (checkpoints, shell hooks) and the
 * debug-report upload.
 */
interface ServerOps {

    /** Starts [action] on the machine Hermes runs on. */
    suspend fun start(action: OpsAction)

    /** The latest run of [action]: its output so far and whether it is still going. */
    suspend fun status(action: OpsAction): ActionRun

    /**
     * Starts `hermes backup` into the profile's `backups/` folder; returns the archive's path
     * on that machine, for [downloadBackup] once the run is done.
     */
    suspend fun startBackup(): String

    /** Copies the finished [archive] to the phone's Download/Hermes; returns where it went. */
    suspend fun downloadBackup(archive: String): String

    /** Puts the backup zip [file] on the machine and starts `hermes import --force` on it. */
    suspend fun startRestore(file: File)

    /** Uploads a redacted debug report and the logs; answers the links to share. */
    suspend fun debugShare(): DebugShare

    suspend fun checkpoints(): Checkpoints

    suspend fun hooks(): Hooks

    /** Adds a shell hook; with [approve] it may run without asking. Takes effect on the next session. */
    suspend fun addHook(event: String, command: String, matcher: String?, approve: Boolean)

    suspend fun removeHook(event: String, command: String)
}

/** A background command, by the action name the status route knows it by. */
enum class OpsAction(val key: String, val startPath: String) {
    DOCTOR("doctor", "/api/ops/doctor"),
    SECURITY_AUDIT("security-audit", "/api/ops/security-audit"),
    PROMPT_SIZE("prompt-size", "/api/ops/prompt-size"),
    DUMP("dump", "/api/ops/dump"),
    CONFIG_MIGRATE("config-migrate", "/api/ops/config-migrate"),
    CHECKPOINTS_PRUNE("checkpoints-prune", "/api/ops/checkpoints/prune"),
    BACKUP("backup", "/api/ops/backup"),
    RESTORE("import", "/api/ops/import"),
}

/** [lines] are this run's output only; [exitCode] is null while it runs or when unknown. */
data class ActionRun(
    val running: Boolean,
    val exitCode: Int?,
    val lines: List<String>,
)

data class DebugShare(
    /** Label (report, agent log, …) → paste link. */
    val urls: List<Pair<String, String>>,
    val failures: List<String>,
    val redacted: Boolean,
    /** The paste service deletes the links after this; null when it keeps them. */
    val autoDeleteSeconds: Long?,
)

data class Checkpoints(
    val sessions: List<CheckpointSession>,
    val totalBytes: Long,
)

data class CheckpointSession(val session: String, val files: Int, val bytes: Long)

data class Hooks(
    val hooks: List<ShellHook>,
    /** Events a new hook may listen to. */
    val validEvents: List<String>,
)

data class ShellHook(
    val event: String,
    val command: String,
    val matcher: String?,
    val timeout: Int?,
    /** Consent recorded: the hook runs without asking. */
    val allowed: Boolean,
    val executable: Boolean,
)

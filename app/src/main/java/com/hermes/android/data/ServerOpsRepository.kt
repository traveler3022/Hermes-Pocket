package com.hermes.android.data

import com.hermes.android.domain.files.Downloads
import com.hermes.android.domain.ops.ActionRun
import com.hermes.android.domain.ops.CheckpointSession
import com.hermes.android.domain.ops.Checkpoints
import com.hermes.android.domain.ops.DebugShare
import com.hermes.android.domain.ops.Hooks
import com.hermes.android.domain.ops.OpsAction
import com.hermes.android.domain.ops.ServerOps
import com.hermes.android.domain.ops.ShellHook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ServerOps] over the dashboard API ([DashboardApi]); the built-in Linux serves the same
 * routers in-process. Backup files travel over HTTP on a server and are copied in place
 * on the built-in Linux, whose disk is the phone's.
 */
@Singleton
class ServerOpsRepository @Inject constructor(
    private val dashboard: DashboardApi,
    private val downloads: Downloads,
) : ServerOps {

    override suspend fun start(action: OpsAction) {
        dashboard.call("POST", action.startPath, JsonObject(emptyMap()))
    }

    override suspend fun status(action: OpsAction): ActionRun {
        val o = dashboard.call("GET", "/api/actions/${action.key}/status?lines=400") as? JsonObject
            ?: return ActionRun(running = false, exitCode = null, lines = emptyList())
        val lines = (o["lines"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return ActionRun(
            running = o.bool("running") == true,
            exitCode = (o["exit_code"] as? JsonPrimitive)?.intOrNull,
            lines = latestRun(lines, action.key),
        )
    }

    override suspend fun startBackup(): String {
        val o = dashboard.call("POST", OpsAction.BACKUP.startPath, JsonObject(emptyMap())) as? JsonObject
        return o?.str("archive") ?: throw IOException("Hermes started no backup")
    }

    override suspend fun downloadBackup(archive: String): String {
        val name = archive.substringAfterLast('/')
        if (dashboard.inProcess) {
            val local = dashboard.phoneFile(archive) ?: throw IOException("The built-in Linux isn't ready")
            if (!local.isFile) throw IOException("Backup not found")
            return downloads.save(local.toURI().toString(), name) { local }
        }
        val temp = withContext(Dispatchers.IO) { File.createTempFile("hermes-backup", ".zip") }
        try {
            dashboard.download("/api/ops/backup/download?archive=" + URLEncoder.encode(archive, "UTF-8"), temp)
            return downloads.save(archive, name) { temp }
        } finally {
            temp.delete()
        }
    }

    override suspend fun startRestore(file: File) {
        if (dashboard.inProcess) {
            val guest = "/tmp/hermes-restore-${UUID.randomUUID().toString().take(8)}.zip"
            val local = dashboard.phoneFile(guest) ?: throw IOException("The built-in Linux isn't ready")
            withContext(Dispatchers.IO) { file.copyTo(local, overwrite = true) }
            val body = JsonObject(mapOf("archive" to JsonPrimitive(guest), "force" to JsonPrimitive(true)))
            dashboard.call("POST", OpsAction.RESTORE.startPath, body)
            return
        }
        dashboard.upload("/api/ops/import-upload", file, "hermes-backup.zip", mapOf("force" to "true"))
    }

    override suspend fun debugShare(): DebugShare {
        val body = JsonObject(mapOf("redact" to JsonPrimitive(true)))
        val o = dashboard.call("POST", "/api/ops/debug-share", body, slow = true) as? JsonObject
            ?: throw IOException("No answer from Hermes")
        return parseDebugShare(o)
    }

    override suspend fun checkpoints(): Checkpoints =
        parseCheckpoints(dashboard.call("GET", "/api/ops/checkpoints"))

    override suspend fun hooks(): Hooks = parseHooks(dashboard.call("GET", "/api/ops/hooks"))

    override suspend fun addHook(event: String, command: String, matcher: String?, approve: Boolean) {
        val body = buildMap<String, JsonElement> {
            put("event", JsonPrimitive(event))
            put("command", JsonPrimitive(command))
            if (!matcher.isNullOrBlank()) put("matcher", JsonPrimitive(matcher))
            put("approve", JsonPrimitive(approve))
        }
        dashboard.call("POST", "/api/ops/hooks", JsonObject(body))
    }

    override suspend fun removeHook(event: String, command: String) {
        val body = JsonObject(mapOf("event" to JsonPrimitive(event), "command" to JsonPrimitive(command)))
        dashboard.call("DELETE", "/api/ops/hooks", body)
    }
}

/**
 * The status route tails an append-only log every run of [name] writes to; a run starts
 * with `=== <name> started … ===`. Keeps the lines after the last such header, without
 * the `=== … ===` markers themselves.
 */
internal fun latestRun(lines: List<String>, name: String): List<String> {
    val start = lines.indexOfLast { it.trim().startsWith("=== $name started") }
    return lines.drop(start + 1).filterNot { val t = it.trim(); t.startsWith("=== ") && t.endsWith(" ===") }
        .dropWhile { it.isBlank() }
}

internal fun parseDebugShare(o: JsonObject): DebugShare = DebugShare(
    urls = (o["urls"] as? JsonObject).orEmpty().mapNotNull { (label, url) ->
        (url as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { label to it }
    },
    failures = (o["failures"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
    redacted = o.bool("redacted") != false,
    autoDeleteSeconds = (o["auto_delete_seconds"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 },
)

internal fun parseCheckpoints(result: JsonElement): Checkpoints {
    val o = result as? JsonObject ?: return Checkpoints(emptyList(), 0)
    val sessions = (o["sessions"] as? JsonArray).orEmpty().mapNotNull { row ->
        val r = row as? JsonObject ?: return@mapNotNull null
        CheckpointSession(
            session = r.str("session") ?: return@mapNotNull null,
            files = (r["files"] as? JsonPrimitive)?.intOrNull ?: 0,
            bytes = (r["bytes"] as? JsonPrimitive)?.longOrNull ?: 0,
        )
    }
    return Checkpoints(sessions, (o["total_bytes"] as? JsonPrimitive)?.longOrNull ?: sessions.sumOf { it.bytes })
}

internal fun parseHooks(result: JsonElement): Hooks {
    val o = result as? JsonObject ?: return Hooks(emptyList(), emptyList())
    val hooks = (o["hooks"] as? JsonArray).orEmpty().mapNotNull { row ->
        val r = row as? JsonObject ?: return@mapNotNull null
        ShellHook(
            event = r.str("event") ?: return@mapNotNull null,
            command = r.str("command") ?: return@mapNotNull null,
            matcher = r.str("matcher"),
            timeout = (r["timeout"] as? JsonPrimitive)?.intOrNull,
            allowed = r.bool("allowed") == true,
            executable = r.bool("executable") == true,
        )
    }
    val events = (o["valid_events"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    return Hooks(hooks, events)
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

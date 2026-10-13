package com.hermes.android.data

import android.content.Context
import com.hermes.android.domain.kanban.KanbanRepository
import com.hermes.android.domain.kanban.KanbanRepository.Attachment
import com.hermes.android.domain.kanban.KanbanRepository.Board
import com.hermes.android.domain.kanban.KanbanRepository.Comment
import com.hermes.android.domain.kanban.KanbanRepository.Detail
import com.hermes.android.domain.kanban.KanbanRepository.Diagnostic
import com.hermes.android.domain.kanban.KanbanRepository.Estimate
import com.hermes.android.domain.kanban.KanbanRepository.HomeChannel
import com.hermes.android.domain.kanban.KanbanRepository.Inspection
import com.hermes.android.domain.kanban.KanbanRepository.LinkedTask
import com.hermes.android.domain.kanban.KanbanRepository.Log
import com.hermes.android.domain.kanban.KanbanRepository.ModelProvider
import com.hermes.android.domain.kanban.KanbanRepository.Orchestration
import com.hermes.android.domain.kanban.KanbanRepository.Run
import com.hermes.android.domain.kanban.KanbanRepository.Task
import com.hermes.android.domain.kanban.KanbanRepository.TaskDiagnostics
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import com.hermes.android.runtime.KanbanSwitch
import com.hermes.android.runtime.remote.RemoteServerSettings
import com.hermes.android.runtime.remote.remoteHttpBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hermes Kanban on the user's server: a task board shared by every profile
 * (`~/.hermes/kanban.db`). A task assigned to a profile is picked up by the dispatcher in
 * the server's gateway (`hermes gateway`, every 60 s), which runs that profile as its own
 * worker; the worker reports back on the card.
 *
 * Read and written through the dashboard's Kanban plugin API
 * (`plugins/kanban/dashboard/plugin_api.py`, mounted at `/api/plugins/kanban`) on a remote
 * server, or the same router run by the phone's own gateway (see [available]). The shared HTTP client
 * signs each call in (RemoteAuthInterceptor) and reaches a tailnet server through the
 * in-app Tailscale node, as for the chat connection itself.
 */
@Singleton
class ServerKanbanRepository @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val runtime: HermesRuntime,
    private val remote: RemoteServerSettings,
    private val gatewayClient: GatewayClient,
    private val switch: KanbanSwitch,
    @ApplicationContext context: Context,
) : KanbanRepository {
    /** The app's client (sign-in, Tailscale route) with a deadline: it has none for its WebSocket. */
    private val rest = http.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    /** For a call that waits on the server's model (specify). */
    private val modelCalls = rest.newBuilder()
        .readTimeout(3, TimeUnit.MINUTES)
        .callTimeout(4, TimeUnit.MINUTES)
        .build()

    /**
     * Turned on in Settings › General ([KanbanSwitch]), and a runtime that has it: a remote
     * server over https (its dashboard), or the built-in Linux, whose gateway then answers
     * `android.kanban` (runtime/linux/LinuxKanban). Termux has neither.
     */
    override val available: Boolean
        get() = switch.on.value && when (runtime.type) {
            RuntimeType.REMOTE -> base() != null
            RuntimeType.PROOT_LINUX -> true
            else -> false
        }

    /** The Settings › General switch, for screens that must follow it live. */
    override val switchedOn: StateFlow<Boolean> get() = switch.on

    private val onPhone: Boolean get() = runtime.type == RuntimeType.PROOT_LINUX

    private val prefs = context.getSharedPreferences("kanban", Context.MODE_PRIVATE)

    /** Each server (and the built-in Linux) has its own boards, so the pick is kept per place. */
    private val boardKey: String
        get() = "board:" + if (onPhone) "linux" else remote.config.value.serverUrl

    private val _selectedBoard = MutableStateFlow<String?>(null)

    /**
     * The board this app reads and writes, as the dashboard does per tab (`?board=`); null is the
     * server's current board. Kept across restarts, per server ([restoreBoard]).
     */
    override val selectedBoard: StateFlow<String?> = _selectedBoard.asStateFlow()

    override fun selectBoard(slug: String?) {
        _selectedBoard.value = slug
        prefs.edit().apply { if (slug == null) remove(boardKey) else putString(boardKey, slug) }.apply()
    }

    /**
     * Picks up the board last chosen for the server now connected. One that is gone from
     * [boards] is forgotten (the server would answer 404 for it).
     */
    override fun restoreBoard(boards: List<Board>) {
        val saved = prefs.getString(boardKey, null)
        val slug = saved?.takeIf { s -> boards.any { it.slug == s } }
        if (saved != null && slug == null) prefs.edit().remove(boardKey).apply()
        _selectedBoard.value = slug
    }

    /** Every live board, with its open-task count and which one the server calls current. */
    override suspend fun boards(): List<Board> {
        val root = call("GET", "/boards") as? JsonObject ?: return emptyList()
        return (root["boards"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { row ->
            val slug = row.str("slug").ifEmpty { return@mapNotNull null }
            Board(
                slug = slug,
                name = row.str("name").ifBlank { slug },
                icon = row.str("icon"),
                total = row.long("total").toInt(),
                isCurrent = row.bool("is_current"),
            )
        }
    }

    /** Adds a board named [name] and returns its slug (the server's id: lowercase latin only). */
    override suspend fun createBoard(name: String): String {
        val slug = boardSlug(name)
        val payload = JsonObject(mapOf("slug" to JsonPrimitive(slug), "name" to JsonPrimitive(name.trim())))
        val created = call("POST", "/boards", payload) as? JsonObject
        return (created?.get("board") as? JsonObject)?.str("slug")?.ifEmpty { null } ?: slug
    }

    /** A remote server streams board changes ([changes]); the phone's own gateway has no socket for it. */
    override val hasLiveChanges: Boolean get() = !onPhone && base() != null

    /**
     * The server's Kanban event stream (`/events`, a WebSocket of `task_events` rows): emits once
     * when it opens and once per batch of changes. Without a cursor the server starts at the
     * current tail, so nothing old is replayed. Ends with an [IOException] when the socket drops.
     */
    override fun changes(): Flow<Unit> = callbackFlow {
        val base = base() ?: throw IOException("Kanban needs a connected server.")
        val url = base.replaceFirst("https://", "wss://") + API + scoped("/events")
        // The shared client: sign-in ticket (RemoteAuthInterceptor), Tailscale route, ping.
        val socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                trySend(Unit)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                close(IOException("Kanban stream closed ($code)"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(IOException("Kanban stream failed: ${t.message}", t))
            }
        })
        awaitClose { socket.cancel() }
    }

    /** The board, column name → tasks, in the server's column order. */
    /** With [includeArchived], an `archived` column comes too. */
    override suspend fun board(includeArchived: Boolean): Map<String, List<Task>> {
        val root = call("GET", if (includeArchived) "/board?include_archived=true" else "/board") as? JsonObject
            ?: return emptyMap()
        return (root["columns"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.associate { column ->
            column.str("name") to (column["tasks"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toTask() }
        }
    }

    override suspend fun task(id: String): Detail {
        val root = call("GET", "/tasks/${id.urlPart()}") as? JsonObject ?: throw IOException("No task $id")
        val task = (root["task"] as? JsonObject)?.toTask() ?: throw IOException("No task $id")
        val comments = (root["comments"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
            Comment(author = it.str("author"), body = it.str("body"), createdAt = it.long("created_at"))
        }
        val runs = (root["runs"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { it.toRun() }
        val attachments = (root["attachments"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { row ->
            val path = row.str("stored_path").ifEmpty { return@mapNotNull null }
            Attachment(
                id = row.long("id"),
                filename = row.str("filename").ifEmpty { path.substringAfterLast('/') },
                size = row.long("size"),
                storedPath = path,
            )
        }
        // Titles come in link_tasks; a linked task gone from the board keeps its id.
        val titles = (root["link_tasks"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .associateBy { it.str("id") }
        val links = root["links"] as? JsonObject
        fun linked(key: String) = (links?.get(key) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .map { id -> titles[id].let { LinkedTask(id, it?.str("title")?.ifEmpty { null } ?: id, it?.str("status").orEmpty()) } }
        return Detail(task, comments, runs, attachments, parents = linked("parents"), children = linked("children"))
    }

    /** The last [LOG_TAIL_BYTES] of the task's worker log (the server keeps up to 2 MiB). */
    override suspend fun log(id: String): Log {
        val root = call("GET", "/tasks/${id.urlPart()}/log?tail=$LOG_TAIL_BYTES") as? JsonObject
            ?: return Log("", exists = false, truncated = false)
        return Log(
            text = root.str("content"),
            exists = root.bool("exists"),
            truncated = root.bool("truncated"),
        )
    }

    /** CPU and memory of the worker behind an open run. */
    override suspend fun inspect(runId: Long): Inspection {
        val root = call("GET", "/runs/$runId/inspect") as? JsonObject ?: throw IOException("No run $runId")
        return Inspection(
            alive = root.bool("alive") && root.str("error").isEmpty(),
            pid = root.str("pid").toLongOrNull(),
            cpuPercent = root.str("cpu_percent").toDoubleOrNull(),
            memoryBytes = root.str("memory_rss_bytes").toDoubleOrNull()?.toLong(),
            reason = root.str("reason").ifEmpty { root.str("error") },
        )
    }

    /**
     * Stops a running task's worker and releases its claim: the task goes back to where it
     * came from (usually ready) with a fresh retry budget, so the dispatcher starts it again.
     * The server refuses a task that isn't running.
     */
    override suspend fun reclaim(id: String, reason: String) {
        call("POST", "/tasks/${id.urlPart()}/reclaim", JsonObject(mapOf("reason" to JsonPrimitive(reason))))
        dispatchNow()
    }

    /**
     * Gives the task to [profile] (null: nobody). A running task is refused unless
     * [stopWorker] first releases it from the current worker.
     */
    override suspend fun reassign(id: String, profile: String?, stopWorker: Boolean) {
        val payload = buildMap<String, JsonElement> {
            put("profile", profile?.let { JsonPrimitive(it) } ?: JsonNull)
            put("reclaim_first", JsonPrimitive(stopWorker))
            put("reason", JsonPrimitive("Reassigned from the Hermes app"))
        }
        call("POST", "/tasks/${id.urlPart()}/reassign", JsonObject(payload))
        if (profile != null) dispatchNow()
    }

    /** Profiles a task can go to: every profile on the server plus any already used on the board. */
    override suspend fun assignees(): List<String> {
        val root = call("GET", "/assignees") as? JsonObject ?: return emptyList()
        // Rows are `{name, on_disk, counts}`; a bare name is accepted too.
        return (root["assignees"] as? JsonArray).orEmpty().mapNotNull { row ->
            (row as? JsonPrimitive)?.contentOrNull ?: (row as? JsonObject)?.str("name")?.ifEmpty { null }
        }
    }

    /**
     * Adds a task. With [assignee] it is ready for that profile's worker; without one it goes
     * to triage, where the server's decomposer splits it into tasks for the right profiles
     * (`kanban.auto_decompose`, on by default).
     */
    override suspend fun create(title: String, body: String, assignee: String?) {
        val payload = buildMap<String, JsonElement> {
            put("title", JsonPrimitive(title.trim()))
            if (body.isNotBlank()) put("body", JsonPrimitive(body.trim()))
            if (assignee != null) put("assignee", JsonPrimitive(assignee)) else put("triage", JsonPrimitive(true))
            // A resend after a dropped answer finds the first copy instead of making a second.
            put("idempotency_key", JsonPrimitive("app-${UUID.randomUUID()}"))
        }
        val created = call("POST", "/tasks", JsonObject(payload)) as? JsonObject
        when {
            assignee != null -> dispatchNow()
            // A server's dispatcher splits triage tasks on its own; the phone's stand-in only
            // dispatches, so it asks for the split here.
            onPhone -> ((created?.get("task") as? JsonObject)?.str("id"))?.takeIf { it.isNotEmpty() }?.let { id ->
                call("POST", "/tasks/${id.urlPart()}/decompose", JsonObject(emptyMap()))
                dispatchNow()
            }
        }
    }

    /**
     * Moves a task: `ready` (also unblocks and reopens a review), `blocked` with [reason],
     * `done`, `archived`. The server refuses moves its rules forbid and says why.
     */
    override suspend fun move(id: String, status: String, reason: String?) {
        val payload = buildMap<String, JsonElement> {
            put("status", JsonPrimitive(status))
            if (reason != null) put("block_reason", JsonPrimitive(reason))
        }
        call("PATCH", "/tasks/${id.urlPart()}", JsonObject(payload))
        if (status == "ready") dispatchNow()
    }

    override suspend fun comment(id: String, text: String) {
        call("POST", "/tasks/${id.urlPart()}/comments", JsonObject(mapOf("body" to JsonPrimitive(text.trim()), "author" to JsonPrimitive("app"))))
    }

    override suspend fun delete(id: String) {
        call("DELETE", "/tasks/${id.urlPart()}")
    }

    /**
     * The same change on several tasks: a new [status] (`ready`, `done`), [archive], or a new
     * [assignee] ("" for nobody; null leaves it). Each task is changed on its own, so some can be
     * refused; returns those as id → the server's reason.
     */
    override suspend fun bulk(
        ids: Collection<String>,
        status: String?,
        assignee: String?,
        archive: Boolean,
    ): Map<String, String> {
        val payload = buildMap<String, JsonElement> {
            put("ids", JsonArray(ids.map { JsonPrimitive(it) }))
            if (status != null) put("status", JsonPrimitive(status))
            // The server refuses to finish a task without a result or summary to show for it.
            if (status == "done") put("summary", JsonPrimitive("Marked done from the Hermes app"))
            if (assignee != null) put("assignee", JsonPrimitive(assignee))
            if (archive) put("archive", JsonPrimitive(true))
        }
        val root = call("POST", "/tasks/bulk", JsonObject(payload)) as? JsonObject
        val refused = (root?.get("results") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { !it.bool("ok") }
            .associate { it.str("id") to it.str("error").ifEmpty { "refused" } }
        if (status == "ready" || !assignee.isNullOrEmpty()) dispatchNow()
        return refused
    }

    override suspend fun orchestration(): Orchestration {
        val root = call("GET", "/orchestration") as? JsonObject ?: throw IOException("No orchestration settings")
        return root.toOrchestration()
    }

    /** Saves all four settings; the server refuses a profile that doesn't exist. */
    override suspend fun saveOrchestration(settings: Orchestration): Orchestration {
        val payload = JsonObject(
            mapOf(
                "orchestrator_profile" to JsonPrimitive(settings.orchestratorProfile),
                "default_assignee" to JsonPrimitive(settings.defaultAssignee),
                "auto_decompose" to JsonPrimitive(settings.autoDecompose),
                "auto_promote_children" to JsonPrimitive(settings.autoPromoteChildren),
            )
        )
        val root = call("PUT", "/orchestration", payload) as? JsonObject ?: return settings
        return root.toOrchestration()
    }

    /** Adds [file] to the task under [name]; the worker reads it from the server's disk. */
    override suspend fun upload(taskId: String, file: File, name: String, mimeType: String?) {
        val path = scoped("/tasks/${taskId.urlPart()}/attachments")
        if (onPhone) {
            // The phone's gateway reads the file from the built-in Linux.
            val guest = "/tmp/hermes-upload-${UUID.randomUUID()}"
            val host = runtime.hostFileForGuestPath(guest) ?: throw IOException("The built-in Linux isn't ready")
            withContext(Dispatchers.IO) { file.copyTo(host, overwrite = true) }
            try {
                callOnPhone("POST", path, null, upload = guest to name)
            } finally {
                host.delete()
            }
            return
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", name, file.asRequestBody(mimeType?.toMediaTypeOrNull()))
            .addFormDataPart("uploaded_by", "app")
            .build()
        send(path, Request.Builder().post(body), modelCalls)
    }

    override suspend fun deleteAttachment(id: Long) {
        call("DELETE", "/attachments/$id")
    }

    /** The server model's guess of what the task will take; fails with the server's reason. */
    override suspend fun estimate(id: String): Estimate {
        val root = call("POST", "/tasks/${id.urlPart()}/estimate", slow = true) as? JsonObject
            ?: throw IOException("No answer")
        if (!root.bool("ok")) throw IOException(root.str("reason").ifEmpty { "No estimate" })
        return Estimate(
            tokens = root.long("est_tokens"),
            complexity = root.str("complexity").ifEmpty { null },
            rationale = root.str("rationale"),
        )
    }

    /** Providers and models the server can run a task on (the dashboard's override list). */
    override suspend fun modelOptions(): List<ModelProvider> {
        val root = call("GET", "/model-options") as? JsonObject ?: return emptyList()
        return (root["providers"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { row ->
            val slug = row.str("slug").ifEmpty { return@mapNotNull null }
            val models = (row["models"] as? JsonArray).orEmpty().mapNotNull { m ->
                (m as? JsonPrimitive)?.contentOrNull ?: (m as? JsonObject)?.let { it.str("id").ifEmpty { it.str("name") } }
            }.filter { it.isNotEmpty() }
            ModelProvider(slug, row.str("label").ifEmpty { slug }, models)
        }
    }

    /**
     * Sets what the task runs on: [model] on [provider] (null model: back to the profile's), and
     * [effort] (null: back to the profile's level; "none" turns thinking off).
     */
    override suspend fun setRunSettings(id: String, model: String?, provider: String?, effort: String?) {
        val payload = buildMap<String, JsonElement> {
            if (model == null) put("clear_model_override", JsonPrimitive(true))
            else {
                put("model_override", JsonPrimitive(model))
                if (provider != null) put("provider_override", JsonPrimitive(provider))
            }
            if (effort == null) put("clear_reasoning_effort", JsonPrimitive(true))
            else put("reasoning_effort", JsonPrimitive(effort))
        }
        call("PATCH", "/tasks/${id.urlPart()}", JsonObject(payload))
    }

    /** Tasks on the board with trouble, worst first. */
    override suspend fun diagnostics(): List<TaskDiagnostics> {
        val root = call("GET", "/diagnostics") as? JsonObject ?: return emptyList()
        return (root["diagnostics"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { row ->
            val id = row.str("task_id").ifEmpty { return@mapNotNull null }
            TaskDiagnostics(
                taskId = id,
                title = row.str("task_title").ifEmpty { id },
                status = row.str("task_status"),
                diagnostics = (row["diagnostics"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
                    Diagnostic(severity = it.str("severity"), title = it.str("title"), detail = it.str("detail"))
                },
            )
        }
    }

    /** The messengers with a home channel, and whether this task's news goes to each. */
    override suspend fun homeChannels(taskId: String): List<HomeChannel> {
        val root = call("GET", "/home-channels?task_id=${taskId.urlPart()}") as? JsonObject ?: return emptyList()
        return (root["home_channels"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { row ->
            val platform = row.str("platform").ifEmpty { return@mapNotNull null }
            HomeChannel(platform, row.str("name").ifEmpty { platform }, row.bool("subscribed"))
        }
    }

    override suspend fun setHomeChannel(taskId: String, platform: String, on: Boolean) {
        call(if (on) "POST" else "DELETE", "/tasks/${taskId.urlPart()}/home-subscribe/${platform.urlPart()}")
    }

    /** Writes [slug] to an archive on the server (with its files) and returns that server path. */
    override suspend fun exportBoard(slug: String): String {
        val root = call("POST", "/boards/${slug.urlPart()}/export", JsonObject(mapOf("attachments" to JsonPrimitive(true))), slow = true)
            as? JsonObject ?: throw IOException("No answer")
        return root.str("archive").ifEmpty { throw IOException("The server wrote no archive") }
    }

    /**
     * Puts the board archive [file] on the server and imports it as a new board; returns its slug.
     * The server imports from its own disk, so the file goes there first.
     */
    override suspend fun importBoard(file: File): String {
        val name = "import-${UUID.randomUUID().toString().take(8)}.tar.gz"
        var serverPath: String
        if (onPhone) {
            serverPath = "/tmp/$name"
            val host = runtime.hostFileForGuestPath(serverPath) ?: throw IOException("The built-in Linux isn't ready")
            withContext(Dispatchers.IO) { file.copyTo(host, overwrite = true) }
        } else {
            serverPath = "~/.hermes/kanban/board-imports/$name"
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", name, file.asRequestBody("application/gzip".toMediaType()))
                .addFormDataPart("path", serverPath)
                .addFormDataPart("overwrite", "true")
                .build()
            val base = base() ?: throw IOException("Kanban needs a connected server.")
            val stored = sendTo("$base/api/files/upload-stream", Request.Builder().post(body), modelCalls) as? JsonObject
            // The server's own absolute path for it (`~` expanded).
            serverPath = stored?.str("path")?.ifEmpty { null } ?: serverPath
        }
        val root = call("POST", "/boards/import", JsonObject(mapOf("archive" to JsonPrimitive(serverPath))), slow = true)
            as? JsonObject ?: throw IOException("No answer")
        return root.str("board").ifEmpty { throw IOException("The server imported no board") }
    }

    /** Makes [childId] wait on [parentId]: it starts once that one is done. The server refuses a cycle. */
    override suspend fun link(parentId: String, childId: String) {
        call("POST", "/links", JsonObject(mapOf("parent_id" to JsonPrimitive(parentId), "child_id" to JsonPrimitive(childId))))
    }

    override suspend fun unlink(parentId: String, childId: String) {
        call("DELETE", "/links?parent_id=${parentId.urlPart()}&child_id=${childId.urlPart()}")
        dispatchNow()
    }

    /**
     * Has the server's model write a triage task out in full (title, details) and move it to
     * the queue. Takes a while: it waits on the model. Null when done, else the server's reason.
     */
    override suspend fun specify(id: String): String? {
        val root = call("POST", "/tasks/${id.urlPart()}/specify", JsonObject(mapOf("author" to JsonPrimitive("app"))), slow = true) as? JsonObject
            ?: return "No answer"
        return if (root.bool("ok")) null else root.str("reason").ifEmpty { "Refused" }
    }

    /** Asks the dispatcher to start ready tasks now instead of at its next 60 s tick. */
    private suspend fun dispatchNow() {
        runCatching { call("POST", "/dispatch") }
    }

    private fun base(): String? = remoteHttpBase(remote.config.value.serverUrl)?.takeIf { it.startsWith("https://") }

    private suspend fun call(method: String, path: String, body: JsonObject? = null, slow: Boolean = false): JsonElement {
        val scopedPath = if (path.startsWith("/boards")) path else scoped(path)
        return if (onPhone) callOnPhone(method, scopedPath, body) else callServer(method, scopedPath, body, slow)
    }

    /** [path] on the selected board; the server's current board when none is selected. */
    private fun scoped(path: String): String {
        val slug = _selectedBoard.value ?: return path
        return path + (if ('?' in path) "&" else "?") + "board=" + slug.urlPart()
    }

    /** The phone's gateway runs the same plugin router in-process (`android.kanban`). */
    /** [upload]: a file in the built-in Linux and its name, sent as the form's `file`. */
    private suspend fun callOnPhone(
        method: String,
        path: String,
        body: JsonObject?,
        upload: Pair<String, String>? = null,
    ): JsonElement {
        val params = buildMap<String, JsonElement> {
            put("method", JsonPrimitive(method))
            put("path", JsonPrimitive(path))
            if (body != null) put("body", body)
            if (upload != null) {
                put("file_path", JsonPrimitive(upload.first))
                put("file_name", JsonPrimitive(upload.second))
            }
        }
        val result = gatewayClient.request(KANBAN_METHOD, params, trackSession = false) as? JsonObject
            ?: throw IOException("No answer from Kanban")
        val status = (result["status"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        val answer = result["body"] ?: JsonNull
        if (status !in 200..299) {
            val detail = ((answer as? JsonObject)?.get("detail") as? JsonPrimitive)?.contentOrNull
            throw IOException(detail ?: "Kanban answered $status")
        }
        return answer
    }

    private suspend fun callServer(method: String, path: String, body: JsonObject? = null, slow: Boolean = false): JsonElement {
        val requestBody = body?.toString()?.toRequestBody(JSON_TYPE)
            ?: if (method == "POST") "{}".toRequestBody(JSON_TYPE) else null
        return send(path, Request.Builder().method(method, requestBody), if (slow) modelCalls else rest)
    }

    /** [request] to the Kanban API's [path] on the server. */
    private suspend fun send(path: String, request: Request.Builder, client: OkHttpClient = rest): JsonElement {
        val base = base() ?: throw IOException("Kanban needs a connected server.")
        return sendTo("$base$API$path", request, client)
    }

    private suspend fun sendTo(url: String, request: Request.Builder, client: OkHttpClient): JsonElement =
        withContext(Dispatchers.IO) {
            client.newCall(request.url(url).build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val parsed = runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)
                if (!response.isSuccessful) {
                    // FastAPI puts the reason in `detail` (the transition rules write user-facing ones).
                    val detail = ((parsed as? JsonObject)?.get("detail") as? JsonPrimitive)?.contentOrNull
                    throw IOException(detail ?: "HTTP ${response.code}")
                }
                parsed
            }
        }

    private companion object {
        const val API = "/api/plugins/kanban"
        const val KANBAN_METHOD = "android.kanban"
        const val LOG_TAIL_BYTES = 64 * 1024
        val JSON_TYPE = "application/json".toMediaType()
    }
}

/**
 * A board slug for [name]: lowercase latin letters, digits and hyphens, as the server requires
 * (1-64 chars, not starting with `-`). A name with none of those (Persian, say) gets a random one;
 * the name itself is kept as the board's display name.
 */
internal fun boardSlug(name: String): String {
    val latin = name.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("")
        .split('-').filter { it.isNotEmpty() }.joinToString("-").take(48).trimEnd('-')
    return latin.ifEmpty { "board-" + UUID.randomUUID().toString().take(8) }
}

private fun String.urlPart(): String = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0

private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.contentOrNull == "true"

private fun JsonObject.toOrchestration() = KanbanRepository.Orchestration(
    orchestratorProfile = str("orchestrator_profile"),
    defaultAssignee = str("default_assignee"),
    autoDecompose = bool("auto_decompose"),
    autoPromoteChildren = bool("auto_promote_children"),
    activeProfile = str("active_profile").ifEmpty { "default" },
)

private fun JsonObject.toRun(): KanbanRepository.Run? {
    val id = str("id").toDoubleOrNull()?.toLong() ?: return null
    return KanbanRepository.Run(
        id = id,
        profile = str("profile").ifEmpty { null },
        status = str("status"),
        outcome = str("outcome").ifEmpty { null },
        startedAt = long("started_at"),
        endedAt = str("ended_at").toDoubleOrNull()?.toLong(),
        summary = str("summary"),
        error = str("error"),
    )
}

private fun JsonObject.toTask(): KanbanRepository.Task? {
    val id = str("id").ifEmpty { return null }
    val progress = this["progress"] as? JsonObject
    return KanbanRepository.Task(
        id = id,
        title = str("title"),
        body = str("body"),
        assignee = str("assignee").ifEmpty { null },
        status = str("status"),
        priority = long("priority").toInt(),
        createdAt = long("created_at"),
        summary = str("latest_summary"),
        result = str("result"),
        lastError = str("last_failure_error"),
        commentCount = long("comment_count").toInt(),
        progress = progress?.let { it.long("done").toInt() to it.long("total").toInt() },
        modelOverride = str("model_override").ifEmpty { null },
        providerOverride = str("provider_override").ifEmpty { null },
        reasoningEffort = str("reasoning_effort").ifEmpty { null },
    )
}

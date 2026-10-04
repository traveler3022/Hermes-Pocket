package com.hermes.android.data

import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import com.hermes.android.runtime.remote.RemoteServerSettings
import com.hermes.android.runtime.remote.remoteHttpBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
 * (`plugins/kanban/dashboard/plugin_api.py`, mounted at `/api/plugins/kanban`). Only a
 * remote server has that dashboard here: the built-in Linux runtime runs no dashboard and
 * no dispatcher, so this is offered for the remote runtime only. The shared HTTP client
 * signs each call in (RemoteAuthInterceptor) and reaches a tailnet server through the
 * in-app Tailscale node, as for the chat connection itself.
 */
@Singleton
class KanbanRepository @Inject constructor(
    http: OkHttpClient,
    private val json: Json,
    private val runtime: HermesRuntime,
    private val remote: RemoteServerSettings,
) {
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
    )

    data class Comment(val author: String, val body: String, val createdAt: Long)

    data class Detail(val task: Task, val comments: List<Comment>)

    /** The app's client (sign-in, Tailscale route) with a deadline: it has none for its WebSocket. */
    private val rest = http.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    /** Kanban needs the server's dashboard: the remote runtime, over https. */
    val available: Boolean get() = runtime.type == RuntimeType.REMOTE && base() != null

    /** The board, column name → tasks, in the server's column order. */
    suspend fun board(): Map<String, List<Task>> {
        val root = call("GET", "/board") as? JsonObject ?: return emptyMap()
        return (root["columns"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.associate { column ->
            column.str("name") to (column["tasks"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toTask() }
        }
    }

    suspend fun task(id: String): Detail {
        val root = call("GET", "/tasks/${id.urlPart()}") as? JsonObject ?: throw IOException("No task $id")
        val task = (root["task"] as? JsonObject)?.toTask() ?: throw IOException("No task $id")
        val comments = (root["comments"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
            Comment(author = it.str("author"), body = it.str("body"), createdAt = it.long("created_at"))
        }
        return Detail(task, comments)
    }

    /** Profiles a task can go to: every profile on the server plus any already used on the board. */
    suspend fun assignees(): List<String> {
        val root = call("GET", "/assignees") as? JsonObject ?: return emptyList()
        return (root["assignees"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }

    /**
     * Adds a task. With [assignee] it is ready for that profile's worker; without one it goes
     * to triage, where the server's decomposer splits it into tasks for the right profiles
     * (`kanban.auto_decompose`, on by default).
     */
    suspend fun create(title: String, body: String, assignee: String?) {
        val payload = buildMap<String, JsonElement> {
            put("title", JsonPrimitive(title.trim()))
            if (body.isNotBlank()) put("body", JsonPrimitive(body.trim()))
            if (assignee != null) put("assignee", JsonPrimitive(assignee)) else put("triage", JsonPrimitive(true))
            // A resend after a dropped answer finds the first copy instead of making a second.
            put("idempotency_key", JsonPrimitive("app-${UUID.randomUUID()}"))
        }
        call("POST", "/tasks", JsonObject(payload))
        if (assignee != null) dispatchNow()
    }

    /**
     * Moves a task: `ready` (also unblocks and reopens a review), `blocked` with [reason],
     * `done`, `archived`. The server refuses moves its rules forbid and says why.
     */
    suspend fun move(id: String, status: String, reason: String? = null) {
        val payload = buildMap<String, JsonElement> {
            put("status", JsonPrimitive(status))
            if (reason != null) put("block_reason", JsonPrimitive(reason))
        }
        call("PATCH", "/tasks/${id.urlPart()}", JsonObject(payload))
        if (status == "ready") dispatchNow()
    }

    suspend fun comment(id: String, text: String) {
        call("POST", "/tasks/${id.urlPart()}/comments", JsonObject(mapOf("body" to JsonPrimitive(text.trim()), "author" to JsonPrimitive("app"))))
    }

    suspend fun delete(id: String) {
        call("DELETE", "/tasks/${id.urlPart()}")
    }

    /** Asks the dispatcher to start ready tasks now instead of at its next 60 s tick. */
    private suspend fun dispatchNow() {
        runCatching { call("POST", "/dispatch") }
    }

    private fun base(): String? = remoteHttpBase(remote.config.value.serverUrl)?.takeIf { it.startsWith("https://") }

    private suspend fun call(method: String, path: String, body: JsonObject? = null): JsonElement =
        withContext(Dispatchers.IO) {
            val base = base() ?: throw IOException("Kanban needs a connected server.")
            val requestBody = body?.toString()?.toRequestBody(JSON_TYPE)
                ?: if (method == "POST") "{}".toRequestBody(JSON_TYPE) else null
            val request = Request.Builder()
                .url("$base$API$path")
                .method(method, requestBody)
                .build()
            rest.newCall(request).execute().use { response ->
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
        val JSON_TYPE = "application/json".toMediaType()
    }
}

private fun String.urlPart(): String = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0

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
    )
}

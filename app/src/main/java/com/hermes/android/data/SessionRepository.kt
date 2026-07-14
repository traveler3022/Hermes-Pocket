package com.hermes.android.data

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Milestone A — the single home for session-protocol semantics.
 *
 * Every correctness bug found in this codebase so far was a ViewModel
 * re-guessing protocol semantics on its own: a stored db id handed to an RPC
 * that resolves live ids (silent global write), a live id handed to
 * session.resume (4007), the raw active_list dumped as "tasks". This
 * repository owns those semantics exactly once:
 *
 * - **Two id kinds.** A LIVE id (`uuid4().hex[:8]`, key of the gateway's
 *   in-memory `_sessions`) and a STORED db key. They are NEVER equal — even a
 *   freshly created session gets both. `session.resume` resolves STORED ids
 *   (4007s on live ones, before its live fast-path); `session.activate`
 *   attaches to LIVE ids. [attach] hides that entirely.
 * - **Reasoning scope.** `config.set key=reasoning` with a live session id is
 *   session-scoped and never persists; without one it writes the global
 *   default. Both on purpose, together, in [setReasoningLevel].
 * - **Task identity.** Delegated work is distinguishable from other live
 *   sessions only via [TaskRegistry].
 *
 * ViewModels consume this API and never speak JSON-RPC session semantics
 * directly. Contract tests: `SessionRepositoryTest` (JVM, FakeGatewayClient).
 */
@Singleton
class SessionRepository @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val taskRegistry: TaskRegistry,
) {

    // ── Attach (resume/activate semantics) ────────────────────────────────

    /**
     * A session the client is attached to: [liveId] is what every subsequent
     * RPC/event speaks; [raw] is the full server payload (messages included)
     * for the transcript parser.
     */
    data class AttachedSession(
        val liveId: String,
        val raw: JsonObject,
    )

    /**
     * Attach to a session given EITHER id kind.
     *
     * Stored ids (session lists, most_recent) go through `session.resume`,
     * which mints/reuses a live id bound to the transcript. Live ids (events,
     * active_list rows, notification extras) 4007 on resume, so we fall back
     * to `session.activate`. Callers that know they hold a live id pass
     * [preferLive] to try activate first and save the doomed round-trip.
     */
    suspend fun attach(sessionId: String, preferLive: Boolean = false): AttachedSession {
        val params = buildJsonObject { put("session_id", sessionId) }.toElementMap()
        val first = if (preferLive) GatewayMethods.SESSION_ACTIVATE else GatewayMethods.SESSION_RESUME
        val second = if (preferLive) GatewayMethods.SESSION_RESUME else GatewayMethods.SESSION_ACTIVATE
        val result = try {
            gatewayClient.request(first, params)
        } catch (firstError: Exception) {
            Timber.w("[Repo] $first failed (${firstError.message}); trying $second for $sessionId")
            gatewayClient.request(second, params)
        }
        val obj = result as? JsonObject
            ?: throw IllegalStateException("attach($sessionId): non-object payload")
        val liveId = obj.str("session_id").ifBlank { sessionId }
        return AttachedSession(liveId = liveId, raw = obj)
    }

    // ── Tasks (delegation) ─────────────────────────────────────────────────

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
        val isRunning: Boolean get() = status == "streaming" || status == "running"
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

    /**
     * Flat list of available models across authenticated providers, for the
     * per-task model picker. Shape mirrors ConfigViewModel.parseModelOptions
     * (build_models_payload → {providers:[{slug, models:[str]}]}).
     */
    suspend fun availableModels(): List<ModelChoice> {
        val result = gatewayClient.request(GatewayMethods.MODEL_OPTIONS)
        val providers = ((result as? JsonObject)?.get("providers") as? JsonArray) ?: return emptyList()
        return providers.mapNotNull { it as? JsonObject }.flatMap { p ->
            val slug = p.str("slug")
            (p["models"] as? JsonArray).orEmptyStrings().map { ModelChoice(slug, it) }
        }
    }

    private fun JsonArray?.orEmptyStrings(): List<String> =
        this?.mapNotNull { (it as? JsonPrimitive)?.content }?.filter { it.isNotBlank() } ?: emptyList()

    /**
     * Launch delegated work: a titled session + one prompt, registered so the
     * Task Desk can tell it apart from every other live session. The created
     * session is deliberately NOT tracked as the reconnect auto-resume target
     * (that belongs to the user's chat). [reasoningEffort] is a per-session
     * override (none/minimal/low/medium/high/xhigh); blank leaves the default.
     */
    suspend fun launchTask(
        title: String,
        prompt: String,
        reasoningEffort: String? = null,
        model: ModelChoice? = null,
    ): String {
        val createParams = buildJsonObject {
            val cleanTitle = title.trim()
            if (cleanTitle.isNotEmpty()) put("title", cleanTitle)
            put("source", TASK_SOURCE)
            reasoningEffort?.trim()?.takeIf { it.isNotEmpty() }?.let { put("reasoning_effort", it) }
            model?.let {
                put("model", it.modelId)
                if (it.provider.isNotBlank()) put("provider", it.provider)
            }
        }.toElementMap()
        val created = gatewayClient.request(
            GatewayMethods.SESSION_CREATE, createParams, trackSession = false,
        ) as? JsonObject ?: throw IllegalStateException("session.create: non-object payload")

        val liveId = created.str("session_id")
            .ifBlank { throw IllegalStateException("session.create returned no session_id") }
        taskRegistry.register(liveId, created.str("stored_session_id").takeIf { it.isNotBlank() })

        gatewayClient.request(
            GatewayMethods.PROMPT_SUBMIT,
            buildJsonObject {
                put("text", prompt.trim())
                put("session_id", liveId)
            }.toElementMap(),
        )
        Timber.i("[Repo] task '$title' launched as $liveId")
        return liveId
    }

    /** Live sessions that belong to the Task Desk (registry-filtered). */
    suspend fun activeTasks(): List<TaskRow> {
        val result = gatewayClient.request(GatewayMethods.SESSION_ACTIVE_LIST)
        val rows = ((result as? JsonObject)?.get("sessions") as? JsonArray)
            ?.mapNotNull { it as? JsonObject } ?: return emptyList()
        return rows.mapNotNull { row ->
            val id = row.str("id")
            if (id.isEmpty()) return@mapNotNull null
            val key = row.str("session_key")
            if (!taskRegistry.isTask(id, key)) return@mapNotNull null
            TaskRow(
                id = id,
                sessionKey = key,
                title = row.str("title").ifEmpty { key.ifEmpty { id } },
                status = row.str("status"),
                preview = row.str("preview"),
                model = row.str("model"),
                messageCount = row.str("message_count").toIntOrNull() ?: 0,
                lastActive = row.str("last_active").toDoubleOrNull() ?: 0.0,
            )
        }.sortedByDescending { it.lastActive }
    }

    /**
     * Tasks launched from this app that are no longer live — read from the
     * server's persistent session store (session.list), filtered to our
     * source AND the local registry (a shared server could hold pocket_task
     * rows this device never created). Newest first.
     */
    suspend fun taskHistory(): List<TaskHistoryRow> {
        val result = gatewayClient.request(GatewayMethods.SESSION_LIST)
        val rows = ((result as? JsonObject)?.get("sessions") as? JsonArray)
            ?.mapNotNull { it as? JsonObject } ?: return emptyList()
        return rows.mapNotNull { row ->
            val id = row.str("id")
            if (id.isEmpty()) return@mapNotNull null
            val isOurs = row.str("source") == TASK_SOURCE && taskRegistry.isTask(id, id)
            if (!isOurs) return@mapNotNull null
            TaskHistoryRow(
                id = id,
                title = row.str("title").ifEmpty { id },
                preview = row.str("preview"),
                messageCount = row.str("message_count").toIntOrNull() ?: 0,
            )
        }
    }

    /**
     * Full transcript of a task for the result sheet. Attaches first (server
     * lazily rebuilds a reaped session from its db row), so it works for both
     * live and finished tasks.
     */
    suspend fun transcript(sessionId: String): List<TranscriptEntry> {
        val attached = attach(sessionId)
        val messages = attached.raw["messages"] as? JsonArray
            ?: run {
                val hist = gatewayClient.request(
                    GatewayMethods.SESSION_HISTORY,
                    buildJsonObject { put("session_id", attached.liveId) }.toElementMap(),
                )
                (hist as? JsonObject)?.get("messages") as? JsonArray
            }
            ?: return emptyList()
        return messages.mapNotNull { it as? JsonObject }.mapNotNull { m ->
            val role = m.str("role").ifEmpty { return@mapNotNull null }
            val text = m.str("content").ifEmpty { m.str("text") }
            if (text.isBlank()) null else TranscriptEntry(role, text)
        }
    }

    suspend fun interrupt(liveId: String) {
        gatewayClient.request(
            GatewayMethods.SESSION_INTERRUPT,
            buildJsonObject { put("session_id", liveId) }.toElementMap(),
        )
    }

    /** Tears the live worker down; the transcript stays in the server store. */
    suspend fun closeSession(liveId: String) {
        gatewayClient.request(
            GatewayMethods.SESSION_CLOSE,
            buildJsonObject { put("session_id", liveId) }.toElementMap(),
        )
    }

    // ── Reasoning effort ───────────────────────────────────────────────────

    /**
     * The effort the given live session actually runs at (server prefers the
     * session's live reasoning_config over the global config.yaml default).
     */
    suspend fun reasoningLevel(liveSessionId: String?): String {
        val params = buildJsonObject {
            put("key", "reasoning")
            liveSessionId?.let { put("session_id", it) }
        }.toElementMap()
        val result = gatewayClient.request(GatewayMethods.CONFIG_GET, params)
        return (result as? JsonObject)?.str("value")?.trim()?.takeIf { it.isNotBlank() } ?: "medium"
    }

    /**
     * Session-scoped write (immediate effect on the live chat) plus, when
     * [persistGlobal], a best-effort global write so the choice survives new
     * chats and app restarts — the session write alone dies with the session.
     */
    suspend fun setReasoningLevel(
        rawLevel: String,
        liveSessionId: String?,
        persistGlobal: Boolean = true,
    ): String {
        val level = rawLevel.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        val params = buildJsonObject {
            put("key", "reasoning")
            put("value", level)
            liveSessionId?.let { put("session_id", it) }
        }.toElementMap()
        gatewayClient.request(GatewayMethods.CONFIG_SET, params)
        if (persistGlobal && liveSessionId != null) {
            try {
                gatewayClient.request(
                    GatewayMethods.CONFIG_SET,
                    buildJsonObject {
                        put("key", "reasoning")
                        put("value", level)
                    }.toElementMap(),
                )
            } catch (e: Exception) {
                Timber.w(e, "[Repo] reasoning global persist failed (live change applied)")
            }
        }
        return level
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.content ?: ""

    private fun JsonObject.toElementMap(): Map<String, JsonElement> =
        entries.associate { (k, v) -> k to v }

    companion object {
        const val TASK_SOURCE = "pocket_task"
    }
}

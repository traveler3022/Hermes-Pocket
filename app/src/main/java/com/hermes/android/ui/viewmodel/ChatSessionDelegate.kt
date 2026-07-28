package com.hermes.android.ui.viewmodel

import com.hermes.android.data.SessionRepository
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.gateway.GatewayException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.UUID

internal class ChatSessionDelegate(
    private val gatewayClient: GatewayClient,
    private val sessionRepository: SessionRepository,
    private val scope: CoroutineScope,
    private val loadReasoningLevel: () -> Unit,
) {
    suspend fun createOrResume(state: MutableStateFlow<ChatUiState>) {
        val mostRecentId = try {
            val mr = gatewayClient.request(GatewayMethods.SESSION_MOST_RECENT)
            (mr as? JsonObject)?.get("session_id")?.let { (it as? JsonPrimitive)?.content }
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.w(e, "[Chat] session.most_recent failed, falling back to a new session")
            null
        }
        if (mostRecentId != null) {
            resume(state, mostRecentId)
        } else {
            create(state)
        }
    }

    suspend fun create(state: MutableStateFlow<ChatUiState>) {
        try {
            val result = gatewayClient.request(GatewayMethods.SESSION_CREATE)
            val sessionId = (result as? JsonObject)
                ?.get("session_id")
                ?.let { it as? JsonPrimitive }
                ?.content
            if (sessionId != null) {
                state.update { it.copy(activeSessionId = sessionId) }
                Timber.i("[Chat] Session created: $sessionId")
            }
        } catch (e: GatewayException) {
            Timber.e(e, "[Chat] Failed to create session")
            state.update { it.copy(
                errorEvent = ErrorEvent.Error("Failed to create session: ${e.message}")
            ) }
        }
    }

    suspend fun resume(state: MutableStateFlow<ChatUiState>, sessionId: String) {
        try {
            val attached = sessionRepository.attach(sessionId)
            val liveSessionId = attached.liveId
            val history = parseSessionHistory(attached.raw)
            state.update { it.copy(
                activeSessionId = liveSessionId,
                messages = history,
                showSessionDrawer = false,
                errorEvent = null,
                sessionLoadedAt = System.currentTimeMillis(),
                activeTodos = emptyList(),
                pendingApproval = null,
            ) }
            loadReasoningLevel()
            if (history.isNotEmpty()) {
                Timber.i("[Chat] Resumed $sessionId as live session $liveSessionId with ${history.size} messages")
            } else {
                Timber.w("[Chat] Resume returned no inline messages, falling back to session.history for $liveSessionId")
                loadHistory(state, liveSessionId)
            }
        } catch (e: Exception) {
            Timber.e(e, "[Chat] Failed to resume session")
            state.update { it.copy(errorEvent = ErrorEvent.Error("Failed to resume: ${e.message}")) }
        }
    }

    suspend fun loadList(state: MutableStateFlow<ChatUiState>) {
        try {
            val result = gatewayClient.request(GatewayMethods.SESSION_LIST)
            val sessions = parseList(result)
            state.update { it.copy(sessions = sessions) }
            Timber.d("[Chat] Session list loaded: ${sessions.size}")
        } catch (e: Exception) {
            Timber.w(e, "[Chat] Failed to load session list")
        }
    }

    suspend fun loadHistory(state: MutableStateFlow<ChatUiState>, sessionId: String) {
        try {
            val params = buildJsonObject { put("session_id", sessionId) }
            val result = gatewayClient.request(GatewayMethods.SESSION_HISTORY, jsonToElementMap(params))
            val messages = parseSessionHistory(result)
            if (messages.isNotEmpty()) {
                state.update { it.copy(
                    messages = messages,
                    sessionLoadedAt = System.currentTimeMillis(),
                ) }
                Timber.i("[Chat] Loaded ${messages.size} history messages for session $sessionId")
            } else {
                Timber.w("[Chat] Session history returned empty for $sessionId")
            }
        } catch (e: Exception) {
            Timber.w(e, "[Chat] Could not load session history for $sessionId, continuing without it")
        }
    }

    suspend fun branch(state: MutableStateFlow<ChatUiState>, resolveSessionId: suspend () -> String?) {
        try {
            val sid = resolveSessionId()
            if (sid == null) {
                state.update { it.copy(errorEvent = ErrorEvent.Warning("No active conversation to branch")) }
                return
            }
            val result = gatewayClient.request(
                GatewayMethods.SESSION_BRANCH,
                jsonToElementMap(buildJsonObject { put("session_id", sid) }),
            )
            val newId = ((result as? JsonObject)?.get("session_id") as? JsonPrimitive)?.content
            loadList(state)
            if (newId != null) {
                resume(state, newId)
                state.update { it.copy(errorEvent = ErrorEvent.Warning("Branched into a new conversation")) }
            }
        } catch (e: Exception) {
            Timber.w(e, "[Chat] session.branch failed")
            val m = e.message.orEmpty()
            state.update { it.copy(
                errorEvent = if (m.contains("4008") || m.contains("nothing to branch"))
                    ErrorEvent.Warning("Send at least one message before branching")
                else ErrorEvent.Error("Branch failed: $m"),
            ) }
        }
    }

    suspend fun resolveLiveSessionId(state: MutableStateFlow<ChatUiState>): String? {
        return try {
            val mr = gatewayClient.request(GatewayMethods.SESSION_MOST_RECENT)
            ((mr as? JsonObject)?.get("session_id") as? JsonPrimitive)?.content
        } catch (e: Exception) {
            null
        } ?: state.value.activeSessionId
    }

    private fun parseList(result: kotlinx.serialization.json.JsonElement): List<SessionItem> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            val arr = obj["sessions"] as? JsonArray ?: return emptyList()
            arr.mapNotNull { item ->
                val session = item as? JsonObject ?: return@mapNotNull null
                SessionItem(
                    id = session["id"]?.let { (it as? JsonPrimitive)?.content } ?: return@mapNotNull null,
                    title = session["title"]?.let { (it as? JsonPrimitive)?.content }?.ifBlank { null }
                        ?: "Untitled",
                    lastMessagePreview = session["preview"]?.let { (it as? JsonPrimitive)?.content },
                    updatedAt = (session["started_at"] ?: session["updated_at"])
                        ?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() }
                        ?.let { normalizeEpochMillis(it) } ?: System.currentTimeMillis(),
                    messageCount = session["message_count"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() },
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "[Chat] Failed to parse sessions")
            emptyList()
        }
    }

    private fun parseSessionHistory(result: kotlinx.serialization.json.JsonElement): List<ChatMessage> {
        return try {
            val obj = result as? JsonObject ?: return emptyList()
            val arr = obj["messages"] as? JsonArray
                ?: obj["history"] as? JsonArray
                ?: return emptyList()
            arr.mapNotNull { item ->
                val msg = item as? JsonObject ?: return@mapNotNull null
                val role = msg["role"]?.let { (it as? JsonPrimitive)?.content } ?: return@mapNotNull null
                val content = msg["content"]?.let { (it as? JsonPrimitive)?.content }
                    ?: msg["text"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                val ts = msg["timestamp"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }
                    ?.let(::normalizeEpochMillis) ?: System.currentTimeMillis()
                val id = msg["id"]?.let { (it as? JsonPrimitive)?.content } ?: UUID.randomUUID().toString()
                when (role) {
                    "user" -> ChatMessage.User(id = id, timestamp = ts, text = content)
                    "assistant" -> ChatMessage.Assistant(
                        id = id, timestamp = ts, text = content,
                        isStreaming = false,
                        reasoning = msg["reasoning"]?.let { (it as? JsonPrimitive)?.content },
                    )
                    "tool" -> ChatMessage.ToolCall(
                        id = id, timestamp = ts,
                        toolName = msg["name"]?.let { (it as? JsonPrimitive)?.content } ?: "tool",
                        argsText = msg["args"]?.let { (it as? JsonPrimitive)?.content },
                        resultText = msg["result"]?.let { (it as? JsonPrimitive)?.content } ?: content,
                        error = msg["error"]?.let { (it as? JsonPrimitive)?.content },
                        isRunning = false, durationS = null,
                    )
                    else -> null
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[Chat] Failed to parse session history")
            emptyList()
        }
    }

    private fun jsonToElementMap(obj: JsonObject): Map<String, kotlinx.serialization.json.JsonElement> = obj.toMap()
}

package com.hermes.android.ui.viewmodel

import com.hermes.android.data.SessionRepository
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.gateway.GatewayException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
    /** Turn ends the open chat has seen, bumped by [onTurnEnded]; lets [recover] tell a snapshot is older than one. */
    private var turnEnds = 0

    /** The open chat's message.complete was handled. */
    fun onTurnEnded() {
        turnEnds++
    }

    suspend fun createOrResume(state: MutableStateFlow<ChatUiState>) {
        val mostRecentId = try {
            val mr = gatewayClient.request(GatewayMethods.SESSION_MOST_RECENT)
            (mr as? JsonObject)?.get("session_id").sessionIdOrNull()
        } catch (e: Exception) {
            // Cancelled because the user asked for a specific chat meanwhile:
            // opening a new one here would bury it.
            if (e is CancellationException) throw e
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
            val sessionId = (result as? JsonObject)?.get("session_id").sessionIdOrNull()
            val storedId = ((result as? JsonObject)?.get("stored_session_id") as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.takeIf { it.isNotBlank() }
            if (sessionId != null) {
                state.update { it.copy(activeSessionId = sessionId, activeSessionKey = storedId, isSending = false) }
                Timber.i("[Chat] Session created: $sessionId")
            }
        } catch (e: GatewayException) {
            Timber.e(e, "[Chat] Failed to create session")
            state.update { it.copy(
                errorEvent = ErrorEvent.Error("Failed to create session: ${e.message}")
            ) }
        }
    }

    /**
     * Returns null when [sessionId] could not be opened, otherwise whether a
     * turn is still running in it.
     */
    suspend fun resume(
        state: MutableStateFlow<ChatUiState>,
        sessionId: String,
        isLiveId: Boolean = false,
    ): Boolean? {
        try {
            // A live id always 4007s on session.resume first; go straight to activate.
            val attached = sessionRepository.attach(sessionId, preferLive = isLiveId)
            val liveSessionId = attached.liveId
            val history = parseSessionHistory(attached.raw)
            val running = (attached.raw["running"] as? JsonPrimitive)?.content == "true"
            state.update { it.copy(
                activeSessionId = liveSessionId,
                // A drawer row hands over the stored id itself; a live id
                // (notification tap) resolves to itself and names no key.
                activeSessionKey = attached.storedId ?: sessionId.takeIf { it != liveSessionId },
                messages = watchDrop("resume", it.messages, history),
                showSessionDrawer = false,
                errorEvent = null,
                sessionLoadedAt = System.currentTimeMillis(),
                activeTodos = emptyList(),
                pendingApproval = null,
                // isSending is this chat's own turn, not the one of the chat we
                // just left: a busy chat (opened from its "working"
                // notification, say) shows the stop button, an idle one send.
                isSending = running,
            ) }
            loadReasoningLevel()
            // Questions the agent is still blocked on come back with the resume.
            (attached.raw["open_requests"] as? kotlinx.serialization.json.JsonArray)
                ?.let(gatewayClient::redeliverServerRequests)
            if (history.isNotEmpty()) {
                Timber.i("[Chat] Resumed $sessionId as live session $liveSessionId with ${history.size} messages")
            } else {
                Timber.w("[Chat] Resume returned no inline messages, falling back to session.history for $liveSessionId")
                loadHistory(state, liveSessionId)
            }
            return running
        } catch (e: Exception) {
            // Superseded by another resume: not a failure to show.
            if (e is CancellationException) throw e
            Timber.e(e, "[Chat] Failed to resume session")
            state.update { it.copy(errorEvent = ErrorEvent.Error("Failed to resume: ${e.message}")) }
            return null
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

    /**
     * Rebuild the open chat from the server after the socket came back: the
     * stream missed whatever the gateway pushed while it was down, so the
     * server's transcript replaces the local one.
     *
     * [storedId] goes first — a live id dies once the gateway reaps a
     * clientless session, while the stored id resumes the same transcript
     * under a new live id. Returns whether a turn is still running, or null
     * when nothing was applied.
     */
    suspend fun recover(
        state: MutableStateFlow<ChatUiState>,
        liveId: String,
        storedId: String?,
        turnEnded: Boolean = false,
    ): Boolean? {
        val turnEndsBefore = turnEnds
        val attached = try {
            if (storedId != null) {
                sessionRepository.attach(storedId)
            } else {
                sessionRepository.attach(liveId, preferLive = true)
            }
        } catch (e: GatewayException) {
            if (e.message?.startsWith("RPC error") == true) {
                // The server no longer knows either id: land on the most recent
                // chat instead of leaving the screen bound to a dead one.
                Timber.w("[Chat] Recovery of $liveId refused (${e.message}); reopening most recent session")
                createOrResume(state)
            } else {
                Timber.w("[Chat] Recovery of $liveId failed: ${e.message}")
            }
            return null
        } catch (e: Exception) {
            Timber.w(e, "[Chat] Recovery of $liveId failed")
            return null
        }
        // A message.complete handled while this snapshot was in flight is newer
        // than it: the server may have read the transcript before storing the
        // reply, and still reports `running` until well after sending the
        // event. Taken at face value it re-marks the finished turn as running,
        // and with the event already spent nothing ever clears that.
        val endedMeanwhile = turnEnds != turnEndsBefore
        // message.complete goes out before the server clears `running`.
        val running = !turnEnded && !endedMeanwhile &&
            (attached.raw["running"] as? JsonPrimitive)?.content == "true"
        val snapshot = parseSessionHistory(attached.raw)
        var applied = false
        state.update { current ->
            // The user may have switched chats while the snapshot was in flight.
            if (current.activeSessionId != liveId) return@update current
            applied = true
            current.copy(
                activeSessionId = attached.liveId,
                activeSessionKey = attached.storedId ?: current.activeSessionKey,
                messages = watchDrop("recover", current.messages, mergeRecoveredTranscript(snapshot, current.messages)),
                isSending = running,
            )
        }
        if (!applied) return null
        (attached.raw["open_requests"] as? kotlinx.serialization.json.JsonArray)
            ?.let(gatewayClient::redeliverServerRequests)
        Timber.i("[Chat] Recovered $liveId as ${attached.liveId}: ${snapshot.size} messages, running=$running")
        if (endedMeanwhile) {
            // Same refetch the turn end triggers when it comes after the
            // recovery: the stored transcript now holds the reply.
            Timber.i("[Chat] Turn ended during recovery of $liveId; refetching the settled transcript")
            return recover(state, attached.liveId, attached.storedId ?: storedId, turnEnded = true)
        }
        return running
    }

    /**
     * The gateway re-issued the open chat under a new live id mid-request.
     * Adopt it with the transcript it came back with: the old live id was
     * reclaimed, so whatever it produced since the screen last synced (a reply
     * that finished meanwhile) is only in this snapshot.
     */
    fun adoptRebound(
        state: MutableStateFlow<ChatUiState>,
        oldLiveId: String,
        attached: SessionRepository.AttachedSession,
    ) {
        val snapshot = parseSessionHistory(attached.raw)
        state.update { current ->
            if (current.activeSessionId != oldLiveId) return@update current
            current.copy(
                activeSessionId = attached.liveId,
                activeSessionKey = attached.storedId ?: current.activeSessionKey,
                messages = watchDrop("rebound", current.messages, mergeRecoveredTranscript(snapshot, current.messages)),
            )
        }
        Timber.i("[Chat] $oldLiveId was reclaimed; continuing as ${attached.liveId}")
    }

    /**
     * Re-attach busy chats the user is not looking at once the socket is back,
     * keyed live id → stored id. The gateway client brings back at most the
     * one session it last heard from, so any other busy chat would finish
     * detached and its reply never arrive. Returns the live ids whose turn is
     * no longer running under that id (it ended while detached, was
     * reclaimed, or could not be reached), so their busy state can be settled.
     */
    suspend fun reattachBackground(busy: Map<String, String?>): Set<String> =
        busy.filter { (liveId, storedId) ->
            try {
                val attached = if (storedId != null) {
                    sessionRepository.attach(storedId)
                } else {
                    sessionRepository.attach(liveId, preferLive = true)
                }
                attached.liveId != liveId || (attached.raw["running"] as? JsonPrimitive)?.content != "true"
            } catch (e: Exception) {
                Timber.w("[Chat] Could not re-attach background session $liveId: ${e.message}")
                true
            }
        }.keys

    suspend fun loadHistory(state: MutableStateFlow<ChatUiState>, sessionId: String, allowEmpty: Boolean = false) {
        try {
            val params = buildJsonObject { put("session_id", sessionId) }
            val result = gatewayClient.request(GatewayMethods.SESSION_HISTORY, jsonToElementMap(params))
            val messages = parseSessionHistory(result)
            if (messages.isNotEmpty() || allowEmpty) {
                state.update {
                    // Another chat was opened while this history was in flight.
                    if (it.activeSessionId != sessionId) return@update it
                    it.copy(
                        messages = watchDrop("history", it.messages, messages),
                        sessionLoadedAt = System.currentTimeMillis(),
                    )
                }
                Timber.i("[Chat] Loaded ${messages.size} history messages for session $sessionId")
            } else {
                Timber.w("[Chat] Session history returned empty for $sessionId")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "[Chat] Could not load session history for $sessionId, continuing without it")
        }
    }

    /**
     * The chat's user turns as the server stores them, row ids included, or null when
     * the history could not be read. A rewind is aimed with these, never with the
     * bubbles on screen alone (see [findUserRow]).
     */
    suspend fun serverUserTurns(sessionId: String): List<ChatMessage.User>? {
        return try {
            val params = buildJsonObject { put("session_id", sessionId) }
            val result = gatewayClient.request(GatewayMethods.SESSION_HISTORY, jsonToElementMap(params))
            parseSessionHistory(result).filterIsInstance<ChatMessage.User>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "[Chat] Could not read the user turns of session $sessionId")
            null
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
            val newId = (result as? JsonObject)?.get("session_id").sessionIdOrNull()
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
                val rowId = msg["row_id"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }
                when (role) {
                    "user" -> ChatMessage.User(id = id, timestamp = ts, text = content, rowId = rowId)
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

/**
 * The server snapshot wins, except for the user's own trailing messages it
 * never received (a send that died with the socket): those stay on screen so
 * the text is not lost. An empty snapshot means the payload carried no
 * transcript, not that the chat is empty, so the local one is kept.
 */
internal fun mergeRecoveredTranscript(
    snapshot: List<ChatMessage>,
    local: List<ChatMessage>,
): List<ChatMessage> {
    if (snapshot.isEmpty()) return local
    val trailingUsers = local.takeLastWhile { it is ChatMessage.User }.filterIsInstance<ChatMessage.User>()
    val serverTail = snapshot.filterIsInstance<ChatMessage.User>().takeLast(trailingUsers.size)
    val unsent = trailingUsers.filterNot { mine ->
        serverTail.any { it.text.contains(mine.text.trim()) }
    }
    return snapshot + unsent
}

/**
 * Settle the turn's reply into the transcript. The streaming bubble is
 * finalized with [finalText]; when there is none to finalize (its MessageStart
 * or deltas never landed, or a recovery replaced the transcript mid-turn) the
 * reply is appended instead, so a finished turn can never vanish from the chat.
 */
internal fun List<ChatMessage>.withReplyLanded(
    streamingId: String?,
    finalText: String,
    reasoning: String?,
): List<ChatMessage> {
    val hasBubble = any { it is ChatMessage.Assistant && it.isStreaming && (streamingId == null || it.id == streamingId) }
    if (hasBubble) {
        return updateFirst({ msg ->
            msg is ChatMessage.Assistant && msg.isStreaming && (streamingId == null || msg.id == streamingId)
        }) { msg ->
            (msg as ChatMessage.Assistant).copy(
                text = finalText.ifEmpty { msg.text },
                isStreaming = false,
                reasoning = reasoning?.takeIf { it.isNotBlank() } ?: msg.reasoning,
            )
        }
    }
    if (finalText.isBlank()) return this
    // A recovery snapshot may already carry this reply; don't show it twice.
    val lastReply = takeLastWhile { it !is ChatMessage.User }.filterIsInstance<ChatMessage.Assistant>().lastOrNull()
    if (lastReply != null && lastReply.text.trim() == finalText.trim()) return this
    return this + ChatMessage.Assistant(
        id = java.util.UUID.randomUUID().toString(),
        timestamp = System.currentTimeMillis(),
        text = finalText,
        isStreaming = false,
        reasoning = reasoning?.takeIf { it.isNotBlank() },
    )
}

/**
 * A session id the gateway actually gave us, or null.
 *
 * `JsonNull` is a `JsonPrimitive`, and its `.content` is the four-character string
 * "null" — which sailed straight into activeSessionId and produced a stream of
 * `session.activate session_id='null'` calls against a session that never existed.
 * `contentOrNull` handles JsonNull; the literal check covers a server that sends the
 * string "null" in a JSON string.
 */
private fun JsonElement?.sessionIdOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

/** Journals a transcript replacement that removes the reply still streaming on screen. */
internal fun watchDrop(site: String, before: List<ChatMessage>, after: List<ChatMessage>): List<ChatMessage> {
    val streaming = before.firstOrNull { it is ChatMessage.Assistant && it.isStreaming }
    if (streaming != null && after.none { it.id == streaming.id }) {
        Timber.w("[Chat] $site replaced the transcript and dropped the streaming reply (${before.size} -> ${after.size} messages)")
    }
    return after
}

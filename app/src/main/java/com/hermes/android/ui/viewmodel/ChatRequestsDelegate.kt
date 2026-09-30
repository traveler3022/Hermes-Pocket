package com.hermes.android.ui.viewmodel

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.service.AppForegroundState
import com.hermes.android.service.ApprovalNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * What the agent waits on in the chat: approving a command (approval.respond), and the
 * clarify / sudo / secret questions. Those are server→client requests: the agent blocks
 * until a response frame with the request's id comes back. They have no RPC of their own
 * (the old clarify.respond / sudo.respond / secret.respond don't exist on the server, so
 * answers never arrived).
 */
internal class ChatRequestsDelegate(
    private val gatewayClient: GatewayClient,
    private val approvalNotificationManager: ApprovalNotificationManager,
    private val foregroundState: AppForegroundState,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ChatUiState>,
) {
    fun respondToApproval(choice: String) {
        val pending = state.value.pendingApproval ?: return
        state.update { it.copy(pendingApproval = null) }
        approvalNotificationManager.cancelApproval(pending.requestId)
        scope.launch {
            try {
                gatewayClient.request(
                    method = GatewayMethods.APPROVAL_RESPOND,
                    params = buildJsonObject {
                        pending.sessionId?.let { sid -> put("session_id", sid) }
                        put("request_id", pending.requestId)
                        put("choice", choice)
                        put("all", false)
                    },
                )
                Timber.i("[Chat] Approval response sent: $choice")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "[Chat] Failed to respond to approval")
                // Never delivered (no socket, timed out): the agent is still blocked on
                // it and the sheet was already gone, so nothing could answer it again.
                // A refusal from the server (it carries a code) means the request is gone.
                val undelivered = (e as? GatewayException)?.code == null
                state.update { it.copy(
                    pendingApproval = if (undelivered && it.pendingApproval == null) pending else it.pendingApproval,
                    errorEvent = ErrorEvent.Error(e.message ?: "Unknown error"),
                ) }
            }
        }
    }

    /** [picked]: the typed text, the chosen option, or — multi-select — every chosen option. */
    fun respondToClarify(requestId: String, picked: List<String>) {
        val card = findInteractiveRequest(requestId) ?: return
        answerServerRequest(
            requestId,
            kotlinx.serialization.json.JsonObject(
                mapOf("answer" to JsonPrimitive(clarifyAnswer(picked, card.multiSelect))),
            ),
        )
    }

    /** Batch clarify: every question's answer, keyed by its qid. */
    fun respondToClarifyBatch(requestId: String, answers: Map<String, List<String>>) {
        val card = findInteractiveRequest(requestId) ?: return
        val encoded = card.questions.associate { q ->
            q.qid to JsonPrimitive(clarifyAnswer(answers[q.qid].orEmpty(), q.multiSelect))
        }
        answerServerRequest(
            requestId,
            kotlinx.serialization.json.JsonObject(mapOf("answers" to kotlinx.serialization.json.JsonObject(encoded))),
        )
    }

    fun respondToSudo(requestId: String, password: String) =
        answerServerRequest(requestId, kotlinx.serialization.json.JsonObject(mapOf("value" to JsonPrimitive(password))))

    fun respondToSecret(requestId: String, value: String) =
        answerServerRequest(requestId, kotlinx.serialization.json.JsonObject(mapOf("value" to JsonPrimitive(value))))

    /**
     * An approval the agent is blocked on. [sessionKey]: the stored id the drawer marks the
     * waiting chat by; the event only has the live one.
     */
    fun onApprovalRequest(event: GatewayEvent.ApprovalRequest, sessionKey: String?) {
        // The server's queue id: approval.respond resolves exactly this
        // entry (a random id here left the server guessing which one).
        val requestId = event.requestId.ifBlank { event.serverRequestId }
        // In the app the approval sheet asks; the notification is for when the user is away.
        if (!foregroundState.isForeground) {
            approvalNotificationManager.showApprovalRequest(
                requestId = requestId,
                sessionId = event.sessionId,
                toolName = "terminal",
                command = event.command,
                description = event.description,
                allowPermanent = event.allowPermanent,
            )
        }
        // The approval sheet and the notification show the request. It is not added
        // to the chat: as plain text it stayed there for good, raw command and all.
        state.update { it.copy(
            pendingApproval = PendingApprovalUi(
                requestId = requestId,
                sessionId = event.sessionId,
                command = event.command,
                description = event.description,
                patternKeys = event.patternKeys,
                allowPermanent = event.allowPermanent,
                serverRequestId = event.serverRequestId,
                sessionKey = sessionKey,
                choices = event.choices,
            ),
        ) }
    }

    fun onClarifyRequest(event: GatewayEvent.ClarifyRequest) = addInteractiveRequest(
        event.sessionId,
        ChatMessage.InteractiveRequest(
            id = event.requestId,
            timestamp = System.currentTimeMillis(),
            requestId = event.requestId,
            question = event.question,
            choices = event.choices,
            kind = InteractiveKind.CLARIFY,
            multiSelect = event.multiSelect,
            questions = event.questions.map { ClarifyQuestionUi(it.qid, it.question, it.choices, it.multiSelect) },
        ),
    )

    fun onSudoRequest(event: GatewayEvent.SudoRequest) = addInteractiveRequest(
        event.sessionId,
        ChatMessage.InteractiveRequest(
            id = event.requestId,
            timestamp = System.currentTimeMillis(),
            requestId = event.requestId,
            question = "",
            choices = null,
            kind = InteractiveKind.SUDO,
        ),
    )

    fun onSecretRequest(event: GatewayEvent.SecretRequest) = addInteractiveRequest(
        event.sessionId,
        ChatMessage.InteractiveRequest(
            id = event.requestId,
            timestamp = System.currentTimeMillis(),
            requestId = event.requestId,
            question = event.prompt,
            choices = null,
            kind = InteractiveKind.SECRET,
        ),
    )

    private fun findInteractiveRequest(requestId: String): ChatMessage.InteractiveRequest? =
        state.value.messages.lastOrNull {
            it is ChatMessage.InteractiveRequest && it.requestId == requestId
        } as? ChatMessage.InteractiveRequest

    /** The clarify tool reads a multi-select answer as a JSON list and anything else as the bare string. */
    private fun clarifyAnswer(picked: List<String>, multiSelect: Boolean): String {
        val values = picked.filter { it.isNotBlank() }
        return if (multiSelect) {
            kotlinx.serialization.json.JsonArray(values.map { JsonPrimitive(it) }).toString()
        } else {
            values.firstOrNull().orEmpty()
        }
    }

    private fun answerServerRequest(requestId: String, result: kotlinx.serialization.json.JsonObject) {
        if (gatewayClient.respondToServerRequest(requestId, result)) {
            markAnswered(requestId)
        } else {
            state.update { it.copy(errorEvent = ErrorEvent.Error("Not connected — answer not sent")) }
        }
    }

    /**
     * Show a question the agent is blocked on — only in its own chat (another
     * chat's comes back through `open_requests` when that chat is opened), and
     * only once: a reconnect replay re-delivers the same request id.
     */
    private fun addInteractiveRequest(sessionId: String?, request: ChatMessage.InteractiveRequest) {
        state.update { current ->
            if (sessionId != null && sessionId != current.activeSessionId) return@update current
            if (current.messages.any { it.id == request.id }) return@update current
            current.copy(messages = current.messages + request)
        }
    }

    /** The server withdrew a request (timeout, interrupt, answered elsewhere): its card stops taking answers. */
    fun onRequestCancel(serverRequestId: String) {
        val pending = state.value.pendingApproval
        if (pending != null && pending.serverRequestId == serverRequestId) {
            approvalNotificationManager.cancelApproval(pending.requestId)
        }
        state.update { current ->
            current.copy(
                pendingApproval = current.pendingApproval?.takeUnless { it.serverRequestId == serverRequestId },
                messages = current.messages.updateFirst({ msg ->
                    msg is ChatMessage.InteractiveRequest && msg.requestId == serverRequestId && !msg.answered
                }) { msg ->
                    (msg as ChatMessage.InteractiveRequest).copy(expired = true)
                },
            )
        }
    }

    private fun markAnswered(requestId: String) {
        state.update { it.copy(
            messages = state.value.messages.updateFirst({ msg ->
                msg is ChatMessage.InteractiveRequest && msg.requestId == requestId
            }) { msg ->
                (msg as ChatMessage.InteractiveRequest).copy(answered = true)
            }
        ) }
    }
}

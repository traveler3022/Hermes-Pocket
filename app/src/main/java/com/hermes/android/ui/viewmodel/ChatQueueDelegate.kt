package com.hermes.android.ui.viewmodel

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.util.UUID

/**
 * "Send when done": messages held per chat and sent, one at a time and in order, when that
 * chat's turn finishes normally. The open chat sends through the screen's own path (its
 * bubble, its row id); a chat in the background is sent to straight away so its queue still
 * moves when the user is looking at another chat.
 *
 * Every send carries `queued: true`: if the chat is busy again by then (a goal follow-up,
 * an auto-continue), Hermes queues it behind that turn instead of cutting it off
 * (session_auto_continue._handle_busy_submit).
 *
 * Lives in memory only: a process death loses what is waiting.
 */
internal class ChatQueueDelegate(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ChatUiState>,
    /** Sends [item] in the open chat, under its live id, as a normal user message would go. */
    private val sendInOpenChat: (liveId: String, item: QueuedSend) -> Unit,
) {
    private val queue = ChatSendQueue()

    init {
        // The panel shows the open chat's queue: republish when another chat opens.
        scope.launch {
            state.map { it.activeSessionKey to it.activeSessionId }
                .distinctUntilChanged()
                .collect { publish() }
        }
    }

    /** The ids the open chat may be queued under: its stored key first, then its live id. */
    private fun openChatKeys(): List<String> = state.value.let { listOfNotNull(it.activeSessionKey, it.activeSessionId) }

    /**
     * Queues [text] for the open chat. When its turn already ended (the reply landed while
     * the menu was open), it goes now.
     */
    fun enqueue(text: String, outgoing: String, attachments: List<PendingAttachment>) {
        val keys = openChatKeys()
        if (keys.isEmpty()) return
        queue.enqueue(keys, QueuedSend(UUID.randomUUID().toString(), text, outgoing, attachments))
        publish()
        val current = state.value
        val liveId = current.activeSessionId
        if (!current.isSending && liveId != null) sendNext(liveId, keys)
    }

    /** Takes [id] out of the open chat's queue so its text can go back into the composer. */
    fun takeForEdit(id: String): QueuedSend? = queue.remove(openChatKeys(), id).also { publish() }

    fun cancel(id: String) {
        queue.remove(openChatKeys(), id)
        publish()
    }

    /**
     * A turn of [liveId] ended with [status]; [keys] are every id that chat may be queued
     * under. Called after the screen has handled the event, so the open chat is idle again
     * before the next message makes it busy.
     */
    fun onTurnEnded(liveId: String, keys: List<String>, status: String?) {
        val next = queue.onTurnEnded(keys, turnOutcomeOf(status)) ?: return
        send(liveId, keys, next)
    }

    private fun sendNext(liveId: String, keys: List<String>) {
        val next = queue.takeNext(keys) ?: return
        send(liveId, keys, next)
    }

    private fun send(liveId: String, keys: List<String>, item: QueuedSend) {
        publish()
        if (liveId == state.value.activeSessionId) {
            sendInOpenChat(liveId, item)
            return
        }
        scope.launch {
            try {
                gatewayClient.request(
                    GatewayMethods.PROMPT_SUBMIT,
                    mapOf(
                        "text" to JsonPrimitive(item.outgoing),
                        "session_id" to JsonPrimitive(liveId),
                        "queued" to JsonPrimitive(true),
                    ),
                )
                Timber.i("[Queue] sent a queued message to background session $liveId")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Kept, at the front: it goes with the chat's next finished turn, or the
                // user takes it back with Edit once the chat is open.
                Timber.w(e, "[Queue] background send to $liveId failed; keeping it queued")
                queue.pushFront(keys, item)
                publish()
            }
        }
    }

    private fun publish() {
        val items = queue.items(openChatKeys())
        state.update { if (it.queuedSends == items) it else it.copy(queuedSends = items) }
    }
}

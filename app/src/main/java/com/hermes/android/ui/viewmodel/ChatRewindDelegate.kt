package com.hermes.android.ui.viewmodel

import com.hermes.android.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/**
 * Changing what was already said: sending the last message again, editing a sent one (both
 * resend it with the stored chat cut before it), and deleting from a message on.
 */
internal class ChatRewindDelegate(
    private val sessionRepository: SessionRepository,
    private val sessionDelegate: ChatSessionDelegate,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ChatUiState>,
    private val clearDraft: () -> Unit,
    /** prompt.submit of text as bubbleId, the stored chat cut before the first of rows it takes. */
    private val resend: (text: String, sessionId: String, rows: List<Long>, bubbleId: String, onRefused: () -> Unit) -> Unit,
) {
    fun retryLast() {
        val sessionId = state.value.activeSessionId ?: return
        if (state.value.isSending) return

        val lastUserMsg = state.value.messages.filterIsInstance<ChatMessage.User>().lastOrNull() ?: return
        val refs = lastUserMsg.attachments.mapNotNull { it.refText }
        val lastUserText = if (refs.isEmpty()) {
            sentText(lastUserMsg)
        } else {
            (sentText(lastUserMsg) + "\n" + refs.joinToString("\n")).trim()
        }

        val before = state.value.messages
        val lastUserIndex = before.indexOfLast { it is ChatMessage.User }
        state.update { it.copy(messages = before.subList(0, lastUserIndex + 1).toList(), isSending = true) }

        scope.launch {
            val rows = locateOnServer(sessionId, before, lastUserMsg)
            if (rows == null) {
                state.update { it.copy(messages = before, isSending = false) }
                return@launch
            }
            resend(lastUserText, sessionId, rows, lastUserMsg.id) {
                state.update { it.copy(messages = before) }
            }
        }
    }

    /** Puts a sent message back in the composer; sending replaces it and what followed. */
    fun startEditing(messageId: String) {
        if (state.value.isSending) return
        val message = state.value.messages.firstOrNull { it.id == messageId } as? ChatMessage.User ?: return
        state.update { it.copy(editingMessageId = messageId, inputText = sentText(message)) }
    }

    fun cancelEditing() {
        state.update { it.copy(editingMessageId = null, inputText = "") }
    }

    fun submitEdit(messageId: String, text: String) {
        val sessionId = state.value.activeSessionId ?: return
        if (state.value.isSending) return
        val before = state.value.messages
        val index = before.indexOfFirst { it.id == messageId }
        val target = before.getOrNull(index) as? ChatMessage.User ?: run { cancelEditing(); return }
        // File references ride along as they did the first time; the edit is to the words.
        val refs = target.attachments.mapNotNull { it.refText }
        val outgoing = if (refs.isEmpty()) text else (text + "\n" + refs.joinToString("\n")).trim()
        val edited = ChatMessage.User(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            text = text,
            attachments = target.attachments,
        )
        clearDraft()
        state.update { it.copy(
            messages = before.subList(0, index) + edited,
            editingMessageId = null,
            inputText = "",
            isSending = true,
        ) }
        scope.launch {
            val rows = locateOnServer(sessionId, before, target)
            if (rows == null) {
                state.update { it.copy(messages = before, isSending = false, editingMessageId = messageId, inputText = text) }
                return@launch
            }
            // Refused (busy, stale): the server kept everything, so the screen must too.
            resend(outgoing, sessionId, rows, edited.id) {
                state.update { it.copy(messages = before, editingMessageId = messageId, inputText = text) }
            }
        }
    }

    /**
     * Removes a sent message and everything after it. The server has no cut without a
     * new prompt, only session.undo (the last turn), so the turns from the message to
     * the end are undone one by one, and the chat then reloads what the server kept.
     */
    fun deleteFrom(messageId: String) {
        val sessionId = state.value.activeSessionId ?: return
        if (state.value.isSending) return
        val messages = state.value.messages
        val target = messages.firstOrNull { it.id == messageId } as? ChatMessage.User ?: return
        scope.launch {
            try {
                val server = sessionDelegate.serverUserTurns(sessionId)
                val rowId = server?.let { findUserRow(messages, target, it) }
                if (server == null || rowId == null) {
                    state.update { it.copy(errorEvent = ErrorEvent.Error(MESSAGE_NOT_LOCATED)) }
                    return@launch
                }
                for (turn in 1..turnsFrom(server, rowId)) {
                    if (sessionRepository.undoLastTurn(sessionId) == 0) break
                }
            } catch (e: Exception) {
                Timber.w(e, "[Chat] delete from message failed")
                state.update { it.copy(errorEvent = ErrorEvent.Error(rewindFailure(e))) }
            }
            sessionDelegate.loadHistory(state, sessionId, allowEmpty = true)
        }
    }

    /** [target]'s row on the server, or null after telling the user it could not be found. */
    private suspend fun locateOnServer(
        sessionId: String,
        local: List<ChatMessage>,
        target: ChatMessage.User,
    ): List<Long>? {
        val server = sessionDelegate.serverUserTurns(sessionId)
        val rowId = server?.let { findUserRow(local, target, it) }
        if (rowId == null) {
            state.update { it.copy(errorEvent = ErrorEvent.Error(MESSAGE_NOT_LOCATED)) }
            return null
        }
        // The target first; the hidden rows are only tried if the server refuses it.
        return listOf(rowId) + hiddenRowsBefore(server, rowId)
    }

    private companion object {
        const val MESSAGE_NOT_LOCATED =
            "Could not find this message in the stored chat, so nothing was changed. Reopen the chat and try again."
    }
}

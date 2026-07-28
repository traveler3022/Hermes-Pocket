package com.hermes.android.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal class ChatStreamingDelegate(
    private val scope: CoroutineScope,
) {
    private var activeAssistantMessageId: String? = null
    private val streamingBuffer = StringBuilder()
    private val reasoningBuffer = StringBuilder()
    private var streamingFlushJob: Job? = null

    val currentAssistantMessageId: String? get() = activeAssistantMessageId

    fun onMessageStart(): String {
        streamingFlushJob?.cancel()
        streamingFlushJob = null
        streamingBuffer.setLength(0)
        reasoningBuffer.setLength(0)
        val msgId = java.util.UUID.randomUUID().toString()
        activeAssistantMessageId = msgId
        return msgId
    }

    fun enqueueDelta(text: String, isReasoning: Boolean = false) {
        if (text.isEmpty()) return
        (if (isReasoning) reasoningBuffer else streamingBuffer).append(text)
        if (streamingFlushJob?.isActive == true) return
        streamingFlushJob = scope.launch {
            delay(STREAM_FLUSH_INTERVAL_MS)
            flushBuffer()
            streamingFlushJob = null
        }
    }

    fun flushBuffer(state: MutableStateFlow<ChatUiState>) {
        if (streamingBuffer.isEmpty() && reasoningBuffer.isEmpty()) return
        val chunk = streamingBuffer.toString()
        streamingBuffer.setLength(0)
        val reasoningChunk = reasoningBuffer.toString()
        reasoningBuffer.setLength(0)
        val targetId = activeAssistantMessageId
        state.update { it.copy(
            messages = it.messages.updateFirst({ msg ->
                msg is ChatMessage.Assistant && msg.isStreaming &&
                    (targetId == null || msg.id == targetId)
            }) { msg ->
                (msg as ChatMessage.Assistant).copy(
                    text = msg.text + chunk,
                    reasoning = if (reasoningChunk.isEmpty()) msg.reasoning
                        else (msg.reasoning ?: "") + reasoningChunk,
                )
            }
        ) }
    }

    fun reset() {
        streamingFlushJob?.cancel()
        streamingFlushJob = null
        streamingBuffer.setLength(0)
        reasoningBuffer.setLength(0)
        activeAssistantMessageId = null
    }

    fun finalizeOrphanedMessage(state: MutableStateFlow<ChatUiState>, marker: String) {
        val orphanedId = activeAssistantMessageId ?: return
        var found = false
        state.update { it.copy(
            messages = it.messages.updateFirst({ msg ->
                msg is ChatMessage.Assistant && msg.isStreaming && msg.id == orphanedId
            }) { msg ->
                found = true
                (msg as ChatMessage.Assistant).copy(
                    isStreaming = false,
                    text = if (msg.text.isBlank()) marker else "${msg.text}\n\n$marker",
                )
            },
            isSending = false,
        ) }
        if (found) {
            Timber.w("[Chat] Finalized orphaned streaming message $orphanedId with marker: $marker")
        }
        activeAssistantMessageId = null
        reset()
    }

    private companion object {
        private const val STREAM_FLUSH_INTERVAL_MS = 80L
    }
}

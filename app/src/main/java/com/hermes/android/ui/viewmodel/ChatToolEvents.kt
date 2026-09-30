package com.hermes.android.ui.viewmodel

import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.asText
import java.util.UUID

/**
 * A tool the agent started, or started again: its card, marked with how much of [replyId]'s
 * reasoning came before it. [replyId] is the reply streaming now.
 */
internal fun ChatUiState.withToolStarted(event: GatewayEvent.ToolStart, replyId: String?): ChatUiState {
    val existing = messages.firstOrNull { it.id == event.toolId } as? ChatMessage.ToolCall
    val exists = messages.any { it.id == event.toolId }
    val ownerId = existing?.reasoningOwnerId ?: replyId
    val mark = existing?.reasoningMark
        ?: (messages.firstOrNull { it.id == ownerId } as? ChatMessage.Assistant)
            ?.reasoning?.length
        ?: 0
    val toolMsg = ChatMessage.ToolCall(
        id = event.toolId,
        timestamp = System.currentTimeMillis(),
        toolName = event.name ?: "unknown",
        argsText = event.argsText,
        resultText = null,
        error = null,
        isRunning = true,
        durationS = null,
        reasoningOwnerId = ownerId,
        reasoningMark = mark,
    )
    return copy(
        messages = if (exists) {
            messages.updateFirst({ it.id == event.toolId }) { toolMsg }
        } else {
            messages + toolMsg
        },
        activeTodos = event.todos?.toUiTodos() ?: activeTodos,
    )
}

internal fun ChatUiState.withToolCompleted(event: GatewayEvent.ToolComplete): ChatUiState = copy(
    messages = messages.updateFirst({ msg ->
        msg is ChatMessage.ToolCall && msg.id == event.toolId
    }) { msg ->
        (msg as ChatMessage.ToolCall).copy(
            resultText = event.resultText ?: event.result,
            error = event.error,
            isRunning = false,
            durationS = event.durationS,
        )
    },
    activeTodos = event.todos?.toUiTodos() ?: activeTodos,
)

/**
 * tool.progress names its tool but carries no id. Matching any running card put one tool's
 * output on another when two ran at once.
 */
internal fun List<ChatMessage>.withToolProgress(event: GatewayEvent.ToolProgress): List<ChatMessage> =
    updateFirst({ msg ->
        msg is ChatMessage.ToolCall && msg.isRunning &&
            (event.name == null || msg.toolName == event.name)
    }) { msg ->
        (msg as ChatMessage.ToolCall).copy(resultText = event.preview)
    }

internal fun List<GatewayEvent.TodoItem>.toUiTodos(): List<TodoItemUi> =
    map { todo ->
        TodoItemUi(
            id = todo.id,
            content = todo.content,
            status = when (todo.status) {
                "in_progress" -> TodoStatus.IN_PROGRESS
                "completed" -> TodoStatus.COMPLETED
                "cancelled" -> TodoStatus.CANCELLED
                else -> TodoStatus.PENDING
            },
        )
    }

/**
 * A delegated child's progress (tools/delegate_tool.py, relayed by tui_gateway): every
 * frame names the child by `subagent_id` and carries its `goal`; `text` is its latest
 * line and `summary` its result. spawn_requested and start name the same child, so a
 * card is updated in place rather than added twice.
 */
internal fun List<ChatMessage>.withSubagentEvent(event: GatewayEvent.SubagentEvent): List<ChatMessage> {
    val p = event.payload
    val cardId = (p["subagent_id"] ?: p["id"]).asText()?.takeIf { it.isNotBlank() }?.let { "subagent-$it" }
    fun isOpenCard(msg: ChatMessage) =
        msg is ChatMessage.SubagentCard && !msg.isComplete && (cardId == null || msg.id == cardId)
    return when (event.subagentType) {
        "spawn_requested", "start" -> {
            val goal = (p["goal"] ?: p["description"] ?: p["text"]).asText()
                ?.takeIf { it.isNotBlank() } ?: "Sub-agent"
            if (cardId != null && any { it.id == cardId }) {
                updateFirst({ it.id == cardId }) { msg ->
                    (msg as ChatMessage.SubagentCard).copy(subagentType = event.subagentType, text = goal)
                }
            } else {
                this + ChatMessage.SubagentCard(
                    id = cardId ?: "subagent-${UUID.randomUUID()}",
                    timestamp = System.currentTimeMillis(),
                    subagentType = event.subagentType,
                    text = goal,
                )
            }
        }
        "complete" -> {
            val result = (p["summary"] ?: p["text"]).asText().orEmpty()
            updateFirst(::isOpenCard) { msg ->
                (msg as ChatMessage.SubagentCard).copy(isComplete = true, text = result.ifEmpty { msg.text })
            }
        }
        "thinking", "progress", "tool" -> {
            val line = (p["text"] ?: p["tool_preview"]).asText()?.takeIf { it.isNotBlank() } ?: return this
            updateFirst(::isOpenCard) { msg ->
                (msg as ChatMessage.SubagentCard).copy(text = line)
            }
        }
        else -> this
    }
}

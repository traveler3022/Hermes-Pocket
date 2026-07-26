package com.hermes.android.ui.viewmodel

/**
 * UI-facing state models for the Chat screen.
 *
 * Per Phase 1.5 Rule 1 (Strict Layer Dependency):
 *   ui.screen → ui.viewmodel ONLY
 *   ui.screen must NOT import from gateway or runtime packages
 *
 * The ViewModel converts GatewayEvent → ChatUiState and exposes it.
 */

/**
 * A single message in the chat transcript.
 */
sealed class ChatMessage {
    abstract val id: String
    abstract val timestamp: Long

    /** User-sent message. [text] is exactly what the user typed; attached
     *  files/images live in [attachments] and render as separate elements —
     *  never merged into the text. */
    data class User(
        override val id: String,
        override val timestamp: Long,
        val text: String,
        val attachments: List<PendingAttachment> = emptyList(),
    ) : ChatMessage()

    /** Assistant message (streaming or complete). */
    data class Assistant(
        override val id: String,
        override val timestamp: Long,
        val text: String,
        val isStreaming: Boolean,
        val reasoning: String?,
    ) : ChatMessage()

    /** Tool call card. */
    data class ToolCall(
        override val id: String,
        override val timestamp: Long,
        val toolName: String,
        val argsText: String?,
        val resultText: String?,
        val error: String?,
        val isRunning: Boolean,
        val durationS: Double?,
    ) : ChatMessage()

    /** Status/error line. */
    data class Status(
        override val id: String,
        override val timestamp: Long,
        val text: String,
        val isError: Boolean,
    ) : ChatMessage()

    /** Interactive request: clarify question, sudo prompt, or secret input. */
    data class InteractiveRequest(
        override val id: String,
        override val timestamp: Long,
        val requestId: String,
        val question: String,
        val choices: List<String>?,
        val answered: Boolean = false,
        val kind: InteractiveKind = InteractiveKind.CLARIFY,
    ) : ChatMessage()

    /** Sub-agent execution card. */
    data class SubagentCard(
        override val id: String,
        override val timestamp: Long,
        val subagentType: String,
        val text: String,
        val isComplete: Boolean = false,
    ) : ChatMessage()
}

enum class InteractiveKind { CLARIFY, SUDO, SECRET }

/**
 * One entry of the agent's live task list (from tool.start/tool.complete
 * `todos` payload). UI-facing mirror of the gateway model — ui.screen must
 * not import from the gateway package (Phase 1.5 Rule 1).
 */
data class TodoItemUi(
    val id: String,
    val content: String,
    val status: TodoStatus,
)

enum class TodoStatus { PENDING, IN_PROGRESS, COMPLETED, CANCELLED }

data class NotificationUi(
    val key: String?,
    val kind: String?,
    val level: String?,
    val text: String?,
    val ttlMs: Long?,
)

/**
 * A pending tool-approval request (`approval.request` event), rendered as a
 * modal bottom sheet over the chat. The gateway blocks the turn until
 * `approval.respond` is sent, so the sheet stays up until the user picks
 * Deny / Allow once / Always allow.
 */
data class PendingApprovalUi(
    val requestId: String,
    val sessionId: String?,
    val command: String,
    val description: String,
    val patternKeys: List<String> = emptyList(),
    /** When false the "always allow" choice must not be offered
     *  (mirrors upstream allow_permanent). */
    val allowPermanent: Boolean = true,
)

/**
 * A conversation session.
 */
data class SessionItem(
    val id: String,
    val title: String,
    val lastMessagePreview: String?,
    val updatedAt: Long,
    /** Number of messages in this session (if known). */
    val messageCount: Int? = null,
)

/** Rename dialog state for the session drawer. */
data class DrawerRenameState(
    val sessionId: String,
    val currentTitle: String,
    val inputText: String = currentTitle,
)

/**
 * Overall state of the Chat screen.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sessions: List<SessionItem> = emptyList(),
    val activeSessionId: String? = null,
    val connectionState: ChatConnectionState = ChatConnectionState.Disconnected,
    val inputText: String = "",
    val isSending: Boolean = false,
    val errorEvent: ErrorEvent? = null,
    val showSessionDrawer: Boolean = false,
    // Feature #16: Search in current chat
    val searchQuery: String = "",
    val showSearch: Boolean = false,
    // Drawer: search / sort / pin / rename / delete
    val drawerSearchQuery: String = "",
    val drawerSortNewest: Boolean = true,
    val drawerPinnedIds: Set<String> = emptySet(),
    val drawerRenameTarget: DrawerRenameState? = null,
    val drawerDeleteTarget: String? = null,
    // Triggers scroll-to-bottom on session load (changes value each time)
    val sessionLoadedAt: Long = 0L,
    // Files/images staged on the gateway, waiting to go with the next prompt
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val isAttaching: Boolean = false,
    // Agent's live task list for the current turn (empty = no plan to show)
    val activeTodos: List<TodoItemUi> = emptyList(),
    // Tool-approval request awaiting the user's decision (modal sheet).
    val pendingApproval: PendingApprovalUi? = null,
    // Reasoning effort (agent.reasoning_effort) — quick-switchable from the
    // chat input bar, mirrors the same setting in Settings > General.
    val reasoningLevel: String = "medium",
    // Client-side display name shown in the top bar / drawer header. Purely
    // cosmetic (local prefs) — does not affect the agent's actual identity
    // (SOUL.md / display.personality on the server).
    val assistantName: String = "Hermes",
    // Client-side avatar image (local file path), customized from Settings.
    // Null shows the default icon.
    val assistantAvatarPath: String? = null,
)

/**
 * A file or image already uploaded to the gateway (over the loopback
 * WebSocket), queued to be referenced by the next prompt.
 *
 * Images are queued gateway-side by `image.attach_bytes` and consumed
 * automatically by the next `prompt.submit`; [gatewayPath] lets us
 * `image.detach` them. Non-image files come back from `file.attach` with a
 * [refText] (`@file:...`) that must be appended to the prompt text.
 */
data class PendingAttachment(
    val name: String,
    val isImage: Boolean,
    val gatewayPath: String? = null,
    val refText: String? = null,
    /** content:// URI of the picked file, for thumbnail preview in the bubble. */
    val localUri: String? = null,
)

enum class ChatConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Reconnecting,
    Failed,
}

/**
 * Typed error events with severity and auto-dismiss timing.
 * Replaces the old string-only errorMessage for richer UX.
 */
sealed class ErrorEvent {
    abstract val message: String
    /** Duration in ms after which the UI should auto-dismiss. 0 = manual dismiss only. */
    abstract val autoDismissMs: Long

    /** Transient issue that self-resolves (reconnecting, rate-limit backoff). */
    data class Warning(override val message: String, override val autoDismissMs: Long = 4000) : ErrorEvent()
    /** Actionable error the user should see (send failed, attach failed). */
    data class Error(override val message: String, override val autoDismissMs: Long = 6000) : ErrorEvent()
    /** Critical — connection dead, gateway unreachable. Stays until resolved. */
    data class Critical(override val message: String, override val autoDismissMs: Long = 0) : ErrorEvent()
}

/**
 * Slash command autocomplete suggestion.
 */
data class SlashCommandSuggestion(
    val command: String,
    val description: String,
)

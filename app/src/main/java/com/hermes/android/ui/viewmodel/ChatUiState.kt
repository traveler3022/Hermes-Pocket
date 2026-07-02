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

data class NotificationUi(
    val key: String?,
    val kind: String?,
    val level: String?,
    val text: String?,
    val ttlMs: Long?,
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
    val errorMessage: String? = null,
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
 * Slash command autocomplete suggestion.
 */
data class SlashCommandSuggestion(
    val command: String,
    val description: String,
)

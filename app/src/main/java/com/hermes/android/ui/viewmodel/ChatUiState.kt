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
        /** Typed before the gateway was live; goes out by itself once it is. */
        val queued: Boolean = false,
        /** Durable SQLite row id from session.history, when known. The server rejects
         *  ordinal-only truncation (retry/regenerate) with RPC 4004 unless this rides
         *  along with the ordinal, so it has to survive from history into a retry. */
        val rowId: Long? = null,
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
        /** The assistant message that was streaming when this tool started, and
         *  how much of its reasoning had arrived by then. The gateway streams a
         *  whole turn's reasoning into one message, so this is the only record of
         *  which part came before the tool and which after. Unset for history. */
        val reasoningOwnerId: String? = null,
        val reasoningMark: Int = 0,
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
        /** Clarify: more than one of [choices] may be picked. */
        val multiSelect: Boolean = false,
        /** Batch clarify: several questions answered together (instead of [question]/[choices]). */
        val questions: List<ClarifyQuestionUi> = emptyList(),
        /** The server withdrew the request (timeout, interrupt) before it was answered. */
        val expired: Boolean = false,
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

/** One question of a batch clarify card. */
data class ClarifyQuestionUi(
    val qid: String,
    val question: String,
    val choices: List<String>?,
    val multiSelect: Boolean,
)

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
    /** The `srq-…` id `request.cancel` names when the server withdraws this approval. */
    val serverRequestId: String = "",
    /** Stored id of [sessionId]'s chat, when known: the drawer's rows are keyed by it. */
    val sessionKey: String? = null,
    /** The answers the server accepts, from once | session | always | deny. */
    val choices: List<String> = listOf("once", "session", "always", "deny"),
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
 * A prompt the user sent while Hermes was still booting. Hermes takes seconds to come
 * up inside Alpine, and making the user watch that and then retype is the worst of both
 * worlds; the message waits here and leaves as soon as there is a session to send it to.
 */
data class QueuedPrompt(
    /** Id of the [ChatMessage.User] bubble already on screen for this prompt. */
    val bubbleId: String,
    /** Fully expanded text (prompt plus any attachment refs) to hand the gateway. */
    val outgoing: String,
    val isSlashCommand: Boolean,
)

/**
 * Overall state of the Chat screen.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sessions: List<SessionItem> = emptyList(),
    val activeSessionId: String? = null,
    /** Stored id of the open chat — its identity. [activeSessionId] is only the
     *  live id the gateway currently runs it under, and dies when the gateway
     *  reclaims the session. */
    val activeSessionKey: String? = null,
    /** Waiting for a live session; sent the moment one exists. */
    val queuedPrompt: QueuedPrompt? = null,
    val connectionState: ChatConnectionState = ChatConnectionState.Disconnected,
    /** When the current connect attempt began (0 = not connecting); drives the wait UI. */
    val connectingSince: Long = 0L,
    /** This device's own rolling average for how long a cold start takes, in ms (0 = unknown). */
    val bootEstimateMs: Long = 0L,
    val inputText: String = "",
    /** The sent message being edited: the composer holds its text, and sending
     *  replaces it and everything after it. Null when composing normally. */
    val editingMessageId: String? = null,
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
    // Turn state of every live session, so the drawer can show which other
    // chats are working and which replied while the user was away.
    val sessionActivity: Map<String, SessionActivity> = emptyMap(),
    // Tool-approval request awaiting the user's decision (modal sheet).
    val pendingApproval: PendingApprovalUi? = null,
    // Reasoning effort (agent.reasoning_effort) — quick-switchable from the
    // chat input bar, mirrors the same setting in Settings > General.
    val reasoningLevel: String = "medium",
    // The open chat's model from its latest session.info (reports a queued
    // mid-turn pick while pending). [sessionInfoSeq] bumps on every event so a
    // repeat of the same model still re-syncs the picker.
    val sessionModel: String? = null,
    val sessionProvider: String? = null,
    val sessionInfoSeq: Int = 0,
    /** The open chat's working directory from its latest session.info; a relative `::preview` file is in it. */
    val sessionCwd: String? = null,
    /** The agent's live status line (thinking.delta: a wait notice, a spinner
     *  phrase). Replaced, never appended; blank when there is none. */
    val thinkingStatus: String = "",
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

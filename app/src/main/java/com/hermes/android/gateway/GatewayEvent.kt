package com.hermes.android.gateway

/**
 * Server → Client events from the tui_gateway.
 *
 * Wire format (JSON-RPC 2.0 notification):
 * ```json
 * {
 *   "jsonrpc": "2.0",
 *   "method": "event",
 *   "params": {
 *     "event": "<event-type>",
 *     "sid": "<session-id>",
 *     "payload": { ... }
 *   }
 * }
 * ```
 *
 * This sealed class models the `params` object (event type + session id + payload).
 *
 * Reference: `tui_gateway/server.py:891` (`_emit()`), `ui-tui/src/gatewayTypes.ts:611` (`GatewayEvent` union).
 */
sealed class GatewayEvent {

    /** Session ID the event belongs to. Null for connection-wide events. */
    abstract val sessionId: String?

    // ── Connection lifecycle ───────────────────────────────────────────────

    /** Emitted immediately after WebSocket connect. Payload: skin (optional). */
    data class GatewayReady(
        override val sessionId: String?,
        val skin: Map<String, String>?,
    ) : GatewayEvent()

    /** Server stderr line. */
    data class GatewayStderr(
        override val sessionId: String?,
        val line: String,
    ) : GatewayEvent()

    /** Gateway failed to start in time. */
    data class GatewayStartTimeout(
        override val sessionId: String?,
        val cwd: String?,
        val python: String?,
        val stderrTail: String?,
    ) : GatewayEvent()

    /** Protocol error. */
    data class GatewayProtocolError(
        override val sessionId: String?,
        val preview: String?,
    ) : GatewayEvent()

    // ── Session ───────────────────────────────────────────────────────────

    /** Session metadata update. Payload is a JSON object (SessionInfo). */
    data class SessionInfo(
        override val sessionId: String?,
        val info: Map<String, kotlinx.serialization.json.JsonElement>,
    ) : GatewayEvent()

    // ── Message streaming (the main chat flow) ────────────────────────────

    /** Assistant message started. */
    data class MessageStart(override val sessionId: String?) : GatewayEvent()

    /** Streaming text chunk. */
    data class MessageDelta(
        override val sessionId: String?,
        val text: String,
        val rendered: String?,
    ) : GatewayEvent()

    /** Assistant message finished. */
    data class MessageComplete(
        override val sessionId: String?,
        val text: String,
        val rendered: String?,
        val reasoning: String?,
        val usage: Map<String, Long>?,
    ) : GatewayEvent()

    /** Thinking text chunk (reasoning models). */
    data class ThinkingDelta(
        override val sessionId: String?,
        val text: String,
    ) : GatewayEvent()

    /** Reasoning text chunk. */
    data class ReasoningDelta(
        override val sessionId: String?,
        val text: String,
    ) : GatewayEvent()

    /** Reasoning available. */
    data class ReasoningAvailable(
        override val sessionId: String?,
        val text: String?,
    ) : GatewayEvent()

    /** Status line update. */
    data class StatusUpdate(
        override val sessionId: String?,
        val kind: String?,
        val text: String?,
    ) : GatewayEvent()

    // ── Tools ─────────────────────────────────────────────────────────────

    /**
     * One entry of the agent's task list, carried on tool.start/tool.complete.
     * Reference: `ui-tui/src/app/turnController.ts` (`parseTodos`).
     */
    data class TodoItem(
        val id: String,
        val content: String,
        val status: String, // "pending" | "in_progress" | "completed" | "cancelled"
    )

    /** Tool execution started. */
    data class ToolStart(
        override val sessionId: String?,
        val toolId: String,
        val name: String?,
        val argsText: String?,
        val context: String?,
        val todos: List<TodoItem>? = null,
    ) : GatewayEvent()

    /** Tool execution finished. */
    data class ToolComplete(
        override val sessionId: String?,
        val toolId: String,
        val name: String?,
        val result: String?,
        val resultText: String?,
        val summary: String?,
        val durationS: Double?,
        val inlineDiff: String?,
        val error: String? = null,
        val todos: List<TodoItem>? = null,
    ) : GatewayEvent()

    /** Tool is generating. */
    data class ToolGenerating(
        override val sessionId: String?,
        val name: String?,
    ) : GatewayEvent()

    /** Tool progress update. */
    data class ToolProgress(
        override val sessionId: String?,
        val name: String?,
        val preview: String?,
    ) : GatewayEvent()

    // ── Interactive requests (require user response) ──────────────────────

    /**
     * Tool needs approval. Step 7 — Android notification with Approve/Deny.
     *
     * [allowPermanent] mirrors upstream `allow_permanent`: when false the UI
     * must not offer the "always allow" choice (e.g. security-reviewed
     * commands are capped at session scope).
     */
    data class ApprovalRequest(
        override val sessionId: String?,
        val command: String,
        val description: String,
        val patternKeys: List<String>,
        val allowPermanent: Boolean = true,
    ) : GatewayEvent()

    /** Agent asks a clarifying question. */
    data class ClarifyRequest(
        override val sessionId: String?,
        val requestId: String,
        val question: String,
        val choices: List<String>?,
    ) : GatewayEvent()

    /** Sudo password needed. */
    data class SudoRequest(
        override val sessionId: String?,
        val requestId: String,
    ) : GatewayEvent()

    /** Env var secret needed. */
    data class SecretRequest(
        override val sessionId: String?,
        val requestId: String,
        val envVar: String,
        val prompt: String,
    ) : GatewayEvent()

    // ── Notifications ─────────────────────────────────────────────────────

    /** Show a notification. */
    data class NotificationShow(
        override val sessionId: String?,
        val key: String?,
        val kind: String?, // "sticky" | "ttl"
        val level: String?, // "error" | "info" | "success" | "warn"
        val text: String?,
        val ttlMs: Long?,
    ) : GatewayEvent()

    /** Clear a notification. */
    data class NotificationClear(
        override val sessionId: String?,
        val key: String?,
    ) : GatewayEvent()

    // ── Billing ───────────────────────────────────────────────────────────

    data class BillingStepUpVerification(
        override val sessionId: String?,
        val verificationUrl: String,
        val userCode: String?,
    ) : GatewayEvent()

    // ── Voice ─────────────────────────────────────────────────────────────

    data class VoiceStatus(
        override val sessionId: String?,
        val state: String?, // "idle" | "listening" | "transcribing"
    ) : GatewayEvent()

    data class VoiceTranscript(
        override val sessionId: String?,
        val text: String?,
        val noSpeechLimit: Boolean?,
    ) : GatewayEvent()

    // ── Subagents ─────────────────────────────────────────────────────────

    data class SubagentEvent(
        override val sessionId: String?,
        val subagentType: String, // spawn_requested | start | thinking | tool | progress | complete
        val payload: Map<String, kotlinx.serialization.json.JsonElement>,
    ) : GatewayEvent()

    // ── Background ────────────────────────────────────────────────────────

    data class BackgroundComplete(
        override val sessionId: String?,
        val taskId: String,
        val text: String,
    ) : GatewayEvent()

    data class ReviewSummary(
        override val sessionId: String?,
        val text: String?,
    ) : GatewayEvent()

    // ── Browser ───────────────────────────────────────────────────────────

    data class BrowserProgress(
        override val sessionId: String?,
        val level: String?, // "info" | "warn" | "error"
        val message: String?,
    ) : GatewayEvent()

    // ── Skin ──────────────────────────────────────────────────────────────

    data class SkinChanged(
        override val sessionId: String?,
        val skin: Map<String, String>?,
    ) : GatewayEvent()

    // ── Dashboard ─────────────────────────────────────────────────────────

    data class DashboardNewSessionRequested(
        override val sessionId: String?,
        val reason: String?,
    ) : GatewayEvent()

    // ── Error ─────────────────────────────────────────────────────────────

    data class Error(
        override val sessionId: String?,
        val message: String?,
    ) : GatewayEvent()

    // ── Catch-all for unknown event types ─────────────────────────────────

    /**
     * An event type we don't model yet. Keeps the client forward-compatible
     * with new server-side events without crashing.
     */
    data class Unknown(
        override val sessionId: String?,
        val eventType: String,
        val rawPayload: Map<String, kotlinx.serialization.json.JsonElement>,
    ) : GatewayEvent()
}

package com.hermes.android.ui.viewmodel

/**
 * Approvals the agents are blocked on, oldest first. The gateway streams every live chat
 * on one socket, so two chats can ask at once; a single slot let the second request
 * replace the first, and the first agent waited out its server timeout.
 *
 * Pure list functions so the order and the dedupe are unit-tested.
 */
internal object ApprovalQueue {

    /**
     * Appends [request] unless its id is already queued: `session.resume`'s `open_requests`
     * re-delivers a request the app already holds. A re-delivery that now knows the chat's
     * stored id fills it in.
     */
    fun add(queue: List<PendingApprovalUi>, request: PendingApprovalUi): List<PendingApprovalUi> {
        val i = queue.indexOfFirst { it.requestId == request.requestId }
        if (i < 0) return queue + request
        val held = queue[i]
        if (held.sessionKey != null || request.sessionKey == null) return queue
        return queue.toMutableList().also { it[i] = held.copy(sessionKey = request.sessionKey) }
    }

    fun remove(queue: List<PendingApprovalUi>, requestId: String): List<PendingApprovalUi> =
        queue.filterNot { it.requestId == requestId }

    /** `request.cancel` names the `srq-…` id, not the approval queue id. */
    fun removeByServerId(queue: List<PendingApprovalUi>, serverRequestId: String): List<PendingApprovalUi> =
        if (serverRequestId.isBlank()) queue else queue.filterNot { it.serverRequestId == serverRequestId }

    /**
     * Drops the queued requests of the chat being opened: its resume re-delivers the ones
     * still open, so one whose `request.cancel` was lost while offline doesn't stay forever.
     */
    fun withoutChat(queue: List<PendingApprovalUi>, sessionId: String?, sessionKey: String?): List<PendingApprovalUi> =
        queue.filterNot { belongsTo(it, sessionId, sessionKey) }

    /** What the sheet shows: the open chat's oldest request, else the oldest of any chat. */
    fun pick(queue: List<PendingApprovalUi>, activeSessionId: String?, activeSessionKey: String?): PendingApprovalUi? =
        queue.firstOrNull { it.sessionId == null || belongsTo(it, activeSessionId, activeSessionKey) }
            ?: queue.firstOrNull()

    fun isForOpenChat(request: PendingApprovalUi, activeSessionId: String?, activeSessionKey: String?): Boolean =
        request.sessionId == null || belongsTo(request, activeSessionId, activeSessionKey)

    /** Ids (live and stored) of every chat that waits on an approval, for the drawer pulse. */
    fun waitingIds(queue: List<PendingApprovalUi>): Set<String> =
        queue.flatMapTo(mutableSetOf()) { listOfNotNull(it.sessionId, it.sessionKey) }

    /**
     * The name of the chat [request] came from when it is not the open one: its title from
     * the drawer's [sessions], or "" when the chat isn't listed (the sheet then says
     * "another chat"). Null for the open chat's own request.
     */
    fun otherChatTitle(
        request: PendingApprovalUi,
        activeSessionId: String?,
        activeSessionKey: String?,
        sessions: List<SessionItem>,
    ): String? {
        if (isForOpenChat(request, activeSessionId, activeSessionKey)) return null
        return sessions.firstOrNull { it.id == request.sessionKey || it.id == request.sessionId }
            ?.title?.takeIf { it.isNotBlank() }
            .orEmpty()
    }

    private fun belongsTo(request: PendingApprovalUi, sessionId: String?, sessionKey: String?): Boolean =
        (sessionId != null && request.sessionId == sessionId) ||
            (sessionKey != null && request.sessionKey == sessionKey)
}

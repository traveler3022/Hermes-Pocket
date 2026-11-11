package com.hermes.android.ui.viewmodel

/**
 * What the approval card above the composer shows: the request to answer now, where it
 * stands among the waiting ones ("1 of N"), and the chat it came from when that is not
 * the open one (null: the open chat's own; "": a chat the drawer doesn't list).
 */
data class ApprovalCardModel(
    val request: PendingApprovalUi,
    val position: Int,
    val total: Int,
    val otherChatTitle: String?,
) {
    /** "1 of N" is shown only when more than one waits. */
    val showsPosition: Boolean get() = total > 1

    companion object {
        /** The card for [queue], or null when nothing waits. Picks as [ApprovalQueue.pick] does. */
        fun of(
            queue: List<PendingApprovalUi>,
            activeSessionId: String?,
            activeSessionKey: String?,
            sessions: List<SessionItem>,
        ): ApprovalCardModel? {
            val request = ApprovalQueue.pick(queue, activeSessionId, activeSessionKey) ?: return null
            return ApprovalCardModel(
                request = request,
                // The card always shows the one to answer first; the rest wait behind it.
                position = 1,
                total = queue.size,
                otherChatTitle = ApprovalQueue.otherChatTitle(request, activeSessionId, activeSessionKey, sessions),
            )
        }
    }
}

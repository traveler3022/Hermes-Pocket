package com.hermes.android.domain.notify

/** The notification that asks for a tool approval while the app is in the background. */
interface ApprovalAlerts {

    /** Shows the approval request [requestId]; "Always" is offered only when [allowPermanent]. */
    fun showApprovalRequest(
        requestId: String,
        sessionId: String?,
        toolName: String,
        command: String,
        description: String,
        allowPermanent: Boolean = true,
    )

    /** Takes the request's notification away (answered, or timed out). */
    fun cancelApproval(requestId: String)
}

/** Whether one of the app's screens is on the display. */
interface AppVisibility {
    val isForeground: Boolean
}

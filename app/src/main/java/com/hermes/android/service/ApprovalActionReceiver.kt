package com.hermes.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject

/**
 * Receives Approve/Deny button taps from tool approval notifications.
 *
 * Sends the response back to the gateway via `approval.respond` RPC.
 *
 * Fix S7F01: Corrected params to match Hermes source:
 *   {session_id, choice: "approve"/"deny", all: false}
 *   (was: {request_id, approved})
 *
 * Reference: ADR-003, tui_gateway/server.py:8943-8960
 */
@AndroidEntryPoint
class ApprovalActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var gatewayClient: GatewayClient

    @Inject
    lateinit var approvalNotificationManager: ApprovalNotificationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val approved = intent.getBooleanExtra(EXTRA_APPROVED, false)

        Timber.i("[Approval] User response: $approved for request=$requestId session=$sessionId")

        approvalNotificationManager.cancelApproval(requestId)

        scope.launch {
            try {
                val params = buildJsonObject {
                    if (sessionId != null) put("session_id", sessionId)
                    put("choice", if (approved) "approve" else "deny")
                    put("all", false)
                }
                gatewayClient.request(GatewayMethods.APPROVAL_RESPOND, params.toMap())
                Timber.i("[Approval] Response sent: choice=${if (approved) "approve" else "deny"}")
            } catch (e: Exception) {
                Timber.e(e, "[Approval] Failed to send response")
            }
        }
    }

    companion object {
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_APPROVED = "approved"
        const val ACTION_APPROVAL_RESPONSE = "com.hermes.android.APPROVAL_RESPONSE"

        fun createIntent(
            context: Context,
            requestId: String,
            sessionId: String?,
            approved: Boolean,
        ): Intent {
            return Intent(context, ApprovalActionReceiver::class.java).apply {
                action = ACTION_APPROVAL_RESPONSE
                putExtra(EXTRA_REQUEST_ID, requestId)
                if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_APPROVED, approved)
            }
        }
    }
}

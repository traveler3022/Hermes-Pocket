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
 * Receives Approve/Always/Deny button taps from tool approval notifications.
 *
 * Sends the response back to the gateway via `approval.respond` RPC with
 * params {session_id, choice, all}. Canonical choice values (see
 * `tools/approval.py` upstream — `_ApprovalEntry.result`):
 *   "once"    — allow this call only (no persistence)
 *   "session" — allow this pattern for the rest of the session
 *   "always"  — persist to the permanent allowlist
 *   "deny"    — block
 *
 * Reference: ADR-003, docs/upstream-reference/ (approval.respond handler)
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
        val choice = intent.getStringExtra(EXTRA_CHOICE) ?: CHOICE_DENY

        Timber.i("[Approval] User response: $choice for request=$requestId session=$sessionId")

        approvalNotificationManager.cancelApproval(requestId)

        scope.launch {
            try {
                val params = buildJsonObject {
                    if (sessionId != null) put("session_id", sessionId)
                    put("choice", choice)
                    put("all", false)
                }
                gatewayClient.request(GatewayMethods.APPROVAL_RESPOND, params.toMap())
                Timber.i("[Approval] Response sent: choice=$choice")
            } catch (e: Exception) {
                Timber.e(e, "[Approval] Failed to send response")
            }
        }
    }

    companion object {
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_CHOICE = "choice"
        const val ACTION_APPROVAL_RESPONSE = "com.hermes.android.APPROVAL_RESPONSE"

        const val CHOICE_ONCE = "once"
        const val CHOICE_ALWAYS = "always"
        const val CHOICE_DENY = "deny"

        fun createIntent(
            context: Context,
            requestId: String,
            sessionId: String?,
            choice: String,
        ): Intent {
            return Intent(context, ApprovalActionReceiver::class.java).apply {
                action = ACTION_APPROVAL_RESPONSE
                putExtra(EXTRA_REQUEST_ID, requestId)
                if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_CHOICE, choice)
            }
        }
    }
}

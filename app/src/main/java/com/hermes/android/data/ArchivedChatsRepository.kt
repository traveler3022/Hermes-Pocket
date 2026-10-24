package com.hermes.android.data

import com.hermes.android.domain.session.ArchivedChats
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `session.archive` sets the flag (gateway RPC, every runtime). session.list has no way to
 * ask for archived rows, so the list comes from the dashboard's `GET /api/sessions?archived=only`;
 * the built-in Linux serves that route in-process (android.dashboard).
 */
@Singleton
class ArchivedChatsRepository @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val dashboard: DashboardApi,
) : ArchivedChats {

    override suspend fun setArchived(sessionId: String, archived: Boolean) {
        gatewayClient.request(
            GatewayMethods.SESSION_ARCHIVE,
            mapOf("session_id" to JsonPrimitive(sessionId), "archived" to JsonPrimitive(archived)),
            // Not the reconnect target: archiving a chat does not open it.
            trackSession = false,
        )
    }

    // The route caps limit at 100.
    override suspend fun archived(): JsonElement =
        dashboard.call("GET", "/api/sessions?archived=only&order=recent&limit=100")
}

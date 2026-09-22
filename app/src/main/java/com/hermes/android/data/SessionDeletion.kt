package com.hermes.android.data

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/**
 * Deletes a stored chat. The server refuses (4023) while any live worker has that
 * transcript loaded — even an idle one left from opening it — so those are
 * interrupted and closed first.
 */
suspend fun GatewayClient.deleteStoredSession(storedId: String) {
    val rows = ((request(GatewayMethods.SESSION_ACTIVE_LIST) as? JsonObject)?.get("sessions") as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        .orEmpty()
    fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.content.orEmpty()
    val liveIds = rows
        .filter { it.str("session_key") == storedId || it.str("id") == storedId }
        .map { it.str("id") }
        .filter { it.isNotEmpty() }
    for (liveId in liveIds) {
        val params = mapOf("session_id" to JsonPrimitive(liveId))
        runCatching { request(GatewayMethods.SESSION_INTERRUPT, params, timeoutMs = 5_000) }
        runCatching { request(GatewayMethods.SESSION_CLOSE, params, timeoutMs = 5_000) }
            .onFailure { Timber.w(it, "[Sessions] closing live $liveId before delete failed") }
    }
    request(GatewayMethods.SESSION_DELETE, mapOf("session_id" to JsonPrimitive(storedId)))
}

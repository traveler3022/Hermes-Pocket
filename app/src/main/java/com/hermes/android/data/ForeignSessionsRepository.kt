package com.hermes.android.data

import com.hermes.android.domain.session.ForeignMessage
import com.hermes.android.domain.session.ForeignSession
import com.hermes.android.domain.session.ForeignSessionPage
import com.hermes.android.domain.session.ForeignSessionPreview
import com.hermes.android.domain.session.ForeignSessions
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `session.foreign.list|preview|import` (tui_gateway/methods_session_foreign.py, read by
 * hermes_cli/foreign_sessions_browser.py). Gateway RPC, so every runtime has it; the folders
 * read are those of the machine Hermes runs on.
 */
@Singleton
class ForeignSessionsRepository @Inject constructor(
    private val gatewayClient: GatewayClient,
) : ForeignSessions {

    override suspend fun list(source: String?, offset: Int): ForeignSessionPage {
        val params = buildMap<String, JsonElement> {
            source?.let { put("source", JsonPrimitive(it)) }
            put("offset", JsonPrimitive(offset))
            put("limit", JsonPrimitive(PAGE_SIZE))
        }
        val root = call(GatewayMethods.SESSION_FOREIGN_LIST, params)
        val rows = (root["sessions"] as? JsonArray).orEmpty().mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val id = row.text("id") ?: return@mapNotNull null
            ForeignSession(
                id = id,
                source = row.text("source").orEmpty(),
                label = row.text("label") ?: row.text("source").orEmpty(),
                title = row.text("title") ?: row.text("label").orEmpty(),
                cwd = row.text("cwd"),
                modifiedAt = row.number("mtime") ?: 0,
                turnCount = (row.number("turn_count") ?: 0).toInt(),
                excerpt = row.text("excerpt").orEmpty(),
            )
        }
        return ForeignSessionPage(
            sessions = rows,
            nextOffset = root.number("next_offset")?.toInt(),
            host = root.text("host").orEmpty(),
            unreadable = (root.number("unreadable") ?: 0).toInt(),
        )
    }

    override suspend fun preview(id: String): ForeignSessionPreview {
        val root = call(GatewayMethods.SESSION_FOREIGN_PREVIEW, mapOf("id" to JsonPrimitive(id)))
        return ForeignSessionPreview(
            messages = (root["messages"] as? JsonArray).orEmpty().mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                ForeignMessage(role = row.text("role") ?: "user", content = row.text("content") ?: return@mapNotNull null)
            },
            total = (root.number("total") ?: 0).toInt(),
            truncated = (root["truncated"] as? JsonPrimitive)?.booleanOrNull == true,
            importedSessionId = root.text("already_imported"),
            cwd = root.text("cwd"),
        )
    }

    override suspend fun import(id: String): String {
        val root = call(GatewayMethods.SESSION_FOREIGN_IMPORT, mapOf("id" to JsonPrimitive(id)))
        return root.text("session_id") ?: throw IllegalStateException("Hermes returned no session id")
    }

    private suspend fun call(method: String, params: Map<String, JsonElement>): JsonObject =
        // Not the reconnect target: browsing or importing opens no chat.
        gatewayClient.request(method, params, trackSession = false) as? JsonObject
            ?: throw IllegalStateException("$method: no result")

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): Long? =
        (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

    private companion object {
        /** The server allows 1–50. */
        const val PAGE_SIZE = 25
    }
}

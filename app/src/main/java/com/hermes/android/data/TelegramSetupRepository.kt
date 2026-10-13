package com.hermes.android.data

import com.hermes.android.domain.messaging.TelegramApplyResult
import com.hermes.android.domain.messaging.TelegramPairing
import com.hermes.android.domain.messaging.TelegramPairingStatus
import com.hermes.android.domain.messaging.TelegramSetup
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `/api/messaging/telegram/onboarding/…` of the dashboard, the flow Hy4ri/hermes-mobile's Telegram
 * setup uses: start → poll until the bot exists → apply with the allowed user ids; then the
 * messaging gateway restarts (`/api/gateway/restart` when apply didn't start it itself).
 */
@Singleton
class TelegramSetupRepository @Inject constructor(
    private val dashboard: DashboardApi,
    private val runtime: HermesRuntime,
) : TelegramSetup {

    override val available: Boolean get() = runtime.type != RuntimeType.PROOT_LINUX

    override suspend fun start(): TelegramPairing {
        val root = dashboard.call("POST", "$BASE/start", buildJsonObject { put("bot_name", "Hermes Agent") }) as? JsonObject
            ?: throw IOException("No answer from Hermes")
        val id = root.text("pairing_id") ?: throw IOException("Hermes sent no pairing id")
        return TelegramPairing(
            pairingId = id,
            suggestedUsername = root.text("suggested_username").orEmpty(),
            deepLink = root.text("deep_link") ?: root.text("qr_payload").orEmpty(),
            expiresAt = root.text("expires_at").orEmpty(),
        )
    }

    override suspend fun status(pairingId: String): TelegramPairingStatus {
        val root = dashboard.call("GET", "$BASE/${pairingId.urlPart()}") as? JsonObject
            ?: return TelegramPairingStatus.Waiting
        if (root.text("status") != "ready") return TelegramPairingStatus.Waiting
        return TelegramPairingStatus.Ready(
            botUsername = root.text("bot_username"),
            ownerUserId = root.text("owner_user_id")?.takeIf { it.all(Char::isDigit) },
        )
    }

    override suspend fun apply(pairingId: String, allowedUserIds: List<String>): TelegramApplyResult {
        val body = buildJsonObject { put("allowed_user_ids", JsonArray(allowedUserIds.map { JsonPrimitive(it) })) }
        val root = dashboard.call("POST", "$BASE/${pairingId.urlPart()}/apply", body) as? JsonObject
            ?: throw IOException("No answer from Hermes")
        if (!root.flag("needs_restart")) return TelegramApplyResult.Done
        if (root.flag("restart_started")) return TelegramApplyResult.Restarting
        return try {
            dashboard.call("POST", "/api/gateway/restart")
            TelegramApplyResult.Restarting
        } catch (e: IOException) {
            TelegramApplyResult.NeedsRestart(root.text("restart_error") ?: e.message)
        }
    }

    override suspend fun cancel(pairingId: String) {
        dashboard.call("DELETE", "$BASE/${pairingId.urlPart()}")
    }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.flag(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull == true
    private fun String.urlPart(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private companion object {
        const val BASE = "/api/messaging/telegram/onboarding"
    }
}

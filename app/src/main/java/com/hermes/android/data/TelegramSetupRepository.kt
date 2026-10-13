package com.hermes.android.data

import com.hermes.android.domain.messaging.TelegramSaveResult
import com.hermes.android.domain.messaging.TelegramSetup
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The dashboard's `PUT /api/messaging/platforms/telegram` (the server checks the token's shape
 * and the ids), then `POST /api/gateway/restart` unless a running gateway already took the token.
 * Nothing goes through a third-party setup bot.
 */
@Singleton
class TelegramSetupRepository @Inject constructor(
    private val dashboard: DashboardApi,
    private val runtime: HermesRuntime,
) : TelegramSetup {

    override val available: Boolean get() = runtime.type != RuntimeType.PROOT_LINUX

    override suspend fun save(botToken: String, allowedUserIds: List<String>): TelegramSaveResult {
        val body = buildJsonObject {
            put("enabled", true)
            put("env", buildJsonObject {
                put("TELEGRAM_BOT_TOKEN", botToken.trim())
                put("TELEGRAM_ALLOWED_USERS", allowedUserIds.joinToString(","))
            })
        }
        val root = dashboard.call("PUT", "/api/messaging/platforms/telegram", body) as? JsonObject
        if ((root?.get("hot_served") as? JsonPrimitive)?.booleanOrNull == true) return TelegramSaveResult.Live
        return try {
            dashboard.call("POST", "/api/gateway/restart")
            TelegramSaveResult.Restarting
        } catch (e: IOException) {
            TelegramSaveResult.NeedsRestart(e.message)
        }
    }
}

package com.hermes.android.data

import com.hermes.android.domain.config.EnvKey
import com.hermes.android.domain.config.EnvKeys
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `.env` keys through the dashboard's `/api/env` routes (the gateway has no RPC for them). After a
 * change the gateway re-reads `.env` (`reload.env`), so new chats use the new value.
 */
@Singleton
class EnvKeysRepository @Inject constructor(
    private val dashboard: DashboardApi,
    private val gateway: GatewayClient,
) : EnvKeys {

    override suspend fun list(): List<EnvKey> = parseEnvKeys(dashboard.call("GET", "/api/env"))

    override suspend fun reveal(key: String): String {
        val answer = dashboard.call("POST", "/api/env/reveal", buildJsonObject { put("key", key) }) as? JsonObject
        return (answer?.get("value") as? JsonPrimitive)?.contentOrNull ?: throw IOException("No value for $key")
    }

    override suspend fun save(key: String, value: String) {
        dashboard.call("PUT", "/api/env", buildJsonObject {
            put("key", key)
            put("value", value)
        })
        reloadGatewayEnv()
    }

    override suspend fun remove(key: String) {
        dashboard.call("DELETE", "/api/env", buildJsonObject { put("key", key) })
        reloadGatewayEnv()
    }

    private suspend fun reloadGatewayEnv() {
        try {
            gateway.request(GatewayMethods.RELOAD_ENV)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The file is saved either way; a restart picks it up.
            Timber.w(e, "[EnvKeys] reload.env failed")
        }
    }
}

/**
 * GET /api/env: `{KEY: {is_set, redacted_value, description, url, category, is_password,
 * channel_managed, custom, …}}`. Keys a Platforms card owns (`channel_managed`) are left out,
 * as the dashboard's Keys page does.
 */
internal fun parseEnvKeys(result: JsonElement): List<EnvKey> {
    val root = result as? JsonObject ?: return emptyList()
    fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
    fun JsonObject.flag(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull == true
    return root.mapNotNull { (key, element) ->
        val row = element as? JsonObject ?: return@mapNotNull null
        if (row.flag("channel_managed")) return@mapNotNull null
        EnvKey(
            key = key,
            description = row.text("description").orEmpty(),
            url = row.text("url"),
            category = row.text("category").orEmpty(),
            isSet = row.flag("is_set"),
            // The server wraps it as «redacted:sk-a...wxyz».
            preview = row.text("redacted_value")?.removePrefix("«redacted:")?.removeSuffix("»"),
            isSecret = row.flag("is_password"),
            custom = row.flag("custom"),
        )
    }
}

package com.hermes.android.data

import com.hermes.android.domain.messaging.Webhooks
import com.hermes.android.domain.messaging.Webhooks.Created
import com.hermes.android.domain.messaging.Webhooks.Draft
import com.hermes.android.domain.messaging.Webhooks.EnableResult
import com.hermes.android.domain.messaging.Webhooks.Route
import com.hermes.android.domain.messaging.Webhooks.WebhookList
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** `/api/webhooks…` of the dashboard (web_routers/ops.py), the same store `hermes webhook` uses. */
@Singleton
class WebhooksRepository @Inject constructor(
    private val dashboard: DashboardApi,
    private val runtime: HermesRuntime,
) : Webhooks {

    override val available: Boolean get() = runtime.type != RuntimeType.PROOT_LINUX

    override suspend fun list(): WebhookList {
        val o = dashboard.call("GET", "/api/webhooks") as? JsonObject ?: throw IOException("No answer from Hermes")
        return WebhookList(
            platformEnabled = o.bool("enabled") ?: false,
            baseUrl = o.str("base_url").orEmpty(),
            routes = (o["subscriptions"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toRoute() },
        )
    }

    override suspend fun enablePlatform(): EnableResult {
        val o = dashboard.call("POST", "/api/webhooks/enable") as? JsonObject
        return EnableResult(needsRestart = o?.bool("needs_restart") ?: false)
    }

    override suspend fun create(draft: Draft): Created {
        val o = dashboard.call("POST", "/api/webhooks", buildJsonObject {
            put("name", draft.name.trim())
            draft.description.trim().takeIf { it.isNotEmpty() }?.let { put("description", it) }
            putJsonArray("events") { draft.events.forEach { add(JsonPrimitive(it)) } }
            draft.prompt.trim().takeIf { it.isNotEmpty() }?.let { put("prompt", it) }
            put("deliver", draft.deliver.trim().ifEmpty { "log" })
            put("deliver_only", draft.deliverOnly)
            draft.deliverChatId.trim().takeIf { it.isNotEmpty() }?.let { put("deliver_chat_id", it) }
        }) as? JsonObject ?: throw IOException("No answer from Hermes")
        val route = o.toRoute() ?: throw IOException("No answer from Hermes")
        return Created(route = route, secret = o.str("secret").orEmpty())
    }

    override suspend fun delete(name: String) {
        dashboard.call("DELETE", "/api/webhooks/${encode(name)}")
    }

    override suspend fun setEnabled(name: String, enabled: Boolean) {
        dashboard.call("PUT", "/api/webhooks/${encode(name)}/enabled", buildJsonObject { put("enabled", enabled) })
    }

    private fun JsonObject.toRoute(): Route? {
        val name = str("name") ?: return null
        return Route(
            name = name,
            description = str("description").orEmpty(),
            events = (this["events"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) },
            deliver = str("deliver") ?: "log",
            deliverOnly = bool("deliver_only") ?: false,
            prompt = str("prompt").orEmpty(),
            url = str("url").orEmpty(),
            // The server treats a missing flag as on.
            enabled = bool("enabled") ?: true,
        )
    }

    private fun encode(name: String) = URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}

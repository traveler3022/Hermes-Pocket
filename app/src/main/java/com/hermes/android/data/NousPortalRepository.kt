package com.hermes.android.data

import com.hermes.android.domain.portal.Connector
import com.hermes.android.domain.portal.ConnectorAccount
import com.hermes.android.domain.portal.ConnectorList
import com.hermes.android.domain.portal.ConnectorOperation
import com.hermes.android.domain.portal.NousPortal
import com.hermes.android.domain.portal.PortalFeature
import com.hermes.android.domain.portal.PortalStatus
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NousPortal]: the status from the dashboard's `GET /api/portal` (hermes_cli/web_routers/status.py;
 * the built-in Linux serves the same router in-process), the connectors from the gateway's
 * `connectors.*` RPCs with the account as owner (tui_gateway/methods_connectors.py), which every
 * runtime has.
 */
@Singleton
class NousPortalRepository @Inject constructor(
    private val dashboard: DashboardApi,
    private val gatewayClient: GatewayClient,
) : NousPortal {

    override suspend fun status(): PortalStatus {
        val o = dashboard.call("GET", "/api/portal") as? JsonObject
            ?: throw IllegalStateException("Hermes sent no Portal status")
        return PortalStatus(
            loggedIn = o.bool("logged_in"),
            freeTier = o.bool("free_tier"),
            accountTier = o.text("account_tier"),
            provider = o.text("provider").orEmpty(),
            portalUrl = o.text("portal_url"),
            subscriptionUrl = o.text("subscription_url"),
            features = (o["features"] as? JsonArray).orEmpty().mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                PortalFeature(label = row.text("label") ?: return@mapNotNull null, state = row.text("state").orEmpty())
            },
        )
    }

    override suspend fun connectors(): ConnectorList {
        val root = call(GatewayMethods.CONNECTORS_LIST, mapOf("owner" to ACCOUNT_OWNER))
        if (!root.bool("available")) return ConnectorList(available = false, connectors = emptyList())
        // The catalog has the names; without it (signed out, Portal unreachable) the slugs still list.
        val catalog = runCatching { call(GatewayMethods.CONNECTORS_CATALOG, emptyMap()) }.getOrNull()
            ?.let { it["connectors"] as? JsonArray }.orEmpty()
            .mapNotNull { it as? JsonObject }
            .associateBy { it.text("slug").orEmpty() }
        val states = (root["connectors"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .associateBy { it.text("connector").orEmpty() }
        val slugs = (states.keys + catalog.keys).filter { it.isNotEmpty() }.distinct()
        return ConnectorList(
            available = true,
            connectors = slugs.map { slug ->
                val state = states[slug]
                val entry = catalog[slug]
                Connector(
                    slug = slug,
                    name = entry?.text("name") ?: slug,
                    description = entry?.text("description").orEmpty(),
                    category = entry?.text("category").orEmpty(),
                    // An app only in the catalog has no policy row yet: it can be connected.
                    enabled = state?.bool("enabled") ?: true,
                    connected = state?.bool("connected") ?: false,
                    status = state?.text("connection_status"),
                )
            }.sortedWith(compareByDescending<Connector> { it.connected }.thenBy { it.name.lowercase() }),
        )
    }

    override suspend fun accounts(): List<ConnectorAccount> {
        val root = call(GatewayMethods.CONNECTORS_ACCOUNTS, emptyMap())
        return (root["accounts"] as? JsonArray).orEmpty().mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            ConnectorAccount(
                connectionId = row.text("connection_id") ?: return@mapNotNull null,
                connector = row.text("connector").orEmpty(),
                label = row.text("label").orEmpty(),
                alias = row.text("alias"),
                status = row.text("status").orEmpty(),
                active = row.bool("active"),
            )
        }
    }

    override suspend fun connect(slug: String, reconnect: Boolean): ConnectorOperation {
        val params = mapOf(
            "owner" to ACCOUNT_OWNER,
            "connectors" to JsonArray(listOf(JsonPrimitive(slug))),
            "reconnect" to JsonPrimitive(reconnect),
        )
        return operationOf(call(GatewayMethods.CONNECTORS_CONNECT, params))
    }

    override suspend fun operation(opId: String): ConnectorOperation =
        operationOf(call(GatewayMethods.CONNECTORS_OPERATION_STATUS, mapOf("owner" to ACCOUNT_OWNER, "op_id" to JsonPrimitive(opId))))

    override suspend fun wake(opId: String) {
        call(GatewayMethods.CONNECTORS_OPERATION_WAKE, mapOf("owner" to ACCOUNT_OWNER, "op_id" to JsonPrimitive(opId)))
    }

    override suspend fun removeAccount(connectionId: String) {
        call(GatewayMethods.CONNECTORS_ACCOUNTS_REMOVE, mapOf("connection_id" to JsonPrimitive(connectionId)))
    }

    private fun operationOf(root: JsonObject): ConnectorOperation {
        val targets = (root["targets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        return ConnectorOperation(
            opId = root.text("op_id") ?: throw IllegalStateException("Hermes started no connection"),
            settled = root.bool("settled"),
            deadlineAt = (root["deadline_at"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
            connectUrl = targets.firstNotNullOfOrNull { it.text("connect_url") },
            failure = targets.firstOrNull { it.text("state") in FAILED_STATES }
                ?.let { it.text("detail") ?: it.text("state") },
        )
    }

    private suspend fun call(method: String, params: Map<String, JsonElement>): JsonObject =
        // Not the reconnect target: this page opens no chat.
        gatewayClient.request(method, params, trackSession = false) as? JsonObject
            ?: throw IllegalStateException("$method: no result")

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private companion object {
        /** The connectors of the profile's Portal account, not of one chat. */
        val ACCOUNT_OWNER = JsonObject(mapOf("type" to JsonPrimitive("account")))
        val FAILED_STATES = setOf("failed", "expired")
    }
}

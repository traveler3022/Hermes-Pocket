package com.hermes.android.data

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read + auto-reload preference only (`billing.state`, `billing.auto_reload`).
 *
 * `billing.charge` / `billing.charge_status` / `billing.step_up` move real
 * money and the step-up flow is a multi-minute out-of-band OAuth device-code
 * exchange (verification URL/code delivered via a side-channel event, per the
 * server's own docs on that RPC) — building that hastily risks a mishandled
 * idempotency key or a stuck charge. Deliberately not wired from mobile;
 * charging stays a desktop/CLI action for now.
 */
@Singleton
class BillingRepository @Inject constructor(
    private val gatewayClient: GatewayClient,
) {
    data class AutoReload(
        val enabled: Boolean,
        val thresholdDisplay: String,
        val reloadToDisplay: String,
    )

    data class BillingState(
        val loggedIn: Boolean,
        val orgName: String?,
        val balanceDisplay: String?,
        val canCharge: Boolean,
        val autoReload: AutoReload?,
    )

    suspend fun state(): BillingState {
        val result = gatewayClient.request(GatewayMethods.BILLING_STATE) as? JsonObject
            ?: return BillingState(false, null, null, false, null)
        val ar = result["auto_reload"] as? JsonObject
        return BillingState(
            loggedIn = result.bool("logged_in") ?: false,
            orgName = result.strOrNull("org_name"),
            balanceDisplay = result.strOrNull("balance_display"),
            canCharge = result.bool("can_charge") ?: false,
            autoReload = ar?.let {
                AutoReload(
                    enabled = it.bool("enabled") ?: false,
                    thresholdDisplay = it.str("threshold_display"),
                    reloadToDisplay = it.str("reload_to_display"),
                )
            },
        )
    }

    /** [threshold]/[reloadTo] are USD amounts as plain numbers (e.g. "10"). */
    suspend fun setAutoReload(enabled: Boolean, threshold: String, reloadTo: String): Boolean {
        val params = buildJsonObject {
            put("enabled", enabled)
            put("threshold", threshold)
            put("top_up_amount", reloadTo)
        }.toElementMap()
        val result = gatewayClient.request(GatewayMethods.BILLING_AUTO_RELOAD, params) as? JsonObject
        return result?.bool("ok") ?: false
    }

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.content ?: ""

    private fun JsonObject.toElementMap(): Map<String, JsonElement> =
        entries.associate { (k, v) -> k to v }

    // JSON null (Python None) decodes as JsonNull, not JsonPrimitive, so the
    // `as?` cast already yields null for it — just fold blank strings too.
    private fun JsonObject.strOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}

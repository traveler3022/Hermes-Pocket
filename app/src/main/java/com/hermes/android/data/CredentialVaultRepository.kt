package com.hermes.android.data

import com.hermes.android.domain.vault.CredentialVault
import com.hermes.android.domain.vault.VaultItem
import com.hermes.android.domain.vault.VaultKind
import com.hermes.android.domain.vault.VaultSource
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The gateway's `vault.*` RPCs (tui_gateway/methods_vault.py), the ones the desktop's
 * Settings › Credential Vault uses. ProfileScope adds the app's profile, so each profile
 * has its own vault. Gateway RPCs, so every runtime has them.
 */
@Singleton
class CredentialVaultRepository @Inject constructor(
    private val gatewayClient: GatewayClient,
) : CredentialVault {

    override suspend fun items(): List<VaultItem> = parseVaultItems(call(GatewayMethods.VAULT_LIST))

    override suspend fun sources(): List<VaultSource> = parseVaultSources(call(GatewayMethods.VAULT_SOURCES))

    override suspend fun setSourceEnabled(name: String, enabled: Boolean) {
        call(GatewayMethods.VAULT_SOURCE_SET, "name" to JsonPrimitive(name), "enabled" to JsonPrimitive(enabled))
    }

    override suspend fun unlock(name: String, masterPassword: String) {
        call(GatewayMethods.VAULT_UNLOCK, "name" to JsonPrimitive(name), "password" to JsonPrimitive(masterPassword))
    }

    override suspend fun lock(name: String) {
        call(GatewayMethods.VAULT_LOCK, "name" to JsonPrimitive(name))
    }

    override suspend fun add(kind: VaultKind, label: String, origin: String?, secret: Map<String, String>): String {
        val params = buildList {
            add("kind" to JsonPrimitive(kind.wire))
            add("label" to JsonPrimitive(label))
            if (!origin.isNullOrBlank()) add("origin" to JsonPrimitive(origin))
            add("secret" to JsonObject(secret.mapValues { JsonPrimitive(it.value) }))
        }
        val result = call(GatewayMethods.VAULT_ADD, *params.toTypedArray()) as? JsonObject
        return result.text("id").orEmpty()
    }

    override suspend fun remove(id: String) {
        call(GatewayMethods.VAULT_REMOVE, "id" to JsonPrimitive(id))
    }

    // Unlocking runs the manager CLI on the server, which can take a while.
    private suspend fun call(method: String, vararg params: Pair<String, JsonElement>): JsonElement =
        gatewayClient.request(method, params.toMap(), timeoutMs = 60_000, trackSession = false)
}

/** `{items: [{id, kind, label, origin, created_at, identifier, has_otp, backend}]}`. */
internal fun parseVaultItems(result: JsonElement): List<VaultItem> =
    ((result as? JsonObject)?.get("items") as? JsonArray).orEmpty().mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        VaultItem(
            id = row.text("id") ?: return@mapNotNull null,
            kind = row.text("kind").orEmpty(),
            label = row.text("label").orEmpty(),
            origin = row.text("origin")?.takeIf { it.isNotBlank() },
            createdAt = row.text("created_at").orEmpty(),
            identifier = row.text("identifier")?.takeIf { it.isNotBlank() },
            hasOtp = (row["has_otp"] as? JsonPrimitive)?.booleanOrNull == true,
            backend = row.text("backend") ?: "local",
        )
    }

/** `{sources: [{name, display_name, enabled, needs_unlock, unlocked, installed}]}`. */
internal fun parseVaultSources(result: JsonElement): List<VaultSource> =
    ((result as? JsonObject)?.get("sources") as? JsonArray).orEmpty().mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        fun flag(key: String) = (row[key] as? JsonPrimitive)?.booleanOrNull == true
        val name = row.text("name") ?: return@mapNotNull null
        VaultSource(
            name = name,
            displayName = row.text("display_name") ?: name,
            enabled = flag("enabled"),
            needsUnlock = flag("needs_unlock"),
            unlocked = flag("unlocked"),
            installed = flag("installed"),
        )
    }

private fun JsonObject?.text(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

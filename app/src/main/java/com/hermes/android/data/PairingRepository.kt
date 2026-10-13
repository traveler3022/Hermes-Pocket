package com.hermes.android.data

import com.hermes.android.domain.messaging.MessagingPairing
import com.hermes.android.domain.messaging.MessagingPairing.PairingList
import com.hermes.android.domain.messaging.MessagingPairing.Request
import com.hermes.android.domain.messaging.MessagingPairing.User
import com.hermes.android.domain.messaging.PairingResult
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The gateway has no pairing RPC and the dashboard no pairing route, so this runs
 * `hermes pairing list|approve|revoke|clear-pending` through `cli.exec` and reads its text.
 * [com.hermes.android.gateway.ProfileScope] adds the open profile, whose store the CLI uses.
 */
@Singleton
class PairingRepository @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val runtime: HermesRuntime,
) : MessagingPairing {

    override val available: Boolean get() = runtime.type != RuntimeType.PROOT_LINUX

    override suspend fun list(): PairingList = parsePairingList(cli("pairing", "list"))

    override suspend fun approve(platform: String, requestId: String): PairingResult =
        pairingOutcome(cli("pairing", "approve", platform, requestId), "Approved!")

    override suspend fun revoke(platform: String, userId: String): PairingResult =
        pairingOutcome(cli("pairing", "revoke", platform, userId), "Revoked access")

    override suspend fun clearPending(): PairingResult {
        cli("pairing", "clear-pending")
        return PairingResult.Done
    }

    private suspend fun cli(vararg argv: String): String {
        val result = gatewayClient.request(
            GatewayMethods.CLI_EXEC,
            mapOf(
                "argv" to JsonArray(argv.map { JsonPrimitive(it) }),
                "timeout" to JsonPrimitive(60),
            ),
            timeoutMs = 90_000,
            trackSession = false,
        ) as? JsonObject ?: throw IllegalStateException("cli.exec: no result")
        val output = result.str("output")
        if (result.str("blocked") == "true") throw IllegalStateException(result.str("hint"))
        if (result.str("code") != "0") {
            throw IllegalStateException(output.lines().lastOrNull { it.isNotBlank() }?.trim() ?: "hermes ${argv.joinToString(" ")} failed")
        }
        return output
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""
}

/** The CLI exits 0 either way; [successMark] in its output tells which. */
internal fun pairingOutcome(output: String, successMark: String): PairingResult =
    if (successMark in output) PairingResult.Done else PairingResult.Failed(lastBlock(output))

/** The CLI ends with its verdict as one indented block; text before it may be update notices. */
private fun lastBlock(output: String): String =
    output.trimEnd().split(Regex("\n\\s*\n")).last().lines().joinToString(" ") { it.trim() }.trim()

/**
 * Reads `hermes pairing list` (Hermes 0.21.7):
 * ```
 *   Pending Pairing Requests (1):
 *   Platform     Request ID         User ID              Name                 Age
 *   --------     ----------         -------              ----                 ---
 *   telegram     961b5263d6833d58   123456789            Ali Reza             1m ago
 *
 *   Approved Users (1):
 *   Platform     User ID              Name
 *   --------     -------              ----
 *   slack        U07AB
 * ```
 * Ids hold no spaces, so a row splits on whitespace; the name is what is left between them.
 */
internal fun parsePairingList(output: String): PairingList {
    val pending = mutableListOf<Request>()
    val approved = mutableListOf<User>()
    var section: String? = null
    for (raw in output.lines()) {
        val line = raw.trim()
        when {
            line.startsWith("Pending Pairing Requests") -> section = "pending"
            line.startsWith("Approved Users") -> section = "approved"
            line.isEmpty() -> section = null
            line.startsWith("Platform ") || line.startsWith("-") -> Unit
            section == "pending" -> {
                val t = line.split(Regex("\\s+"))
                if (t.size < 5 || t.last() != "ago") continue
                pending += Request(
                    platform = t[0],
                    requestId = t[1].takeUnless { it == "-" },
                    userId = t[2],
                    userName = t.subList(3, t.size - 2).joinToString(" "),
                    ageMinutes = t[t.size - 2].removeSuffix("m").toIntOrNull(),
                )
            }
            section == "approved" -> {
                val t = line.split(Regex("\\s+"))
                if (t.size < 2) continue
                approved += User(platform = t[0], userId = t[1], userName = t.drop(2).joinToString(" "))
            }
        }
    }
    return PairingList(pending, approved)
}

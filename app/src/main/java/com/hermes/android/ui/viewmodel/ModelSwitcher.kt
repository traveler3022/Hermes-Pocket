package com.hermes.android.ui.viewmodel

import com.hermes.android.data.SessionRepository
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import kotlinx.serialization.json.contentOrNull

/** Result of a `config.set model` call. */
sealed interface ModelSwitchOutcome {
    /** Switched; [deferred] = a turn was running, so it applies at the next turn start. */
    data class Applied(val deferred: Boolean) : ModelSwitchOutcome

    /** Nothing switched yet: the gateway wants [message] confirmed, then a re-send with `confirmed`. */
    data class NeedsConfirm(val message: String) : ModelSwitchOutcome

    data class Failed(val message: String) : ModelSwitchOutcome
}

/** Error text for callers without a confirm flow (provider setup); null when the switch went through. */
fun ModelSwitchOutcome.errorText(): String? = when (this) {
    is ModelSwitchOutcome.Applied -> null
    is ModelSwitchOutcome.NeedsConfirm -> "Model not switched — confirmation required: $message"
    is ModelSwitchOutcome.Failed -> message
}

/** A model pick awaiting the user's confirmation; re-sent with `confirmed = true`. */
data class ModelSwitchConfirm(
    val model: ModelOption,
    val liveSessionId: String?,
    val storedSessionId: String?,
    val message: String,
)

/** `model.options`: the catalogue plus the model/provider the gateway reports as current. */
data class ModelCatalog(
    val models: List<ModelOption>,
    val model: String?,
    val provider: String?,
)

/** Hermes model RPCs shared by Settings and the chat's model sheet. */
class ModelSwitcher @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val sessionRepository: SessionRepository,
) {

    /** [liveSessionId]: an open chat — the gateway then reports THAT session's model, else config.yaml's default. */
    suspend fun loadCatalog(liveSessionId: String? = null): ModelCatalog {
        val sid = liveSessionId?.takeIf { it.isNotBlank() }
        val result = gatewayClient.request(
            GatewayMethods.MODEL_OPTIONS,
            if (sid != null) mapOf("session_id" to JsonPrimitive(sid)) else emptyMap(),
        )
        val obj = result as? JsonObject
        fun text(key: String) = (obj?.get(key) as? JsonPrimitive)?.contentOrNull
        return ModelCatalog(parseModelOptions(result), text("model"), text("provider"))
    }

    /**
     * Switch model + provider the Hermes-native way, via `config.set` key="model".
     *
     * Earlier the app wrote `~/.hermes/config.yaml` directly (writeModelConfig).
     * That only affects the NEXT session — the live running agent keeps the old
     * model, which is why "switch provider" appeared to do nothing.
     *
     * The correct path is Hermes' own `config.set` handler with key="model".
     * Its `value` mirrors the `/model` command grammar parsed by
     * `parse_model_flags`:
     *   "<model> --provider <provider>[ --global]"
     *     • `--provider` pins the provider (else Hermes infers it from the model)
     *     • `--global` persists the choice to config.yaml so new sessions inherit it.
     *       Only sent without an open chat (Settings): a pick inside a chat is that
     *       chat's, same as Hermes Desktop (#90235) — otherwise every other open
     *       chat without its own override adopts it at its next turn.
     *
     * The response mirrors Hermes Desktop's handling: `confirm_required` means
     * nothing was switched until re-sent with `confirm_expensive_model`;
     * `deferred` means a turn was running and the pick applies at the next one.
     *
     * The server resolves `session_id` against LIVE ids only; without one it
     * answers 4001 "config.set model requires a live session". So we use the
     * open chat's live id when given (re-attaching its stored id if reclaimed),
     * else attach `session.most_recent`'s STORED id to obtain a live one.
     */
    suspend fun switch(
        provider: String,
        model: String,
        liveSessionId: String? = null,
        storedSessionId: String? = null,
        confirmed: Boolean = false,
    ): ModelSwitchOutcome {
        val live = liveSessionId?.takeIf { it.isNotBlank() }
        val value = buildString {
            append(model)
            if (provider.isNotBlank()) append(" --provider ").append(provider)
            if (live == null) append(" --global")
        }
        fun params(sid: String?) = buildJsonObject {
            put("key", "model")
            put("value", value)
            if (!sid.isNullOrBlank()) put("session_id", sid)
            if (confirmed) put("confirm_expensive_model", true)
        }.toMap()
        return try {
            val result = if (live != null) {
                sessionRepository.onLiveSession(live, storedSessionId, onRebound = {}) { sid ->
                    gatewayClient.request(GatewayMethods.CONFIG_SET, params(sid))
                }
            } else {
                val recent = mostRecentLiveId()
                if (recent != null) {
                    gatewayClient.request(GatewayMethods.CONFIG_SET, params(recent))
                } else {
                    // No chat yet (fresh install): the server refuses a sessionless model
                    // set with 4001, so lend it a scratch session for the --global write.
                    withScratchSession { sid -> gatewayClient.request(GatewayMethods.CONFIG_SET, params(sid)) }
                }
            }
            val obj = result as? JsonObject
            fun flag(key: String) = (obj?.get(key) as? JsonPrimitive)?.contentOrNull == "true"
            fun text(key: String) = (obj?.get(key) as? JsonPrimitive)
                ?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
            if (flag("confirm_required")) {
                ModelSwitchOutcome.NeedsConfirm(
                    text("confirm_message") ?: text("warning") ?: "This model needs confirmation before switching.",
                )
            } else {
                ModelSwitchOutcome.Applied(deferred = flag("deferred"))
            }
        } catch (e: GatewayException) {
            // 4009 = session busy (mid-turn) on older gateways; current ones
            // answer `deferred` instead.
            val m = e.message.orEmpty()
            ModelSwitchOutcome.Failed(
                if (m.contains("busy") || m.contains("4009")) {
                    "Session is busy — interrupt the current turn before switching models."
                } else {
                    "Failed to switch model: $m"
                },
            )
        }
    }

    private suspend fun <T> withScratchSession(block: suspend (String) -> T): T {
        val created = gatewayClient.request(GatewayMethods.SESSION_CREATE) as? JsonObject
        val sid = (created?.get("session_id") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw GatewayException("session.create returned no session_id")
        return try {
            block(sid)
        } finally {
            runCatching {
                gatewayClient.request(GatewayMethods.SESSION_CLOSE, mapOf("session_id" to JsonPrimitive(sid)), timeoutMs = 5_000)
            }
        }
    }

    /** Live id for `session.most_recent`'s stored id; null when there is none (JsonNull ≠ "null"). */
    private suspend fun mostRecentLiveId(): String? = try {
        val mr = gatewayClient.request(GatewayMethods.SESSION_MOST_RECENT)
        ((mr as? JsonObject)?.get("session_id") as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?.let { sessionRepository.attach(it).liveId }
    } catch (e: Exception) {
        Timber.w(e, "[Models] could not attach most recent session for model switch")
        null
    }
}

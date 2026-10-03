package com.hermes.android.gateway

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which Hermes profile the app is talking to.
 *
 * The gateway has no "active profile" of its own: every RPC may carry a `profile`
 * param, and the server runs that call against the named profile's home (config,
 * skills, memory, sessions). A session remembers the profile it was opened in, so
 * session-bound calls follow it (`tui_gateway/server.py`, the profile-scope wrapper).
 *
 * So switching profile here only changes what new, unbound calls carry. A call on a
 * session the app already opened carries that session's own profile, which keeps a
 * background chat from another profile working after a switch.
 *
 * `null` is the profile the gateway was started with; it is never sent.
 */
@Singleton
class ProfileScope @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("profile_scope", Context.MODE_PRIVATE)

    private val _active = MutableStateFlow(prefs.getString(KEY_ACTIVE, null)?.ifBlank { null })
    val active: StateFlow<String?> = _active.asStateFlow()

    /** Session id (live or stored) → the profile it was opened in ("" = launch profile). */
    private val owners = ConcurrentHashMap<String, String>()

    /** Methods this backend refused a `profile` on, learned at run time (older backends). */
    private val refused = ConcurrentHashMap.newKeySet<String>()

    fun select(name: String?) {
        val value = name?.trim()?.ifEmpty { null }
        prefs.edit().putString(KEY_ACTIVE, value).apply()
        _active.value = value
    }

    /** The profile [params] should run in, or null to send them as they are. */
    internal fun profileFor(method: String, params: Map<String, JsonElement>): String? {
        if (method in NO_PROFILE || method in refused || "profile" in params) return null
        val sid = (params["session_id"] as? JsonPrimitive)?.contentOrNull
        val owner = sid?.let { owners[it] }
        if (owner != null) return owner.ifEmpty { null }
        return _active.value
    }

    /** Remembers which profile the sessions named in [result] belong to. */
    internal fun remember(profile: String?, result: JsonElement) {
        val obj = result as? JsonObject ?: return
        for (key in SESSION_KEYS) {
            val id = (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
            owners[id] = profile.orEmpty()
        }
    }

    /** The backend has no profile named [name] (another server, or it was deleted). */
    internal fun forget(name: String) {
        if (_active.value == name) select(null)
        owners.entries.removeIf { it.value == name }
    }

    internal fun refuse(method: String) {
        refused += method
    }

    private companion object {
        const val KEY_ACTIVE = "active"
        val SESSION_KEYS = listOf("session_id", "stored_session_id", "session_key")

        /** Methods whose params have no `profile` field: the server rejects it with 4000. */
        val NO_PROFILE = setOf(
            "browser.controller.detach", "browser.controller.heartbeat", "browser.controller.register",
            "browser.controller.result", "client.capabilities", "complete.slash", "diagnostics.share_nous",
            "gateway.capabilities", "image.generate", "learning.delete", "learning.detail", "learning.edit",
            "learning.frames", "model.disconnect", "model.save_key", "onboarding.ensure_setup_profile",
            "onboarding.reset_setup_profile", "paste.collapse", "ping", "plugins.list", "reload.env",
            "reload.mcp", "skills.reload", "tools.list", "tools.show", "toolsets.list",
        )
    }
}

/** True when [e] says the named profile does not exist on this backend (`rpc_dispatch.py`). */
internal fun GatewayException.isUnknownProfile(): Boolean = code == UNKNOWN_PROFILE_CODE

/** True when the backend refused the `profile` param itself (an older backend). */
internal fun GatewayException.refusedProfileParam(): Boolean =
    code == INVALID_PARAMS_CODE && (rpcMessage ?: message).orEmpty().contains("profile")

private const val UNKNOWN_PROFILE_CODE = 4064
private const val INVALID_PARAMS_CODE = 4000

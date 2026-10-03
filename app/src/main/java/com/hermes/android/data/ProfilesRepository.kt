package com.hermes.android.data

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.gateway.ProfileScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hermes profiles: separate agents on one gateway, each with its own model, SOUL,
 * skills, memory and sessions (`tui_gateway/methods_profiles.py`).
 */
@Singleton
class ProfilesRepository @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val scope: ProfileScope,
) {
    data class Profile(
        val name: String,
        val displayName: String,
        val description: String,
        val model: String,
        val isDefault: Boolean,
    ) {
        /** The default profile is the app's own agent until the user names it. */
        val title: String get() = displayName.ifBlank { if (isDefault) "Hermes" else name }
    }

    /** The selected profile's name; null is the profile the gateway was started with. */
    val active: StateFlow<String?> get() = scope.active

    /**
     * Every profile the user can talk to. Rows with a backend `role` (the setup
     * profile the onboarding flow uses) are internal and left out.
     */
    suspend fun list(): List<Profile> {
        val result = gatewayClient.request(
            GatewayMethods.PROFILES_LIST,
            mapOf("include_sessions" to JsonPrimitive(false)),
            trackSession = false,
        ) as? JsonObject ?: return emptyList()
        val rows = (result["profiles"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val profiles = rows.filter { it.str("role").isEmpty() }.map {
            Profile(
                name = it.str("name"),
                displayName = it.str("display_name"),
                description = it.str("description"),
                model = it.str("model"),
                isDefault = it.str("is_default") == "true",
            )
        }.filter { it.name.isNotEmpty() }
        // A selection this server does not have (another server, or deleted
        // elsewhere) would make every call fail: drop it.
        scope.active.value?.let { selected ->
            if (profiles.isNotEmpty() && profiles.none { it.name == selected }) scope.select(null)
        }
        return profiles.sortedWith(compareByDescending<Profile> { it.isDefault }.thenBy { it.title.lowercase() })
    }

    /**
     * Creates profile [name]. With [copyFrom] the new profile starts with that one's
     * config (model, toolsets) and skills; without it, a fresh config and the bundled
     * skills. Either way the server copies the launch profile's provider keys, so the
     * new agent can answer at once (`mirror_credentials`, on by default).
     */
    suspend fun create(name: String, description: String, copyFrom: String?) {
        val params = buildMap<String, JsonElement> {
            put("name", JsonPrimitive(name))
            if (description.isNotBlank()) put("description", JsonPrimitive(description.trim()))
            if (copyFrom != null) put("clone_from", JsonPrimitive(copyFrom))
        }
        gatewayClient.request(GatewayMethods.PROFILES_CREATE, params, trackSession = false)
    }

    /** Talk to [name] from now on; null goes back to the gateway's own profile. */
    fun select(name: String?) = scope.select(name)

    companion object {
        /** `hermes_cli/profiles.py::_PROFILE_ID_RE`: the id is also a folder and a command name. */
        private val NAME = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

        fun isValidName(name: String): Boolean = NAME.matches(name)
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""
}

package com.hermes.android.data

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.gateway.ProfileScope
import kotlinx.coroutines.flow.SharedFlow
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

    /** Fires when another profile is picked or the open one is gone; not on a rename. */
    val switches: SharedFlow<String?> get() = scope.switches

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

    /** What the editor shows: everything here is the profile's own, not the selection's. */
    data class Details(
        val name: String,
        val description: String,
        val soul: String,
        val model: String,
        val provider: String,
    )

    suspend fun describe(name: String): Details {
        val result = gatewayClient.request(
            GatewayMethods.PROFILES_DESCRIBE,
            mapOf("name" to JsonPrimitive(name)),
            trackSession = false,
        ) as? JsonObject ?: throw IllegalStateException("profiles.describe: no result")
        val model = result["model"] as? JsonObject
        return Details(
            name = result.str("name").ifEmpty { name },
            description = result.str("description"),
            soul = result.str("soul"),
            model = model?.str("default").orEmpty(),
            provider = model?.str("provider").orEmpty(),
        )
    }

    /** Writes [description] and [soul] (SOUL.md, the agent's persona) of profile [name]. */
    suspend fun save(name: String, description: String, soul: String) {
        val result = gatewayClient.request(
            GatewayMethods.PROFILES_CONFIGURE,
            mapOf(
                "name" to JsonPrimitive(name),
                "description" to JsonPrimitive(description),
                "soul" to JsonPrimitive(soul),
            ),
            trackSession = false,
        ) as? JsonObject
        val applied = result?.get("applied") as? JsonObject
        val failed = applied?.filterValues { (it as? JsonPrimitive)?.contentOrNull == "false" }?.keys.orEmpty()
        if (failed.isNotEmpty()) throw IllegalStateException("Not saved: ${failed.joinToString()}")
    }

    /**
     * Renames [old] to [new]. For the default profile only its shown name changes (its id
     * stays `default`). The gateway has no RPC for this; the CLI does it
     * (`hermes profile rename`), so it runs through `cli.exec` on the gateway's host —
     * the same way in every runtime.
     */
    suspend fun rename(old: String, new: String) {
        cli("profile", "rename", old, new)
        if (old != "default") scope.renamed(old, new)
    }

    /**
     * Deletes profile [name] with its config, skills, memory and chats. The open profile
     * is left first, so nothing is still talking to it while its folder goes.
     */
    suspend fun delete(name: String) {
        if (scope.active.value == name) scope.select(null)
        cli("profile", "delete", name, "--yes")
        scope.forget(name)
    }

    private suspend fun cli(vararg argv: String) {
        val result = gatewayClient.request(
            GatewayMethods.CLI_EXEC,
            mapOf(
                "argv" to JsonArray(argv.map { JsonPrimitive(it) }),
                // Run from the gateway's own profile, never from inside the one being
                // renamed or deleted (the CLI finds every profile from the Hermes root).
                "profile" to JsonPrimitive(""),
                "timeout" to JsonPrimitive(60),
            ),
            timeoutMs = 90_000,
            trackSession = false,
        ) as? JsonObject ?: throw IllegalStateException("cli.exec: no result")
        val output = result.str("output").trim()
        if (result.str("blocked") == "true") throw IllegalStateException(result.str("hint"))
        if (result.str("code") != "0") {
            throw IllegalStateException(output.lines().lastOrNull { it.isNotBlank() } ?: "hermes ${argv.joinToString(" ")} failed")
        }
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

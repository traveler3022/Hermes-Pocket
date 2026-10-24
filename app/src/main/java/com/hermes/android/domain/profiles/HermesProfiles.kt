package com.hermes.android.domain.profiles

import android.net.Uri
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hermes profiles: separate agents on one gateway, each with its own model, SOUL,
 * skills, memory and sessions.
 */
interface HermesProfiles {

    /** The selected profile's name; null is the profile the gateway was started with. */
    val active: StateFlow<String?>

    /** Fires when another profile is picked or the open one is gone; not on a rename. */
    val switches: SharedFlow<String?>

    /** Every profile the user can talk to (internal ones left out), default first. */
    suspend fun list(): List<Profile>

    /** Creates profile [name], from [copyFrom]'s config when given; [copyKeys] false skips the provider keys. */
    suspend fun create(name: String, description: String, copyFrom: String?, copyKeys: Boolean = true)

    suspend fun describe(name: String): Details

    suspend fun save(name: String, description: String, soul: String)

    /** Writes only SOUL.md of profile [name]; its description stays as it is. */
    suspend fun saveSoul(name: String, soul: String)

    suspend fun rename(old: String, new: String)

    suspend fun delete(name: String)

    /** Talk to [name] from now on; null goes back to the gateway's own profile. */
    fun select(name: String?)

    /** Profile [name]'s avatar (PNG, JPEG or WebP bytes); null when it has none. */
    suspend fun avatar(name: String): ByteArray?

    /** Sets profile [name]'s avatar from the picked [image]; null removes it, back to the first letter. */
    suspend fun setAvatar(name: String, image: Uri?)

    data class Profile(
        val name: String,
        val displayName: String,
        val description: String,
        val model: String,
        val isDefault: Boolean,
        /** The user set an image; until then the avatar is the title's first letter. */
        val hasAvatar: Boolean = false,
    ) {
        /** The default profile is the app's own agent until the user names it. */
        val title: String get() = displayName.ifBlank { if (isDefault) "Hermes" else name }
    }

    /** What the editor shows: everything here is the profile's own, not the selection's. */
    data class Details(
        val name: String,
        val description: String,
        val soul: String,
        val model: String,
        val provider: String,
    )

    companion object {
        /** `hermes_cli/profiles.py::_PROFILE_ID_RE`: the id is also a folder and a command name. */
        private val NAME = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

        fun isValidName(name: String): Boolean = NAME.matches(name)
    }
}

package com.hermes.android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.hermes.android.domain.profiles.HermesProfiles
import com.hermes.android.domain.profiles.HermesProfiles.Profile
import com.hermes.android.domain.profiles.HermesProfiles.Details
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.gateway.ProfileScope
import com.hermes.android.gateway.hermesPythonCommand
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hermes profiles: separate agents on one gateway, each with its own model, SOUL,
 * skills, memory and sessions (`tui_gateway/methods_profiles.py`).
 */
@Singleton
class ProfilesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gatewayClient: GatewayClient,
    private val scope: ProfileScope,
) : HermesProfiles {
    /** The selected profile's name; null is the profile the gateway was started with. */
    override val active: StateFlow<String?> get() = scope.active

    /** Fires when another profile is picked or the open one is gone; not on a rename. */
    override val switches: SharedFlow<String?> get() = scope.switches

    /**
     * Every profile the user can talk to. Rows with a backend `role` (the setup
     * profile the onboarding flow uses) are internal and left out.
     */
    override suspend fun list(): List<Profile> {
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
                hasAvatar = it.str("has_avatar") == "true",
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
     * new agent can answer at once (`mirror_credentials`, on by default). [copyKeys] false
     * turns that copy off; a [copyFrom] copy still brings that profile's `.env` keys,
     * because the server copies `.env` with the settings.
     */
    override suspend fun create(name: String, description: String, copyFrom: String?, copyKeys: Boolean) {
        val params = buildMap<String, JsonElement> {
            put("name", JsonPrimitive(name))
            if (description.isNotBlank()) put("description", JsonPrimitive(description.trim()))
            if (copyFrom != null) put("clone_from", JsonPrimitive(copyFrom))
            if (!copyKeys) put("mirror_credentials", JsonPrimitive(false))
        }
        val result = gatewayClient.request(GatewayMethods.PROFILES_CREATE, params, trackSession = false) as? JsonObject
        val path = result?.str("path").orEmpty()
        if (copyFrom == null && copyKeys && path.isNotEmpty()) {
            try {
                copyCustomProviders(path)
            } catch (e: Exception) {
                Timber.w(e, "[Profiles] custom providers not copied to %s", name)
            }
        }
    }

    /**
     * The server's key copy brings the launch model (`model.provider: custom:<name>`) and the
     * `.env`/`auth.json` keys, but only a `providers:` gateway travels with it
     * (`launch_model_seed`). A custom endpoint added in this app lives in the
     * `custom_providers:` list, so the new profile answered "Unknown provider 'custom:<name>'".
     * Copies that list's entries the new profile [profileDir] does not have yet.
     */
    private suspend fun copyCustomProviders(profileDir: String) {
        val script = """
            import base64, os, pathlib
            try:
                from ruamel.yaml import YAML
                y = YAML()
                y.width = 2**31 - 1
                load = lambda p: y.load(p.read_text(encoding='utf-8'))
                dump = lambda d, f: y.dump(d, f)
            except ImportError:
                import yaml
                load = lambda p: yaml.safe_load(p.read_text(encoding='utf-8'))
                dump = lambda d, f: yaml.safe_dump(d, f, sort_keys=False, allow_unicode=True)
            dst_dir = pathlib.Path(base64.b64decode('${b64(profileDir)}').decode())
            src = dst_dir.parent.parent / 'config.yaml'
            dst = dst_dir / 'config.yaml'
            s = (load(src) if src.is_file() else None) or {}
            custom = [c for c in (s.get('custom_providers') or []) if isinstance(c, dict) and c.get('name')]
            if custom:
                d = (load(dst) if dst.is_file() else None) or {}
                have = d.get('custom_providers')
                if not isinstance(have, list):
                    have = []
                names = {c.get('name') for c in have if isinstance(c, dict)}
                have.extend(c for c in custom if c.get('name') not in names)
                d['custom_providers'] = have
                tmp = dst.with_name(dst.name + '.tmp')
                with tmp.open('w', encoding='utf-8') as f:
                    dump(d, f)
                os.chmod(tmp, 0o600)
                os.replace(tmp, dst)
        """.trimIndent()
        val result = gatewayClient.request(
            GatewayMethods.SHELL_EXEC,
            mapOf("command" to JsonPrimitive(hermesPythonCommand(script))),
            trackSession = false,
        ) as? JsonObject
        val code = result?.str("code")?.toIntOrNull() ?: -1
        if (code != 0) throw IllegalStateException(result?.str("stderr")?.lines()?.lastOrNull { it.isNotBlank() } ?: "exit $code")
    }

    private fun b64(s: String): String = java.util.Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    override suspend fun describe(name: String): Details {
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
    override suspend fun save(name: String, description: String, soul: String) {
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

    override suspend fun saveSoul(name: String, soul: String) {
        val result = gatewayClient.request(
            GatewayMethods.PROFILES_CONFIGURE,
            mapOf("name" to JsonPrimitive(name), "soul" to JsonPrimitive(soul)),
            trackSession = false,
        ) as? JsonObject
        val applied = (result?.get("applied") as? JsonObject)?.get("soul") as? JsonPrimitive
        if (applied?.contentOrNull != "true") throw IllegalStateException("Not saved: soul")
    }

    /**
     * Renames [old] to [new]. For the default profile only its shown name changes (its id
     * stays `default`). The gateway has no RPC for this; the CLI does it
     * (`hermes profile rename`), so it runs through `cli.exec` on the gateway's host —
     * the same way in every runtime.
     */
    override suspend fun rename(old: String, new: String) {
        cli("profile", "rename", old, new)
        if (old != "default") scope.renamed(old, new)
    }

    /**
     * Deletes profile [name] with its config, skills, memory and chats. The open profile
     * is left first, so nothing is still talking to it while its folder goes.
     */
    override suspend fun delete(name: String) {
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
    override fun select(name: String?) = scope.select(name)

    /** `profiles.get_asset`: `{found, mime, data: "data:<mime>;base64,..."}`; no image is `found: false`. */
    override suspend fun avatar(name: String): ByteArray? {
        val result = gatewayClient.request(
            GatewayMethods.PROFILES_GET_ASSET,
            mapOf("name" to JsonPrimitive(name), "asset" to JsonPrimitive("avatar")),
            trackSession = false,
        ) as? JsonObject ?: return null
        if (result.str("found") != "true") return null
        val data = result.str("data").substringAfter("base64,", "")
        if (data.isEmpty()) return null
        return runCatching { java.util.Base64.getDecoder().decode(data) }.getOrNull()
    }

    /**
     * `profiles.set_asset` takes PNG/JPEG/WebP up to 2 MB, so the picked image goes as a JPEG at
     * most [AVATAR_SIDE] px on its long side (ImageDecoder also turns it upright).
     */
    override suspend fun setAvatar(name: String, image: Uri?) {
        val params = buildMap<String, JsonElement> {
            put("name", JsonPrimitive(name))
            put("asset", JsonPrimitive("avatar"))
            if (image == null) {
                put("clear", JsonPrimitive(true))
            } else {
                val jpeg = withContext(Dispatchers.IO) { avatarJpeg(image) }
                put("data", JsonPrimitive("data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(jpeg)))
            }
        }
        gatewayClient.request(GatewayMethods.PROFILES_SET_ASSET, params, trackSession = false)
    }

    private fun avatarJpeg(image: Uri): ByteArray {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, image)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > AVATAR_SIDE) {
                val scale = AVATAR_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
        try {
            val out = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)) throw IllegalStateException("Could not read this image")
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    private companion object {
        const val AVATAR_SIDE = 512
    }
}

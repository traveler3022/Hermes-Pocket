package com.hermes.android.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pet gallery — adopt/rename/remove/disable a companion sprite
 * (pet.gallery/select/remove/rename/disable/scale). The full animated
 * spritesheet renderer (pet.info/pet.cells + client-side frame-geometry
 * state machine) and the AI-generation pipeline (pet.hatch/generate/cancel)
 * are out of scope here — this is adopt-and-manage, not the animation engine.
 *
 * Thumbnails come back as base64 PNG data URIs (`pet.thumb`), not a fetchable
 * URL (server-side CSP/hotlink notes rule that out) — decoded to [Bitmap]
 * here since Coil 2.x has no built-in `data:` URI fetcher but accepts Bitmap
 * models directly.
 */
@Singleton
class PetRepository @Inject constructor(
    private val gatewayClient: GatewayClient,
) {
    data class PetEntry(
        val slug: String,
        val displayName: String,
        val installed: Boolean,
        val thumbUrl: String,
    )

    data class Gallery(
        val enabled: Boolean,
        val activeSlug: String,
        val pets: List<PetEntry>,
    )

    suspend fun gallery(localOnly: Boolean = false): Gallery {
        val result = gatewayClient.request(
            GatewayMethods.PET_GALLERY,
            buildJsonObject { put("localOnly", localOnly) }.toElementMap(),
        ) as? JsonObject ?: return Gallery(false, "", emptyList())
        val rows = result["pets"] as? JsonArray ?: emptyList<JsonElement>()
        return Gallery(
            enabled = result.bool("enabled") ?: false,
            activeSlug = result.str("active"),
            pets = rows.mapNotNull { it as? JsonObject }.map {
                PetEntry(
                    slug = it.str("slug"),
                    displayName = it.str("displayName").ifBlank { it.str("slug") },
                    installed = it.bool("installed") ?: false,
                    thumbUrl = it.str("spritesheetUrl"),
                )
            },
        )
    }

    /** Decoded idle-frame thumbnail for one pet, or null (fail-open per server contract). */
    suspend fun thumbnail(slug: String, sourceUrl: String = ""): Bitmap? {
        val result = gatewayClient.request(
            GatewayMethods.PET_THUMB,
            buildJsonObject {
                put("slug", slug)
                if (sourceUrl.isNotBlank()) put("url", sourceUrl)
            }.toElementMap(),
        ) as? JsonObject ?: return null
        if (result.bool("ok") != true) return null
        val dataUri = result.str("dataUri")
        val b64 = dataUri.substringAfter(",", "")
        if (b64.isEmpty()) return null
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /** Adopt (install if needed) + activate. */
    suspend fun select(slug: String): Boolean {
        val result = gatewayClient.request(
            GatewayMethods.PET_SELECT,
            buildJsonObject { put("slug", slug) }.toElementMap(),
        ) as? JsonObject
        return result?.bool("ok") ?: false
    }

    suspend fun remove(slug: String): Boolean {
        val result = gatewayClient.request(
            GatewayMethods.PET_REMOVE,
            buildJsonObject { put("slug", slug) }.toElementMap(),
        ) as? JsonObject
        return result?.bool("ok") ?: false
    }

    suspend fun rename(slug: String, name: String): String? {
        val result = gatewayClient.request(
            GatewayMethods.PET_RENAME,
            buildJsonObject {
                put("slug", slug)
                put("name", name)
            }.toElementMap(),
        ) as? JsonObject ?: return null
        return if (result.bool("ok") == true) result.str("slug") else null
    }

    suspend fun disable(): Boolean {
        val result = gatewayClient.request(GatewayMethods.PET_DISABLE) as? JsonObject
        return result?.bool("ok") ?: false
    }

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.content ?: ""

    private fun JsonObject.toElementMap(): Map<String, JsonElement> =
        entries.associate { (k, v) -> k to v }
}

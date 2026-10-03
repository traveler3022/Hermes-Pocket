package com.hermes.android.runtime.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** What one browser sign-in to the server at [serverUrl] produced. */
data class RemoteTokens(
    /** The server's HTTP base, as [remoteHttpBase] spells it. */
    val serverUrl: String,
    val accessToken: String,
    val refreshToken: String,
    /** Epoch seconds the access token stops working; 0 when the server did not say. */
    val expiresAt: Long,
    val provider: String,
    val userId: String,
) {
    override fun toString() =
        "RemoteTokens(serverUrl=$serverUrl, userId=$userId, provider=$provider, expiresAt=$expiresAt, tokens=<redacted>)"
}

/**
 * Keeps the sign-in tokens encrypted at rest: AES-GCM under a key that lives in the
 * AndroidKeyStore and never leaves it, so the prefs file on its own is useless.
 */
@Singleton
class RemoteTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): RemoteTokens? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return runCatching {
            val sealed = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            val o = Json.parseToJsonElement(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES).decodeToString()).jsonObject
            RemoteTokens(
                serverUrl = o.str("server_url"),
                accessToken = o.str("access_token"),
                refreshToken = o.str("refresh_token"),
                expiresAt = (o["expires_at"] as? JsonPrimitive)?.longOrNull ?: 0L,
                provider = o.str("provider"),
                userId = o.str("user_id"),
            )
        }.onFailure {
            // A key wiped by a restore or a lock-screen reset: the tokens are gone either way.
            Timber.w(it, "[RemoteAuth] Stored sign-in unreadable; dropping it")
            prefs.edit().remove(KEY_BLOB).apply()
        }.getOrNull()
    }

    @Synchronized
    fun save(tokens: RemoteTokens) {
        val text = buildJsonObject {
            put("server_url", tokens.serverUrl)
            put("access_token", tokens.accessToken)
            put("refresh_token", tokens.refreshToken)
            put("expires_at", tokens.expiresAt)
            put("provider", tokens.provider)
            put("user_id", tokens.userId)
        }.toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(text.encodeToByteArray())
        prefs.edit().putString(KEY_BLOB, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_BLOB).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty()

    private companion object {
        const val PREFS_NAME = "hermes_remote_auth"
        const val KEY_BLOB = "tokens"
        const val KEY_ALIAS = "hermes_remote_tokens"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

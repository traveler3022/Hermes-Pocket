package com.hermes.android.runtime.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A server the app connected to once; tapping it connects again without typing its address. */
data class SavedServer(
    /** The address as it was used to connect. */
    val url: String,
    /** The Tailscale device name, or the host. */
    val name: String,
    /** Epoch millis of the last connection. */
    val lastUsed: Long,
)

/**
 * Servers the app has connected to, newest first. A server is added by itself once a connection
 * through it works, so someone who connected through Tailscale (and never saw the address) can
 * still pick it again. The login of each one is kept apart in [RemoteTokenStore].
 */
@Singleton
class SavedServers @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _servers = MutableStateFlow(read())
    val servers: StateFlow<List<SavedServer>> = _servers.asStateFlow()

    /** Adds [url] or moves it to the top; a blank [name] keeps the one it had. */
    @Synchronized
    fun remember(url: String, name: String) {
        val key = keyOf(url) ?: return
        val old = _servers.value.firstOrNull { keyOf(it.url) == key }
        val entry = SavedServer(
            url = url.trim(),
            name = name.ifBlank { old?.name.orEmpty() }.ifBlank { hostOf(url) },
            lastUsed = System.currentTimeMillis(),
        )
        write(listOf(entry) + _servers.value.filter { keyOf(it.url) != key })
    }

    @Synchronized
    fun forget(url: String) {
        val key = keyOf(url) ?: return
        write(_servers.value.filter { keyOf(it.url) != key })
    }

    private fun write(list: List<SavedServer>) {
        val text = buildJsonArray {
            list.forEach { s ->
                add(buildJsonObject {
                    put("url", s.url)
                    put("name", s.name)
                    put("last_used", s.lastUsed)
                })
            }
        }.toString()
        prefs.edit().putString(KEY_LIST, text).apply()
        _servers.value = list
    }

    private fun read(): List<SavedServer> {
        val text = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            (Json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val url = o.str("url").ifBlank { return@mapNotNull null }
                SavedServer(
                    url = url,
                    name = o.str("name").ifBlank { hostOf(url) },
                    lastUsed = (o["last_used"] as? JsonPrimitive)?.longOrNull ?: 0L,
                )
            }
        }.onFailure { Timber.w(it, "[SavedServers] List unreadable; starting empty") }.getOrDefault(emptyList())
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private companion object {
        const val PREFS_NAME = "hermes_saved_servers"
        const val KEY_LIST = "servers"
    }
}

/** Two spellings of one server ("host", "https://host/") are the same entry. */
private fun keyOf(url: String): String? = remoteHttpBase(url)

private fun hostOf(url: String): String =
    url.trim().substringAfter("://").substringBefore('/').substringBefore(':').ifBlank { url.trim() }

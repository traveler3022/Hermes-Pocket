package com.hermes.android.data

import com.hermes.android.domain.topics.ChatTopic
import com.hermes.android.domain.topics.ChatTopics
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The topics, kept in the profile's `ui_meta` under [TopicMeta.KEY].
 *
 * Read: `profiles.list` carries each profile's `ui_meta` and `ui_meta_revisions`
 * (`methods_profiles.py::_profile_ui_meta_fields`). Write: `profiles.configure` with
 * `ui_meta` and `ui_meta_expected_revisions`, a per-key compare-and-swap; a lost race
 * answers `applied.ui_meta_conflicts` and nothing is written (`_configure_ui_meta`).
 *
 * The read-modify-write loop follows Kyllene's ThreadsApi.change and UiMetaWrite.kt:
 * Copyright (c) 2026 Giton22, MIT License.
 */
@Singleton
class TopicsRepository @Inject constructor(
    private val gatewayClient: GatewayClient,
) : ChatTopics {

    private val _topics = MutableStateFlow<List<ChatTopic>>(emptyList())
    override val topics: StateFlow<List<ChatTopic>> = _topics.asStateFlow()

    /** The profile [_topics] belongs to ("" = the default profile), so a read for another never shows here. */
    @Volatile
    private var shownFor: String? = null

    /** Reads and writes in turn, so a read that began before a save can't put the older list back. */
    private val lock = Mutex()

    override suspend fun refresh(profile: String?) {
        val key = profile.orEmpty()
        if (shownFor != key) {
            shownFor = key
            _topics.value = emptyList()
        }
        lock.withLock {
            val read = try {
                TopicMeta.parse(stored(profile).block)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Topics] read failed")
                return
            }
            if (shownFor == key) _topics.value = read
        }
    }

    override suspend fun change(profile: String?, edit: (List<ChatTopic>) -> List<ChatTopic>): List<ChatTopic> {
        val key = profile.orEmpty()
        val saved = lock.withLock {
            var result: List<ChatTopic>? = null
            for (attempt in 1..WRITE_ATTEMPTS) {
                val stored = stored(profile)
                val before = TopicMeta.parse(stored.block)
                val after = edit(before)
                if (after == before) {
                    result = before
                    break
                }
                if (write(stored.name, TopicMeta.write(after, stored.block), stored.revision)) {
                    result = after
                    break
                }
            }
            result ?: throw IllegalStateException("The topics changed on another device at the same time. Try again.")
        }
        if (shownFor == key) _topics.value = saved
        return saved
    }

    /** What `profiles.list` has for [profile]: its own name, the topics block and that key's revision. */
    private suspend fun stored(profile: String?): Stored {
        val result = gatewayClient.request(
            GatewayMethods.PROFILES_LIST,
            mapOf("include_sessions" to JsonPrimitive(false)),
            trackSession = false,
        ) as? JsonObject ?: throw IllegalStateException("profiles.list gave no answer")
        val rows = (result["profiles"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val row = rows.firstOrNull { row ->
            if (profile == null) row.flag("is_default") else row.text("name") == profile
        } ?: throw IllegalStateException("No profile named ${profile ?: "default"}")
        val name = row.text("name") ?: throw IllegalStateException("A profile with no name")
        // Present on every gateway that keeps revisions; without it the write goes unguarded.
        val revision = (row["ui_meta_revisions"] as? JsonObject)?.let { revisions ->
            (revisions[TopicMeta.KEY] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: 0
        }
        val block = (row["ui_meta"] as? JsonObject)?.get(TopicMeta.KEY) as? JsonObject
        return Stored(name, block, revision)
    }

    /** Saves [block] under the topics key; false when another write got there first. */
    private suspend fun write(name: String, block: JsonObject, revision: Int?): Boolean {
        val params = buildJsonObject {
            put("name", name)
            put("ui_meta", buildJsonObject { put(TopicMeta.KEY, block) })
            revision?.let { put("ui_meta_expected_revisions", buildJsonObject { put(TopicMeta.KEY, it) }) }
        }
        val reply = gatewayClient.request(
            GatewayMethods.PROFILES_CONFIGURE,
            params.entries.associate { (k, v) -> k to v },
            trackSession = false,
        ) as? JsonObject
        val applied = reply?.get("applied") as? JsonObject
        if (applied?.get("ui_meta_conflicts").let { it != null && it !is kotlinx.serialization.json.JsonNull }) return false
        if ((applied?.get("ui_meta") as? JsonPrimitive)?.booleanOrNull != true) {
            throw IllegalStateException("The gateway didn't save the topics.")
        }
        return true
    }

    private class Stored(val name: String, val block: JsonObject?, val revision: Int?)

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private companion object {
        /** How many times a write that keeps losing races is read and tried again. */
        const val WRITE_ATTEMPTS = 4
    }
}

/**
 * The topics as `ui_meta` keeps them: `{"topics": [{"id", "name", "sessionId", "cwd"}]}`.
 * Other keys in the block are kept as they are.
 */
object TopicMeta {
    const val KEY = "hermes-pocket-topics"

    /** The topics stored in [block]; entries missing a field, or repeating an id, are skipped. */
    fun parse(block: JsonElement?): List<ChatTopic> {
        val entries = ((block as? JsonObject)?.get("topics") as? JsonArray).orEmpty()
        return entries.mapNotNull { entry ->
            val o = entry as? JsonObject ?: return@mapNotNull null
            val id = o.field("id") ?: return@mapNotNull null
            val name = o.field("name") ?: return@mapNotNull null
            val sessionId = o.field("sessionId") ?: return@mapNotNull null
            ChatTopic(id, name, sessionId, o.field("cwd"))
        }.distinctBy { it.id }
    }

    /** [topics] written into [block], whose other keys are kept. */
    fun write(topics: List<ChatTopic>, block: JsonObject? = null): JsonObject {
        val list = JsonArray(
            topics.map { topic ->
                buildJsonObject {
                    put("id", topic.id)
                    put("name", topic.name)
                    put("sessionId", topic.sessionId)
                    topic.cwd?.let { put("cwd", it) }
                }
            },
        )
        return JsonObject(block.orEmpty() + ("topics" to list))
    }

    private fun JsonObject.field(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}

package com.hermes.android.domain.topics

import kotlinx.coroutines.flow.StateFlow

/**
 * Topics: permanent, named places for one ongoing conversation each, like a group's
 * forum topics in a messaging app. A topic points at its current chat ([ChatTopic.sessionId]);
 * starting it fresh files that chat away in the archive and points the topic at a new one,
 * so the topic itself keeps its place in the drawer.
 *
 * The list lives on the server, in the profile's `ui_meta` (`profiles.list` reads it,
 * `profiles.configure` writes it with a per-key revision), so every device on the same
 * gateway and profile sees the same topics in the same order. `profiles.*` are gateway
 * RPCs, so Remote, Termux and Built-in Linux all keep them.
 *
 * The idea and the stored shape follow Kyllene's threads (core/threads/Threads.kt,
 * ThreadsApi.kt): Copyright (c) 2026 Giton22, MIT License.
 */
interface ChatTopics {

    /** The topics of the profile last read, in their order; empty until read. */
    val topics: StateFlow<List<ChatTopic>>

    /**
     * Reads the topics of [profile] (null: the gateway's default profile) again. A failed
     * read keeps what shows. Another profile's topics are dropped at once.
     */
    suspend fun refresh(profile: String?)

    /**
     * Applies [edit] to [profile]'s topics and saves them. The list is read fresh and written
     * with the revision it was read at, so an edit another device made meanwhile is kept: a
     * lost race reads again and applies [edit] to that. Answers the list as saved.
     */
    suspend fun change(profile: String?, edit: (List<ChatTopic>) -> List<ChatTopic>): List<ChatTopic>
}

/** A topic: its [name], the chat it points at now, and the folder its new chats start in. */
data class ChatTopic(
    val id: String,
    val name: String,
    val sessionId: String,
    val cwd: String? = null,
)

/** A new topic id: random, short, and safe in JSON and logs. */
fun newTopicId(random: kotlin.random.Random = kotlin.random.Random.Default): String =
    (1..12).map { TOPIC_ID_CHARS[random.nextInt(TOPIC_ID_CHARS.length)] }.joinToString("")

private const val TOPIC_ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

/** [topic] added at the end. A topic already pointing at the same chat is not added twice. */
fun List<ChatTopic>.withTopic(topic: ChatTopic): List<ChatTopic> =
    if (any { it.id == topic.id || it.sessionId == topic.sessionId }) this else this + topic

/** The topic [id] taken out; its chat is left alone. */
fun List<ChatTopic>.withoutTopic(id: String): List<ChatTopic> = filterNot { it.id == id }

/** The topic [id] named [name]; a blank name changes nothing. */
fun List<ChatTopic>.renamed(id: String, name: String): List<ChatTopic> {
    val clean = name.trim().takeIf { it.isNotEmpty() } ?: return this
    return map { if (it.id == id) it.copy(name = clean) else it }
}

/** The topic [id] pointed at the chat [sessionId]. */
fun List<ChatTopic>.pointedAt(id: String, sessionId: String): List<ChatTopic> =
    map { if (it.id == id) it.copy(sessionId = sessionId) else it }

/**
 * The topic [id] moved to where the topic [other] is: one place up or down when [other] is
 * its neighbour. An unknown id changes nothing.
 */
fun List<ChatTopic>.movedPast(id: String, other: String): List<ChatTopic> {
    val from = indexOfFirst { it.id == id }
    val to = indexOfFirst { it.id == other }
    if (from < 0 || to < 0) return this
    val rest = toMutableList()
    val topic = rest.removeAt(from)
    rest.add(to.coerceIn(0, rest.size), topic)
    return rest
}

package com.hermes.android.ui.viewmodel

import com.hermes.android.data.SessionRepository
import com.hermes.android.data.TaskRegistry
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tapback semantics, the reactions history carries, and finding a live reply's stored row. */
class MessageReactionsTest {

    private val heart = MessageReaction("\u2764\uFE0F", "user")
    private val laugh = MessageReaction("\uD83D\uDE02", "user")
    private val agentThumbs = MessageReaction("\uD83D\uDC4D", "agent")

    @Test
    fun `a reaction is added, switched, and retracted by the same emoji`() {
        val set = applyReaction(emptyList(), heart.emoji)
        assertEquals(listOf(heart), set)
        assertEquals(listOf(laugh), applyReaction(set, laugh.emoji))
        assertEquals(emptyList<MessageReaction>(), applyReaction(set, heart.emoji))
    }

    @Test
    fun `clearing the user's reaction leaves the agent's`() {
        assertEquals(listOf(agentThumbs), applyReaction(listOf(agentThumbs, heart), null))
        assertEquals(listOf(agentThumbs, laugh), applyReaction(listOf(agentThumbs, heart), laugh.emoji))
    }

    @Test
    fun `display metadata is read as an object or as JSON text, skipping broken entries`() {
        val list = buildJsonArray {
            add(buildJsonObject { put("emoji", heart.emoji); put("author", "user"); put("at", 1.5) })
            add(buildJsonObject { put("author", "agent") })
            add(JsonPrimitive("junk"))
            add(buildJsonObject { put("emoji", agentThumbs.emoji); put("author", "agent"); put("seen", true) })
        }
        val meta = buildJsonObject { put("reactions", list) }
        assertEquals(listOf(heart, agentThumbs), reactionsOf(meta))
        assertEquals(listOf(heart, agentThumbs), reactionsOf(JsonPrimitive(meta.toString())))
        assertEquals(emptyList<MessageReaction>(), reactionsOf(null))
        assertEquals(emptyList<MessageReaction>(), reactionsOf(JsonPrimitive("not json")))
    }

    private fun reply(id: String, text: String, rowId: Long? = null) =
        ChatMessage.Assistant(id = id, timestamp = 0, text = text, isStreaming = false, reasoning = null, rowId = rowId)

    @Test
    fun `a live reply is found by its text, repeats counted from the end`() {
        val local = listOf(reply("a", "Done."), reply("b", "Here it is"), reply("c", "Done."))
        val server = listOf(reply("x", "Done.", 11), reply("y", "Here it is", 12), reply("z", "Done.", 13))
        assertEquals(11L, findAssistantRow(local, local[0], server))
        assertEquals(12L, findAssistantRow(local, local[1], server))
        assertEquals(13L, findAssistantRow(local, local[2], server))
    }

    @Test
    fun `a reply the server does not hold is not guessed`() {
        val local = listOf(reply("a", "Only on screen"))
        assertNull(findAssistantRow(local, local[0], listOf(reply("x", "Something else", 11))))
        // A known row id wins, but only while the server still has that row.
        assertEquals(11L, findAssistantRow(local, reply("a", "x", 11), listOf(reply("x", "Something else", 11))))
        assertNull(findAssistantRow(local, reply("a", "x", 99), listOf(reply("x", "Something else", 11))))
    }

    private class FakeGatewayClient(val result: JsonElement) : GatewayClient {
        override val connectionState: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected(null))
        override val events: SharedFlow<GatewayEvent> = MutableSharedFlow()

        override suspend fun connect(url: String, connectTimeoutMs: Long): ConnectionState = connectionState.value
        override suspend fun disconnect() = Unit
        override suspend fun request(
            method: String,
            params: Map<String, JsonElement>,
            timeoutMs: Long,
            trackSession: Boolean,
        ): JsonElement = result
        override suspend fun notify(method: String, params: Map<String, JsonElement>) = Unit
        override suspend fun downloadFile(url: String): ByteArray = ByteArray(0)
    }

    private object NoTasks : TaskRegistry {
        override fun register(liveId: String, storedKey: String?) = Unit
        override fun isTask(liveId: String, sessionKey: String): Boolean = false
    }

    @Test
    fun `history gives both sides their reactions and replies their row ids`() = runTest {
        val history = buildJsonObject {
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user"); put("content", "hi"); put("row_id", 101)
                    put("display_metadata", buildJsonObject {
                        put("reactions", buildJsonArray {
                            add(buildJsonObject { put("emoji", agentThumbs.emoji); put("author", "agent") })
                        })
                    })
                })
                add(buildJsonObject {
                    put("role", "assistant"); put("content", "hello"); put("row_id", 102)
                    put("display_metadata", """{"reactions":[{"emoji":"${heart.emoji}","author":"user"}]}""")
                })
            })
        }
        val gateway = FakeGatewayClient(history)
        val sessions = ChatSessionDelegate(gateway, SessionRepository(gateway, NoTasks), this) {}

        val turns = sessions.serverTurns("s1")!!

        val user = turns[0] as ChatMessage.User
        val assistant = turns[1] as ChatMessage.Assistant
        assertEquals(listOf(agentThumbs), user.reactions)
        assertEquals(102L, assistant.rowId)
        assertEquals(listOf(heart), assistant.reactions)
    }
}

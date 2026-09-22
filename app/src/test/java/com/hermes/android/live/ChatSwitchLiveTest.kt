package com.hermes.android.live

import android.content.Context
import com.hermes.android.data.SessionRepository
import com.hermes.android.data.TaskRegistry
import com.hermes.android.gateway.OkHttpGatewayClient
import com.hermes.android.gateway.StdioGatewayHub
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.service.AppForegroundState
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ChatUiState
import com.hermes.android.ui.viewmodel.ChatViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The real ChatViewModel against a real Hermes gateway, driven the way the user does:
 * send in one chat, open a new chat while it is still answering, send there. The new
 * chat must show its reply streaming, not only once the turn is over.
 * Runs only when HERMES_LIVE_URL is set.
 */
@OptIn(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
class ChatSwitchLiveTest {

    private val url: String? = System.getenv("HERMES_LIVE_URL")
    private val main = newSingleThreadContext("vm-main")
    private lateinit var gateway: OkHttpGatewayClient
    private lateinit var vm: ChatViewModel
    private val failures = java.util.Collections.synchronizedList(mutableListOf<String>())

    private object NoTasks : TaskRegistry {
        override fun register(liveId: String, storedKey: String?) = Unit
        override fun isTask(liveId: String, sessionKey: String): Boolean = false
    }

    @Before
    fun setUp() {
        Assume.assumeTrue("HERMES_LIVE_URL not set", !url.isNullOrBlank())
        Dispatchers.setMain(main)
        val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val context = mockk<Context>(relaxed = true)
        gateway = OkHttpGatewayClient(http, json, StdioGatewayHub(), context)
        val runtime = mockk<HermesRuntime>(relaxed = true) { every { getWebSocketUrl() } returns url!! }
        vm = ChatViewModel(
            gateway, SessionRepository(gateway, NoTasks), runtime,
            mockk(relaxed = true), AppForegroundState(), context,
        )
    }

    @After
    fun tearDown() {
        runBlocking { runCatching { gateway.disconnect() } }
        Dispatchers.resetMain()
        main.close()
    }

    private suspend fun awaitState(timeoutMs: Long, what: String, test: (ChatUiState) -> Boolean): ChatUiState =
        try {
            withTimeout(timeoutMs) { vm.uiState.first(test) }
        } catch (e: Exception) {
            throw AssertionError("timed out waiting for: $what\nstate: ${describe(vm.uiState.value)}", e)
        }

    private fun describe(s: ChatUiState) = "active=${s.activeSessionId} sending=${s.isSending} messages=" +
        s.messages.joinToString { m ->
            when (m) {
                is ChatMessage.User -> "User(${m.text})"
                is ChatMessage.Assistant -> "Assistant(streaming=${m.isStreaming}, ${m.text.take(30)})"
                else -> m::class.simpleName.orEmpty()
            }
        }

    /** Same model as the phone when HERMES_LIVE_MODEL is set (e.g. atria/atria-dawn-preview). */
    private suspend fun useLiveModel(sessionId: String) {
        val model = System.getenv("HERMES_LIVE_MODEL")?.takeIf { it.isNotBlank() } ?: return
        gateway.request(
            com.hermes.android.gateway.GatewayMethods.CONFIG_SET,
            mapOf(
                "key" to kotlinx.serialization.json.JsonPrimitive("model"),
                "value" to kotlinx.serialization.json.JsonPrimitive(model),
                "session_id" to kotlinx.serialization.json.JsonPrimitive(sessionId),
            ),
        )
    }

    private fun send(text: String) = runBlocking(main) {
        vm.updateInputText(text)
        vm.sendMessage()
    }

    @Test
    fun `a new chat opened while another is answering shows its own reply streaming`() = runBlocking {
        awaitState(120_000, "connected with a chat open") {
            it.connectionState == ChatConnectionState.Connected && it.activeSessionId != null
        }
        val first = vm.uiState.value.activeSessionId
        useLiveModel(first!!)

        send("Count slowly from 1 to 40, one number per line.")
        awaitState(60_000, "first chat busy") { it.isSending }

        runBlocking(main) { vm.newConversation() }
        val second = awaitState(30_000, "new chat created") {
            it.activeSessionId != null && it.activeSessionId != first
        }.activeSessionId
        useLiveModel(second!!)

        send("Say hello in one short sentence.")
        // The bubble must be there while the turn runs, not only after message.complete.
        val streaming = awaitState(90_000, "a streaming assistant bubble in the new chat") { s ->
            s.activeSessionId == second && s.isSending &&
                s.messages.any { it is ChatMessage.Assistant && it.isStreaming }
        }
        // What the chat screen keeps on the surface: an assistant message missing from
        // the turn-work map is folded away, i.e. not drawn at all.
        for (fold in listOf(true, false)) {
            val shown = com.hermes.android.ui.screen.buildTurnWork(streaming.messages, fold).keys
            val bubble = streaming.messages.last { it is ChatMessage.Assistant && it.isStreaming }
            assertTrue("streaming bubble not rendered (foldNarration=$fold): ${describe(streaming)}", bubble.id in shown)
        }
        // Keep checking while it streams: the same must hold at every state change.
        val watch = kotlinx.coroutines.GlobalScope.launch {
            vm.uiState.collect { s ->
                if (s.activeSessionId == second && s.isSending) {
                    val bubble = s.messages.lastOrNull { it is ChatMessage.Assistant && it.isStreaming }
                    val shown = com.hermes.android.ui.screen.buildTurnWork(s.messages, true).keys
                    if (bubble == null || bubble.id !in shown) failures += describe(s)
                }
            }
        }
        val done = awaitState(180_000, "the new chat's reply finished") { s ->
            s.activeSessionId == second && !s.isSending &&
                s.messages.any { it is ChatMessage.Assistant && !it.isStreaming && it.text.isNotBlank() }
        }
        watch.cancel()
        assertTrue("reply vanished mid-turn:\n" + failures.take(3).joinToString("\n"), failures.isEmpty())
        assertTrue(describe(done), done.messages.filterIsInstance<ChatMessage.User>().map { it.text } ==
            listOf("Say hello in one short sentence."))
    }

    @Test
    fun `going back to a chat that is still answering shows the answer as it streams`() = runBlocking {
        awaitState(120_000, "connected with a chat open") {
            it.connectionState == ChatConnectionState.Connected && it.activeSessionId != null
        }
        // An older chat to switch to (B).
        useLiveModel(vm.uiState.value.activeSessionId!!)
        send("Reply with the single word: ready")
        val b = awaitState(120_000, "chat B answered") { s ->
            !s.isSending && s.messages.any { it is ChatMessage.Assistant && !it.isStreaming && it.text.isNotBlank() }
        }
        val bKey = b.activeSessionKey ?: b.activeSessionId!!

        // Chat A: a long answer.
        runBlocking(main) { vm.newConversation() }
        val aLive = awaitState(30_000, "chat A created") { it.activeSessionId != null && it.activeSessionId != b.activeSessionId }
        useLiveModel(aLive.activeSessionId!!)
        send("Write the numbers from 1 to 120, each on its own line, with a short word after each.")
        awaitState(60_000, "chat A busy") { it.isSending }
        val aKey = awaitState(30_000, "chat A stored id") { it.activeSessionKey != null }.activeSessionKey!!

        // Straight to B, send there.
        runBlocking(main) { vm.resumeSession(bKey) }
        awaitState(30_000, "back in chat B") { it.activeSessionKey == bKey && !it.isSending }
        send("Reply with the single word: again")

        // Back to A while it is still answering: the answer must be on screen and growing.
        runBlocking(main) { vm.resumeSession(aKey) }
        val back = awaitState(30_000, "back in chat A, still busy") { it.activeSessionKey == aKey && it.isSending }
        val drawn = awaitState(60_000, "chat A shows its running answer") { s ->
            s.activeSessionKey == aKey && s.isSending && s.messages.any {
                it is ChatMessage.Assistant && it.isStreaming &&
                    (it.text.isNotBlank() || !it.reasoning.isNullOrBlank())
            }
        }
        val shown = com.hermes.android.ui.screen.buildTurnWork(drawn.messages, true).keys
        val bubble = drawn.messages.last { it is ChatMessage.Assistant && it.isStreaming }
        assertTrue("running answer not drawn: ${describe(drawn)}", bubble.id in shown)
        println("back=${describe(back)}\ndrawn=${describe(drawn)}")
    }
}

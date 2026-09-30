package com.hermes.android.data

import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.gateway.SessionSource
import com.hermes.android.gateway.sessionAttachParams
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract tests for the session-protocol semantics (Milestone A).
 *
 * Every case here is a bug that actually shipped when ViewModels re-guessed
 * these semantics on their own:
 * - a LIVE id handed to session.resume → 4007 (Task Desk open, notification
 *   tap, transport auto-resume)
 * - a STORED id handed to config.set's session path → silent global write
 *   (the "reasoning never changes" bug)
 * - the raw active_list dumped as "tasks" (the cluttered Task Desk)
 * - a task launch adopted as the chat's reconnect auto-resume target
 */
class SessionRepositoryTest {

    private class FakeGatewayClient : GatewayClient {
        override val connectionState: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected(null))
        override val events: SharedFlow<GatewayEvent> = MutableSharedFlow()

        data class Call(
            val method: String,
            val params: Map<String, JsonElement>,
            val trackSession: Boolean,
        )

        val calls = mutableListOf<Call>()
        var handler: (String, Map<String, JsonElement>) -> JsonElement =
            { _, _ -> JsonObject(emptyMap()) }

        override suspend fun connect(url: String, connectTimeoutMs: Long): ConnectionState =
            connectionState.value

        override suspend fun disconnect() = Unit

        override suspend fun request(
            method: String,
            params: Map<String, JsonElement>,
            timeoutMs: Long,
            trackSession: Boolean,
        ): JsonElement {
            calls += Call(method, params, trackSession)
            return handler(method, params)
        }

        override suspend fun notify(method: String, params: Map<String, JsonElement>) = Unit

        override suspend fun downloadFile(url: String): ByteArray = ByteArray(0)
    }

    private class InMemoryTaskRegistry : TaskRegistry {
        val liveIds = mutableSetOf<String>()
        val storedKeys = mutableSetOf<String>()
        override fun register(liveId: String, storedKey: String?) {
            liveIds.add(liveId)
            storedKey?.let { storedKeys.add(it) }
        }
        override fun isTask(liveId: String, sessionKey: String): Boolean =
            liveId in liveIds || (sessionKey.isNotBlank() && sessionKey in storedKeys)
    }

    private lateinit var gateway: FakeGatewayClient
    private lateinit var registry: InMemoryTaskRegistry
    private lateinit var repo: SessionRepository

    @Before
    fun setUp() {
        gateway = FakeGatewayClient()
        registry = InMemoryTaskRegistry()
        repo = SessionRepository(gateway, registry)
    }

    private fun Map<String, JsonElement>.str(key: String) =
        (this[key] as? JsonPrimitive)?.content

    // ── attach: stored vs live id semantics ────────────────────────────────

    @Test
    fun `attach with stored id goes through resume`() = runTest {
        gateway.handler = { method, _ ->
            assertEquals(GatewayMethods.SESSION_RESUME, method)
            buildJsonObject { put("session_id", "live99"); put("resumed", "stored1") }
        }
        val attached = repo.attach("stored1")
        assertEquals("live99", attached.liveId)
        assertEquals(1, gateway.calls.size)
    }

    @Test
    fun `attach with live id falls back to activate on resume failure`() = runTest {
        gateway.handler = { method, _ ->
            when (method) {
                GatewayMethods.SESSION_RESUME ->
                    throw GatewayException("RPC error 4007: session not found")
                GatewayMethods.SESSION_ACTIVATE ->
                    buildJsonObject { put("session_id", "liveA") }
                else -> error("unexpected $method")
            }
        }
        val attached = repo.attach("liveA")
        assertEquals("liveA", attached.liveId)
        assertEquals(
            listOf(GatewayMethods.SESSION_RESUME, GatewayMethods.SESSION_ACTIVATE),
            gateway.calls.map { it.method },
        )
    }

    @Test
    fun `attach preferLive tries activate first`() = runTest {
        gateway.handler = { method, _ ->
            assertEquals(GatewayMethods.SESSION_ACTIVATE, method)
            buildJsonObject { put("session_id", "liveB") }
        }
        assertEquals("liveB", repo.attach("liveB", preferLive = true).liveId)
        assertEquals(1, gateway.calls.size)
    }

    // ── task launch ────────────────────────────────────────────────────────

    @Test
    fun `launchTask registers both ids and never becomes the auto-resume target`() = runTest {
        gateway.handler = { method, _ ->
            when (method) {
                GatewayMethods.SESSION_CREATE -> buildJsonObject {
                    put("session_id", "liveT")
                    put("stored_session_id", "storedT")
                }
                GatewayMethods.PROMPT_SUBMIT -> buildJsonObject { put("status", "streaming") }
                else -> error("unexpected $method")
            }
        }
        val liveId = repo.launchTask("backup", "run the backups")
        assertEquals("liveT", liveId)
        assertTrue(registry.isTask("liveT", ""))
        assertTrue(registry.isTask("other-live", "storedT"))

        val create = gateway.calls.first { it.method == GatewayMethods.SESSION_CREATE }
        assertFalse("task session must not hijack chat auto-resume", create.trackSession)
        assertEquals(SessionSource.TASK, create.params.str("source"))

        val submit = gateway.calls.first { it.method == GatewayMethods.PROMPT_SUBMIT }
        assertEquals("liveT", submit.params.str("session_id"))
        assertEquals("run the backups", submit.params.str("text"))
    }

    // ── task board filtering ───────────────────────────────────────────────

    @Test
    fun `activeTasks returns only registered launches, not the chat session`() = runTest {
        registry.register("liveT", "storedT")
        gateway.handler = { _, _ ->
            buildJsonObject {
                put(
                    "sessions",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", "liveT"); put("session_key", "storedT")
                                put("title", "backup"); put("status", "streaming")
                                put("preview", "working…"); put("model", "m")
                                put("message_count", 3); put("last_active", 2.0)
                            }
                        )
                        add(
                            buildJsonObject {
                                put("id", "chatLive"); put("session_key", "chatStored")
                                put("title", "my chat"); put("status", "streaming")
                                put("preview", "…"); put("model", "m")
                                put("message_count", 9); put("last_active", 5.0)
                            }
                        )
                    }
                )
            }
        }
        val tasks = repo.activeTasks()
        assertEquals(listOf("liveT"), tasks.map { it.id })
        assertTrue(tasks.single().isRunning)
    }

    @Test
    fun `a task the gateway reports as working is running and not finished`() = runTest {
        registry.register("liveT", "storedT")
        registry.register("liveDone", "storedDone")
        gateway.handler = { method, _ ->
            when (method) {
                GatewayMethods.SESSION_ACTIVE_LIST -> buildJsonObject {
                    put("sessions", buildJsonArray {
                        add(buildJsonObject {
                            put("id", "liveT"); put("session_key", "storedT"); put("status", "working")
                        })
                        add(buildJsonObject {
                            put("id", "liveDone"); put("session_key", "storedDone"); put("status", "idle")
                        })
                    })
                }
                // session.list names tasks by their stored id.
                GatewayMethods.SESSION_LIST -> buildJsonObject {
                    put("sessions", buildJsonArray {
                        for (id in listOf("storedT", "storedDone")) {
                            add(buildJsonObject {
                                put("id", id); put("source", SessionSource.TASK); put("message_count", 2)
                            })
                        }
                    })
                }
                else -> JsonObject(emptyMap())
            }
        }

        assertTrue(repo.activeTasks().first { it.id == "liveT" }.isRunning)
        assertEquals(listOf("storedDone"), repo.finishedTasks().map { it.id })
    }

    // ── reasoning scope semantics ──────────────────────────────────────────

    @Test
    fun `setReasoningLevel with a live session writes session-scoped then persists globally`() = runTest {
        gateway.handler = { _, _ -> buildJsonObject { put("key", "reasoning"); put("value", "high") } }
        val level = repo.setReasoningLevel("high", liveSessionId = "live1")
        assertEquals("high", level)
        assertEquals(2, gateway.calls.size)
        assertEquals("live1", gateway.calls[0].params.str("session_id"))
        assertEquals("high", gateway.calls[0].params.str("value"))
        assertEquals(null, gateway.calls[1].params.str("session_id"))
        assertEquals("high", gateway.calls[1].params.str("value"))
    }

    @Test
    fun `setReasoningLevel without a session writes the global default once`() = runTest {
        gateway.handler = { _, _ -> buildJsonObject { put("value", "low") } }
        repo.setReasoningLevel("low", liveSessionId = null)
        assertEquals(1, gateway.calls.size)
        assertEquals(null, gateway.calls[0].params.str("session_id"))
    }

    @Test
    fun `reasoningLevel prefers server value and defaults to medium`() = runTest {
        gateway.handler = { _, _ -> buildJsonObject { put("value", "xhigh") } }
        assertEquals("xhigh", repo.reasoningLevel("live1"))
        assertEquals("live1", gateway.calls[0].params.str("session_id"))

        gateway.handler = { _, _ -> buildJsonObject { put("value", "") } }
        assertEquals("medium", repo.reasoningLevel(null))
    }

    // ── Milestone B ─────────────────────────────────────────────────────────

    @Test
    fun `launchTask forwards per-task reasoning effort and model`() = runTest {
        gateway.handler = { method, _ ->
            when (method) {
                GatewayMethods.SESSION_CREATE -> buildJsonObject {
                    put("session_id", "liveT"); put("stored_session_id", "storedT")
                }
                else -> buildJsonObject { put("status", "streaming") }
            }
        }
        repo.launchTask(
            "deep", "analyze logs",
            reasoningEffort = "high",
            model = SessionRepository.ModelChoice(provider = "anthropic", modelId = "claude-x"),
        )
        val create = gateway.calls.first { it.method == GatewayMethods.SESSION_CREATE }
        assertEquals("high", create.params.str("reasoning_effort"))
        assertEquals("claude-x", create.params.str("model"))
        assertEquals("anthropic", create.params.str("provider"))
    }

    @Test
    fun `availableModels flattens providers into provider+model pairs`() = runTest {
        gateway.handler = { _, _ ->
            buildJsonObject {
                put(
                    "providers",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("slug", "anthropic")
                                put("models", buildJsonArray { add(JsonPrimitive("claude-x")); add(JsonPrimitive("claude-y")) })
                            }
                        )
                        add(
                            buildJsonObject {
                                put("slug", "openai")
                                put("models", buildJsonArray { add(JsonPrimitive("gpt-z")) })
                            }
                        )
                    }
                )
            }
        }
        val models = repo.availableModels()
        assertEquals(3, models.size)
        assertEquals(SessionRepository.ModelChoice("anthropic", "claude-x"), models[0])
        assertEquals(SessionRepository.ModelChoice("openai", "gpt-z"), models[2])
    }

    @Test
    fun `taskHistory returns only our source AND registered rows`() = runTest {
        registry.register("mine", "mine")
        gateway.handler = { _, _ ->
            buildJsonObject {
                put(
                    "sessions",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", "mine"); put("title", "my task")
                                put("preview", "done"); put("message_count", 4)
                                put("source", SessionSource.TASK)
                            }
                        )
                        // right source, but this device never launched it
                        add(
                            buildJsonObject {
                                put("id", "someone-else"); put("title", "x")
                                put("source", SessionSource.TASK)
                            }
                        )
                        // our registry but a normal chat source → not a task
                        add(
                            buildJsonObject {
                                put("id", "mine"); put("title", "chat"); put("source", "tui")
                            }
                        )
                    }
                )
            }
        }
        val history = repo.taskHistory()
        assertEquals(listOf("mine"), history.map { it.id })
        assertEquals(4, history.single().messageCount)
    }

    @Test
    fun `transcript attaches then flattens roles and text`() = runTest {
        gateway.handler = { method, _ ->
            when (method) {
                GatewayMethods.SESSION_RESUME, GatewayMethods.SESSION_ACTIVATE -> buildJsonObject {
                    put("session_id", "liveX")
                    put(
                        "messages",
                        buildJsonArray {
                            add(buildJsonObject { put("role", "user"); put("content", "hi") })
                            add(buildJsonObject { put("role", "assistant"); put("text", "hello") })
                            add(buildJsonObject { put("role", "assistant"); put("content", "  ") })
                        }
                    )
                }
                else -> JsonObject(emptyMap())
            }
        }
        val t = repo.transcript("storedX")
        assertEquals(2, t.size) // blank-only message dropped
        assertEquals("user", t[0].role)
        assertEquals("hi", t[0].text)
        assertEquals("hello", t[1].text)
        // Reading a task's transcript must not become the chat's reconnect target.
        assertTrue(gateway.calls.none { it.trackSession })
    }

    @Test
    fun `storedMessages reads history on the live id when resume carries none`() = runTest {
        gateway.handler = { method, params ->
            when (method) {
                GatewayMethods.SESSION_RESUME -> buildJsonObject { put("session_id", "liveH") }
                GatewayMethods.SESSION_HISTORY -> {
                    assertEquals("liveH", params.str("session_id"))
                    buildJsonObject {
                        put("messages", buildJsonArray {
                            add(buildJsonObject { put("role", "user"); put("text", "q") })
                        })
                    }
                }
                else -> error("unexpected $method")
            }
        }
        val stored = repo.storedMessages("storedH")
        assertEquals("liveH", stored.liveId)
        assertEquals(1, stored.messages?.size)
        assertTrue(gateway.calls.none { it.trackSession })
    }

    @Test
    fun `rename titles the live id, not the stored id from the list`() = runTest {
        gateway.handler = { method, _ ->
            when (method) {
                GatewayMethods.SESSION_RESUME -> buildJsonObject { put("session_id", "liveR") }
                GatewayMethods.SESSION_TITLE -> buildJsonObject { put("title", "New") }
                else -> error("unexpected $method")
            }
        }
        repo.rename("storedR", "New")
        assertEquals(
            listOf(GatewayMethods.SESSION_RESUME, GatewayMethods.SESSION_TITLE),
            gateway.calls.map { it.method },
        )
        val title = gateway.calls.last().params
        assertEquals("liveR", title.str("session_id"))
        assertEquals("New", title.str("title"))
        assertTrue(gateway.calls.none { it.trackSession })
    }

    // ── onLiveSession: a reclaimed live id ─────────────────────────────────

    @Test
    fun `onLiveSession passes a healthy live id straight through`() = runTest {
        var rebound: SessionRepository.AttachedSession? = null
        val result = repo.onLiveSession("live1", "stored1", { rebound = it }) { live -> "ok:$live" }
        assertEquals("ok:live1", result)
        assertEquals(null, rebound)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun `onLiveSession re-attaches through the stored id when the live id is gone`() = runTest {
        gateway.handler = { method, _ ->
            assertEquals(GatewayMethods.SESSION_RESUME, method)
            buildJsonObject { put("session_id", "live2"); put("session_key", "stored1") }
        }
        val tried = mutableListOf<String>()
        var rebound: SessionRepository.AttachedSession? = null
        val result = repo.onLiveSession("live1", "stored1", { rebound = it }) { live ->
            tried += live
            if (live == "live1") throw GatewayException("RPC error 4001: session not found")
            "sent:$live"
        }
        assertEquals("sent:live2", result)
        assertEquals(listOf("live1", "live2"), tried)
        assertEquals("live2", rebound?.liveId)
        assertEquals("stored1", rebound?.storedId)
    }

    @Test
    fun `onLiveSession does not retry other failures`() = runTest {
        val tried = mutableListOf<String>()
        val error = runCatching {
            repo.onLiveSession("live1", "stored1", {}) { live ->
                tried += live
                throw GatewayException("RPC error 4009: session busy")
            }
        }.exceptionOrNull()
        assertTrue(error is GatewayException)
        assertEquals(listOf("live1"), tried)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun `onLiveSession without a stored id surfaces the dead live id`() = runTest {
        val error = runCatching {
            repo.onLiveSession<String>("live1", null, {}) {
                throw GatewayException("RPC error 4001: session not found")
            }
        }.exceptionOrNull()
        assertTrue(error is GatewayException)
        assertTrue(gateway.calls.isEmpty())
    }

    // ── chats ──────────────────────────────────────────────────────────────

    @Test
    fun `a new chat is filed as desktop, in the project's folder when given`() = runTest {
        gateway.handler = { method, _ ->
            assertEquals(GatewayMethods.SESSION_CREATE, method)
            buildJsonObject { put("session_id", "liveC"); put("stored_session_id", "storedC") }
        }
        val created = repo.createChat(cwd = "/root/project")
        assertEquals("liveC", created?.liveId)
        assertEquals("storedC", created?.storedId)
        assertEquals(SessionSource.CHAT, gateway.calls.single().params.str("source"))
        assertEquals("/root/project", gateway.calls.single().params.str("cwd"))

        repo.createChat()
        assertFalse("cwd" in gateway.calls.last().params)
    }

    @Test
    fun `mostRecentId takes no null for an id`() = runTest {
        var id: JsonElement = JsonPrimitive("stored1")
        gateway.handler = { _, _ -> buildJsonObject { put("session_id", id) } }
        assertEquals("stored1", repo.mostRecentId())
        id = JsonPrimitive("null")
        assertEquals(null, repo.mostRecentId())
        id = kotlinx.serialization.json.JsonNull
        assertEquals(null, repo.mostRecentId())
    }

    @Test
    fun `stopTurn interrupts, then ends only the chat's running processes`() = runTest {
        gateway.handler = { method, _ ->
            when (method) {
                GatewayMethods.PROCESS_LIST -> buildJsonObject {
                    put("processes", buildJsonArray {
                        add(buildJsonObject { put("session_id", "proc_1"); put("status", "running") })
                        add(buildJsonObject { put("session_id", "proc_2"); put("status", "exited") })
                    })
                }
                else -> JsonObject(emptyMap())
            }
        }
        repo.stopTurn("live1")
        assertEquals(
            listOf(GatewayMethods.SESSION_INTERRUPT, GatewayMethods.PROCESS_LIST, GatewayMethods.PROCESS_KILL),
            gateway.calls.map { it.method },
        )
        assertTrue(gateway.calls.all { it.params.str("session_id") == "live1" })
        assertEquals("proc_1", gateway.calls.last().params.str("process_id"))
    }

    @Test
    fun `steer reports a refusal with the server's note`() = runTest {
        gateway.handler = { _, _ -> buildJsonObject { put("status", "rejected"); put("text", "not now") } }
        assertEquals(SessionRepository.SteerResult(accepted = false, note = "not now"), repo.steer("live1", "go left"))
        assertEquals("go left", gateway.calls.single().params.str("text"))

        gateway.handler = { _, _ -> buildJsonObject { put("status", "queued") } }
        assertTrue(repo.steer("live1", "go right").accepted)
    }

    @Test
    fun `a scratch session is closed even when the write in it fails`() = runTest {
        gateway.handler = { method, _ ->
            if (method == GatewayMethods.SESSION_CREATE) buildJsonObject { put("session_id", "scratch") } else JsonObject(emptyMap())
        }
        val error = runCatching {
            repo.withScratchSession<Unit> { throw GatewayException("RPC error 4002: bad model") }
        }.exceptionOrNull()
        assertTrue(error is GatewayException)
        val close = gateway.calls.last()
        assertEquals(GatewayMethods.SESSION_CLOSE, close.method)
        assertEquals("scratch", close.params.str("session_id"))
    }

    @Test
    fun `source goes with session resume only, since session activate rejects it`() {
        val resume = sessionAttachParams(GatewayMethods.SESSION_RESUME, "s1")
        assertEquals(JsonPrimitive(SessionSource.CHAT), resume["source"])
        val activate = sessionAttachParams(GatewayMethods.SESSION_ACTIVATE, "s1")
        assertEquals(setOf("session_id"), activate.keys)
    }
}

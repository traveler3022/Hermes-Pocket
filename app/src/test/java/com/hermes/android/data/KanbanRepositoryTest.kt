package com.hermes.android.data

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import com.hermes.android.runtime.KanbanSwitch
import com.hermes.android.runtime.remote.RemoteServerConfig
import com.hermes.android.runtime.remote.RemoteServerSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Calls in the shapes of `plugins/kanban/dashboard/plugin_api.py`, answered without a network. */
class KanbanRepositoryTest {

    private val sent = mutableListOf<Pair<Request, String>>()
    private var answer: (Request) -> Pair<Int, String> = { 200 to "{}" }

    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val body = request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
        sent += request to body
        val (code, text) = answer(request)
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("x")
            .body(text.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    /** What the phone's gateway was asked (`android.kanban` params) and answers. */
    private val phoneCalls = mutableListOf<Map<String, JsonElement>>()
    private var phoneAnswer: (Map<String, JsonElement>) -> JsonElement = { buildJsonObject { put("status", 200) } }
    private val gateway = mockk<GatewayClient> {
        coEvery { request("android.kanban", any(), any(), any()) } answers {
            @Suppress("UNCHECKED_CAST") val params = secondArg<Map<String, JsonElement>>()
            phoneCalls += params
            phoneAnswer(params)
        }
    }

    private fun repo(type: RuntimeType = RuntimeType.REMOTE, switchedOn: Boolean = true): KanbanRepository {
        val runtime = mockk<HermesRuntime> { every { this@mockk.type } returns type }
        val settings = mockk<RemoteServerSettings> {
            every { config } returns MutableStateFlow(RemoteServerConfig("https://box.tail1234.ts.net"))
        }
        val switch = mockk<KanbanSwitch> { every { on } returns MutableStateFlow(switchedOn) }
        return KanbanRepository(http, Json { ignoreUnknownKeys = true }, runtime, settings, gateway, switch)
    }

    @Test
    fun `the board reads columns and cards`() = runTest {
        answer = {
            200 to """{"columns":[{"name":"ready","tasks":[{"id":"t_1","title":"Write the brief","assignee":"writer",
                "status":"ready","priority":2,"created_at":1700000000,"latest_summary":"draft done",
                "comment_count":3,"progress":{"done":1,"total":2}}]},{"name":"done","tasks":[]}]}"""
        }
        val board = repo().board()

        val task = board.getValue("ready").single()
        assertEquals("Write the brief", task.title)
        assertEquals("writer", task.assignee)
        assertEquals("draft done", task.summary)
        assertEquals(3, task.commentCount)
        assertEquals(1 to 2, task.progress)
        assertEquals(emptyList<KanbanRepository.Task>(), board.getValue("done"))
        assertEquals("https://box.tail1234.ts.net/api/plugins/kanban/board", sent.single().first.url.toString())
    }

    @Test
    fun `an assigned task is created ready and the dispatcher is nudged`() = runTest {
        repo().create("Check the logs", "", assignee = "ops")

        val (create, body) = sent[0]
        assertEquals("POST", create.method)
        assertEquals("/api/plugins/kanban/tasks", create.url.encodedPath)
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("ops", json["assignee"]!!.jsonPrimitive.content)
        assertFalse("triage" in json)
        assertTrue(json["idempotency_key"]!!.jsonPrimitive.content.startsWith("app-"))
        assertEquals("/api/plugins/kanban/dispatch", sent[1].first.url.encodedPath)
    }

    @Test
    fun `an unassigned task goes to triage for the decomposer`() = runTest {
        repo().create("Plan the launch", "details", assignee = null)

        val json = Json.parseToJsonElement(sent.single().second).jsonObject
        assertEquals("true", json["triage"]!!.jsonPrimitive.content)
        assertFalse("assignee" in json)
    }

    @Test
    fun `a refused move shows the server's reason`() = runTest {
        answer = { 409 to """{"detail":"Cannot move to 'ready': blocked by parent(s) not done"}""" }
        val error = runCatching { repo().move("t_9", "ready") }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("Cannot move to 'ready': blocked by parent(s) not done", error!!.message)
        val (patch, body) = sent.first()
        assertEquals("PATCH", patch.method)
        assertEquals("/api/plugins/kanban/tasks/t_9", patch.url.encodedPath)
        assertEquals("ready", Json.parseToJsonElement(body).jsonObject["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `off unless switched on, and never on Termux`() {
        assertTrue(repo().available)
        assertTrue(repo(RuntimeType.PROOT_LINUX).available)
        assertFalse(repo(switchedOn = false).available)
        assertFalse(repo(RuntimeType.PROOT_LINUX, switchedOn = false).available)
        assertFalse(repo(RuntimeType.TERMUX).available)
    }

    @Test
    fun `assignees come as rows with a name`() = runTest {
        answer = { 200 to """{"assignees":[{"name":"critic","on_disk":true,"counts":{}},{"name":"default","on_disk":true}]}""" }
        assertEquals(listOf("critic", "default"), repo().assignees())
    }

    @Test
    fun `on the phone the same calls go to the gateway`() = runTest {
        phoneAnswer = { buildJsonObject { put("status", 200); put("body", Json.parseToJsonElement("""{"columns":[{"name":"ready","tasks":[{"id":"t_1","title":"x","status":"ready"}]}]}""")) } }
        val board = repo(RuntimeType.PROOT_LINUX).board()

        assertEquals("t_1", board.getValue("ready").single().id)
        assertEquals("GET", phoneCalls.single()["method"]!!.jsonPrimitive.content)
        assertEquals("/board", phoneCalls.single()["path"]!!.jsonPrimitive.content)
        assertTrue("no HTTP on the phone", sent.isEmpty())
    }

    @Test
    fun `on the phone an unassigned task is split right after it is made`() = runTest {
        phoneAnswer = { params ->
            buildJsonObject {
                put("status", 200)
                if (params["path"]!!.jsonPrimitive.content == "/tasks") put("body", Json.parseToJsonElement("""{"task":{"id":"t_7","title":"Plan","status":"triage"}}"""))
            }
        }
        repo(RuntimeType.PROOT_LINUX).create("Plan the launch", "", assignee = null)

        assertEquals(listOf("/tasks", "/tasks/t_7/decompose", "/dispatch"), phoneCalls.map { it["path"]!!.jsonPrimitive.content })
    }

    @Test
    fun `on the phone a refusal carries the reason`() = runTest {
        phoneAnswer = { buildJsonObject { put("status", 404); put("body", Json.parseToJsonElement("""{"detail":"task nope not found"}""")) } }
        val error = runCatching { repo(RuntimeType.PROOT_LINUX).move("nope", "ready") }.exceptionOrNull()

        assertEquals("task nope not found", error?.message)
    }
}

package com.hermes.android.data

import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import com.hermes.android.runtime.remote.RemoteServerConfig
import com.hermes.android.runtime.remote.RemoteServerSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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

    private fun repo(type: RuntimeType = RuntimeType.REMOTE): KanbanRepository {
        val runtime = mockk<HermesRuntime> { every { this@mockk.type } returns type }
        val settings = mockk<RemoteServerSettings> {
            every { config } returns MutableStateFlow(RemoteServerConfig("https://box.tail1234.ts.net"))
        }
        return KanbanRepository(http, Json { ignoreUnknownKeys = true }, runtime, settings)
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
    fun `only a remote server offers the board`() {
        assertTrue(repo().available)
        assertFalse(repo(RuntimeType.PROOT_LINUX).available)
        assertFalse(repo(RuntimeType.TERMUX).available)
    }
}

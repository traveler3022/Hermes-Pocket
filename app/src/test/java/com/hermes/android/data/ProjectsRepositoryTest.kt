package com.hermes.android.data

import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayMethods
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract test locking in the project-tree wire shape verified against
 * `tui_gateway/project_tree.py::build_tree` / `_build_repos` — it is
 * camelCase (`label`, `sessionCount`, `groups`), NOT the snake_case the rest
 * of the protocol uses. Getting this wrong is a silent "browser shows
 * nothing" bug, not a compile error — the exact failure mode this repo
 * layer exists to prevent.
 */
class ProjectsRepositoryTest {

    private class FakeGatewayClient(
        private val handler: (String, Map<String, JsonElement>) -> JsonElement,
    ) : GatewayClient {
        override val connectionState: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected(null))
        override val events: SharedFlow<GatewayEvent> = MutableSharedFlow()

        override suspend fun connect(url: String, connectTimeoutMs: Long) = connectionState.value
        override suspend fun disconnect() = Unit
        override suspend fun request(
            method: String,
            params: Map<String, JsonElement>,
            timeoutMs: Long,
            trackSession: Boolean,
        ): JsonElement = handler(method, params)
        override suspend fun notify(method: String, params: Map<String, JsonElement>) = Unit
        override suspend fun downloadFile(url: String): ByteArray = ByteArray(0)
    }

    @Test
    fun `projectTree parses the real camelCase project-node shape`() = runTest {
        val gateway = FakeGatewayClient { method, _ ->
            assertEquals(GatewayMethods.PROJECTS_TREE, method)
            buildJsonObject {
                put(
                    "projects",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", "proj1")
                                put("label", "Hermes Pocket")
                                put("path", "/home/user/Hermes-Pocket")
                                put("sessionCount", 4)
                                put("lastActive", 100.0)
                                put("repos", buildJsonArray {})
                                put("previewSessions", buildJsonArray {})
                            }
                        )
                    }
                )
                put("active_id", "proj1")
                put("scoped_session_ids", buildJsonArray {})
            }
        }
        val repo = ProjectsRepository(gateway)
        val projects = repo.projectTree()
        assertEquals(1, projects.size)
        assertEquals("proj1", projects[0].id)
        assertEquals("Hermes Pocket", projects[0].label)
        assertEquals(4, projects[0].sessionCount)
        assertEquals("/home/user/Hermes-Pocket", projects[0].path)
    }

    @Test
    fun `projectSessions flattens repos-groups-sessions into a flat list`() = runTest {
        val gateway = FakeGatewayClient { method, _ ->
            assertEquals(GatewayMethods.PROJECTS_PROJECT_SESSIONS, method)
            buildJsonObject {
                put(
                    "project",
                    buildJsonObject {
                        put("id", "proj1")
                        put("label", "Hermes Pocket")
                        put(
                            "repos",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", "repo1")
                                        put("label", "Hermes-Pocket")
                                        put("path", "/home/user/Hermes-Pocket")
                                        put("sessionCount", 2)
                                        put(
                                            "groups",
                                            buildJsonArray {
                                                add(
                                                    buildJsonObject {
                                                        put("id", "lane1")
                                                        put("label", "main")
                                                        put("isMain", true)
                                                        put("isKanban", false)
                                                        put(
                                                            "sessions",
                                                            buildJsonArray {
                                                                add(
                                                                    buildJsonObject {
                                                                        put("id", "s1")
                                                                        put("title", "fix reconnect")
                                                                        put("preview", "…")
                                                                        put("last_active", 50.0)
                                                                    }
                                                                )
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        }
        val repo = ProjectsRepository(gateway)
        val detail = repo.projectSessions("proj1")
        assertEquals("Hermes Pocket", detail?.label)
        assertEquals(1, detail?.sessions?.size)
        assertEquals("s1", detail?.sessions?.get(0)?.id)
        assertEquals("fix reconnect", detail?.sessions?.get(0)?.title)
    }
    @Test
    fun `createProject sends name and folders and parses the snake_case ProjectInfo`() = runTest {
        var sent: Map<String, JsonElement> = emptyMap()
        val gateway = FakeGatewayClient { method, params ->
            assertEquals(GatewayMethods.PROJECTS_CREATE, method)
            sent = params
            buildJsonObject {
                put(
                    "project",
                    buildJsonObject {
                        put("id", "p_1a2b3c4d")
                        put("slug", "pocket")
                        put("name", "Pocket")
                        put("primary_path", "/root/Hermes-Pocket")
                        put("archived", false)
                        put("created_at", 1)
                        put(
                            "folders",
                            buildJsonArray {
                                add(buildJsonObject { put("path", "/root/Hermes-Pocket"); put("is_primary", true) })
                                add(buildJsonObject { put("path", "/root/docs") })
                            },
                        )
                    },
                )
            }
        }
        val project = ProjectsRepository(gateway).createProject("Pocket", "~/Hermes-Pocket")
        assertEquals(setOf("name", "folders"), sent.keys)
        assertEquals("[\"~/Hermes-Pocket\"]", sent["folders"].toString())
        assertEquals("p_1a2b3c4d", project?.id)
        assertEquals("/root/Hermes-Pocket", project?.primaryPath)
        assertEquals(listOf("/root/Hermes-Pocket", "/root/docs"), project?.folders)
        assertEquals(false, project?.archived)
    }

    @Test
    fun `projectTree marks only user-made projects as stored`() = runTest {
        val gateway = FakeGatewayClient { _, _ ->
            buildJsonObject {
                put(
                    "projects",
                    buildJsonArray {
                        add(buildJsonObject { put("id", "__no_project__"); put("isNoProject", true) })
                        add(buildJsonObject { put("id", "/root/repo"); put("isAuto", true) })
                        add(buildJsonObject { put("id", "p_1"); put("isAuto", false); put("isNoProject", false) })
                    },
                )
            }
        }
        val stored = ProjectsRepository(gateway).projectTree().associate { it.id to it.isStored }
        assertEquals(mapOf("__no_project__" to false, "/root/repo" to false, "p_1" to true), stored)
    }
}

package com.hermes.android.data

import com.hermes.android.domain.projects.Projects
import com.hermes.android.domain.projects.Projects.ProjectSummary
import com.hermes.android.domain.projects.Projects.ProjectSession
import com.hermes.android.domain.projects.Projects.ProjectDetail
import com.hermes.android.domain.projects.Projects.StoredProject
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.contentOrNull

/**
 * The gateway's project browser (`projects.tree` / `projects.project_sessions` /
 * `project.facts`) plus the user's own stored projects (`projects.list/get/create/update/
 * archive/add_folder/remove_folder`, as the desktop manages them). Repos are discovered
 * server-side from existing session cwds — no client filesystem crawl (that's
 * the desktop's local-fs `projects.discover_repos` / `record_repos` path,
 * which has no mobile equivalent and is deliberately not wired here).
 *
 * Wire shape (verified against `tui_gateway/project_tree.py::build_tree` and
 * `_build_repos` — NOT the snake_case the rest of the protocol uses):
 * project `{id, label, sessionCount, lastActive, repos:[repo], previewSessions:[session]}`,
 * repo `{id, label, path, groups:[lane], sessionCount}`,
 * lane `{id, label, isMain, isKanban, sessions:[session]}`,
 * session (still snake_case) `{id, title, preview, last_active}`.
 */
@Singleton
class ProjectsRepository @Inject constructor(
    private val gatewayClient: GatewayClient,
) : Projects {
    /** Top-level project list for the browser screen. */
    override suspend fun projectTree(): List<ProjectSummary> {
        val result = gatewayClient.request(GatewayMethods.PROJECTS_TREE) as? JsonObject
            ?: return emptyList()
        val rows = result["projects"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { it as? JsonObject }.map { p ->
            ProjectSummary(
                id = p.str("id"),
                label = p.str("label").ifBlank { p.str("id") },
                // A project with no primary folder still has repo folders; the first one is its root,
                // as the desktop's projectRootCwd picks it.
                path = p.str("path").ifBlank {
                    (p["repos"] as? JsonArray)?.firstNotNullOfOrNull { r ->
                        (r as? JsonObject)?.str("path")?.takeIf { it.isNotBlank() }
                    }.orEmpty()
                },
                sessionCount = p.str("sessionCount").toIntOrNull() ?: 0,
                lastActive = p.str("lastActive").toDoubleOrNull() ?: 0.0,
                isStored = p.str("isAuto") != "true" && p.str("isNoProject") != "true",
            )
        }.sortedByDescending { it.lastActive }
    }

    /** Fully hydrated session list for one project (drill-in). */
    override suspend fun projectSessions(projectId: String): ProjectDetail? {
        val result = gatewayClient.request(
            GatewayMethods.PROJECTS_PROJECT_SESSIONS,
            buildJsonObject { put("project_id", projectId) }.toElementMap(),
        ) as? JsonObject ?: return null
        val proj = result["project"] as? JsonObject ?: return null
        val sessions = mutableListOf<ProjectSession>()
        (proj["repos"] as? JsonArray)?.forEach { repoEl ->
            val repo = repoEl as? JsonObject ?: return@forEach
            (repo["groups"] as? JsonArray)?.forEach { laneEl ->
                val lane = laneEl as? JsonObject ?: return@forEach
                (lane["sessions"] as? JsonArray)?.forEach { sEl ->
                    val s = sEl as? JsonObject ?: return@forEach
                    sessions += ProjectSession(
                        id = s.str("id"),
                        title = s.str("title").ifBlank { s.str("id") },
                        preview = s.str("preview"),
                        lastActive = s.str("last_active").toDoubleOrNull() ?: 0.0,
                    )
                }
            }
        }
        return ProjectDetail(
            id = proj.str("id"),
            label = proj.str("label").ifBlank { proj.str("id") },
            sessions = sessions.sortedByDescending { it.lastActive },
        )
    }

    /** Structured facts (manifests, package manager, verify commands) for a cwd. */
    override suspend fun projectFacts(cwd: String): String? {
        val result = gatewayClient.request(
            GatewayMethods.PROJECT_FACTS,
            buildJsonObject { put("cwd", cwd) }.toElementMap(),
        ) as? JsonObject ?: return null
        val facts = result["facts"] as? JsonObject ?: return null
        return facts.entries.joinToString("\n") { (k, v) ->
            "$k: ${(v as? JsonPrimitive)?.contentOrNull ?: v.toString()}"
        }
    }

    /** `session.workspace.move`: the server replaces the chat's cwd and git identity; a live agent follows. */
    override suspend fun moveSession(sessionId: String, cwd: String) {
        gatewayClient.request(
            GatewayMethods.SESSION_WORKSPACE_MOVE,
            buildJsonObject {
                put("session_key", sessionId)
                put("cwd", cwd)
            }.toElementMap(),
        )
    }

    override suspend fun storedProjects(): List<StoredProject> {
        val result = gatewayClient.request(GatewayMethods.PROJECTS_LIST) as? JsonObject ?: return emptyList()
        return (result["projects"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toStoredProject() }
    }

    override suspend fun storedProject(id: String): StoredProject? =
        projectResult(GatewayMethods.PROJECTS_GET, buildJsonObject { put("id", id) })

    override suspend fun createProject(name: String, folder: String): StoredProject? =
        projectResult(
            GatewayMethods.PROJECTS_CREATE,
            buildJsonObject {
                put("name", name)
                put("folders", buildJsonArray { add(folder) })
            },
        )

    override suspend fun renameProject(id: String, name: String): StoredProject? =
        projectResult(
            GatewayMethods.PROJECTS_UPDATE,
            buildJsonObject {
                put("id", id)
                put("name", name)
            },
        )

    override suspend fun archiveProject(id: String, restore: Boolean) {
        gatewayClient.request(
            GatewayMethods.PROJECTS_ARCHIVE,
            buildJsonObject {
                put("id", id)
                put("restore", restore)
            }.toElementMap(),
        )
    }

    override suspend fun addFolder(id: String, path: String): StoredProject? =
        projectResult(
            GatewayMethods.PROJECTS_ADD_FOLDER,
            buildJsonObject {
                put("id", id)
                put("path", path)
            },
        )

    override suspend fun removeFolder(id: String, path: String): StoredProject? =
        projectResult(
            GatewayMethods.PROJECTS_REMOVE_FOLDER,
            buildJsonObject {
                put("id", id)
                put("path", path)
            },
        )

    /** Calls a method that answers `{project: ProjectInfo}` (snake_case, `projects_db.Project.to_dict`). */
    private suspend fun projectResult(method: String, params: JsonObject): StoredProject? {
        val result = gatewayClient.request(method, params.toElementMap()) as? JsonObject ?: return null
        return (result["project"] as? JsonObject)?.toStoredProject()
    }

    private fun JsonObject.toStoredProject(): StoredProject? {
        val id = str("id").ifBlank { return null }
        val folders = (this["folders"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.str("path")?.takeIf(String::isNotBlank) }
        return StoredProject(
            id = id,
            name = str("name").ifBlank { str("slug").ifBlank { id } },
            primaryPath = str("primary_path").ifBlank { folders.firstOrNull().orEmpty() }.ifBlank { null },
            archived = str("archived") == "true",
            folders = folders,
        )
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    private fun JsonObject.toElementMap(): Map<String, JsonElement> =
        entries.associate { (k, v) -> k to v }
}

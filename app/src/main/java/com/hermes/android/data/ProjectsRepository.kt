package com.hermes.android.data

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-side of the gateway's project browser (`projects.tree` /
 * `projects.project_sessions` / `project.facts`). Repos are discovered
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
) {
    data class ProjectSummary(
        val id: String,
        val label: String,
        val path: String,
        val sessionCount: Int,
        val lastActive: Double,
    )

    data class ProjectSession(
        val id: String,
        val title: String,
        val preview: String,
        val lastActive: Double,
    )

    data class ProjectDetail(
        val id: String,
        val label: String,
        val sessions: List<ProjectSession>,
    )

    /** Top-level project list for the browser screen. */
    suspend fun projectTree(): List<ProjectSummary> {
        val result = gatewayClient.request(GatewayMethods.PROJECTS_TREE) as? JsonObject
            ?: return emptyList()
        val rows = result["projects"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { it as? JsonObject }.map { p ->
            ProjectSummary(
                id = p.str("id"),
                label = p.str("label").ifBlank { p.str("id") },
                path = p.str("path"),
                sessionCount = p.str("sessionCount").toIntOrNull() ?: 0,
                lastActive = p.str("lastActive").toDoubleOrNull() ?: 0.0,
            )
        }.sortedByDescending { it.lastActive }
    }

    /** Fully hydrated session list for one project (drill-in). */
    suspend fun projectSessions(projectId: String): ProjectDetail? {
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
    suspend fun projectFacts(cwd: String): String? {
        val result = gatewayClient.request(
            GatewayMethods.PROJECT_FACTS,
            buildJsonObject { put("cwd", cwd) }.toElementMap(),
        ) as? JsonObject ?: return null
        val facts = result["facts"] as? JsonObject ?: return null
        return facts.entries.joinToString("\n") { (k, v) ->
            "$k: ${(v as? JsonPrimitive)?.content ?: v.toString()}"
        }
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.content ?: ""

    private fun JsonObject.toElementMap(): Map<String, JsonElement> =
        entries.associate { (k, v) -> k to v }
}

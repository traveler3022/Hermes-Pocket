package com.hermes.android.domain.projects

/** The gateway's project browser: projects found from session cwds, and their sessions. */
interface Projects {

    /** Top-level project list for the browser screen. */
    suspend fun projectTree(): List<ProjectSummary>

    /** Fully hydrated session list for one project (drill-in). */
    suspend fun projectSessions(projectId: String): ProjectDetail?

    /** Structured facts (manifests, package manager, verify commands) for a cwd. */
    suspend fun projectFacts(cwd: String): String?

    /** Re-homes a stored chat into [cwd] (a project's folder), so it is listed under that project. */
    suspend fun moveSession(sessionId: String, cwd: String)

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
}

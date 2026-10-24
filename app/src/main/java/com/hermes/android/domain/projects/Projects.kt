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

    /** Every stored project, archived ones included (`projects.list`). */
    suspend fun storedProjects(): List<StoredProject>

    /** One stored project with its folders (`projects.get`). */
    suspend fun storedProject(id: String): StoredProject?

    /** Creates a stored project named [name] working in [folder] (`projects.create`). */
    suspend fun createProject(name: String, folder: String): StoredProject?

    /** Renames a stored project (`projects.update`). */
    suspend fun renameProject(id: String, name: String): StoredProject?

    /** Archives a stored project, or brings it back when [restore] (`projects.archive`). */
    suspend fun archiveProject(id: String, restore: Boolean)

    /** Attaches [path] to a stored project (`projects.add_folder`). */
    suspend fun addFolder(id: String, path: String): StoredProject?

    /** Detaches [path] from a stored project (`projects.remove_folder`). */
    suspend fun removeFolder(id: String, path: String): StoredProject?

    data class ProjectSummary(
        val id: String,
        val label: String,
        val path: String,
        val sessionCount: Int,
        val lastActive: Double,
        /** A project the user made (`p_…`), as opposed to one grouped automatically from session folders. */
        val isStored: Boolean = false,
    )

    /** A project the user made, with its folders; [primaryPath] is where its new chats start. */
    data class StoredProject(
        val id: String,
        val name: String,
        val primaryPath: String?,
        val archived: Boolean,
        val folders: List<String>,
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

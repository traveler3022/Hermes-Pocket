package com.hermes.android.gateway

import kotlinx.serialization.Serializable

/**
 * Client → Server JSON-RPC requests.
 *
 * Each request has a unique `id` (assigned by the client) and a `method` name.
 * The server responds with a JSON-RPC response carrying the same `id`.
 *
 * Reference: `tui_gateway/server.py:965` (`@method()` decorator for RPC registration).
 *
 * Phase 1.5 Rule 1 (Strict Layer Dependency): this is a Domain type — no
 * OkHttp or networking imports.
 */
@Serializable
data class GatewayRequest(
    val jsonrpc: String = "2.0",
    val id: Long,
    val method: String,
    val params: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
)

/**
 * Factory for the RPC methods used in Steps 3-7.
 *
 * Keeps method-name strings in one place so they don't get typo'd across
 * the codebase. Add new methods here as needed.
 */
object GatewayMethods {
    const val SESSION_CREATE = "session.create"
    const val SESSION_LIST = "session.list"
    const val SESSION_MOST_RECENT = "session.most_recent"
    const val SESSION_RESUME = "session.resume"
    const val SESSION_DELETE = "session.delete"
    const val SESSION_INTERRUPT = "session.interrupt"
    const val SESSION_HISTORY = "session.history"
    const val SESSION_TITLE = "session.title"
    const val SESSION_USAGE = "session.usage"

    const val PROMPT_SUBMIT = "prompt.submit"

    const val TOOLS_LIST = "tools.list"
    const val TOOLS_CONFIGURE = "tools.configure"

    const val MODEL_OPTIONS = "model.options"
    const val MODEL_SAVE_KEY = "model.save_key"
    const val MODEL_DISCONNECT = "model.disconnect"

    const val CONFIG_SET = "config.set"
    const val CONFIG_GET = "config.get"
    const val CONFIG_SHOW = "config.show"

    const val COMMANDS_CATALOG = "commands.catalog"
    const val COMMAND_DISPATCH = "command.dispatch"

    const val SKILLS_MANAGE = "skills.manage"
    const val SKILLS_RELOAD = "skills.reload"
    const val PLUGINS_LIST = "plugins.list"
    const val PLUGINS_MANAGE = "plugins.manage"
    const val CRON_MANAGE = "cron.manage"
    const val AGENTS_LIST = "agents.list"

    const val RELOAD_MCP = "reload.mcp"
    const val RELOAD_ENV = "reload.env"

    const val APPROVAL_RESPOND = "approval.respond"
    const val CLARIFY_RESPOND = "clarify.respond"
    const val SUDO_RESPOND = "sudo.respond"
    const val SECRET_RESPOND = "secret.respond"

    const val INSIGHTS_GET = "insights.get"

    const val SHELL_EXEC = "shell.exec"
    const val PROCESS_STOP = "process.stop"

    const val SESSION_BRANCH = "session.branch"
    const val SESSION_STEER = "session.steer"
    const val CREDITS_VIEW = "credits.view"

    // Delegation v1 (Task Desk)
    const val SESSION_ACTIVE_LIST = "session.active_list"
    const val SESSION_ACTIVATE = "session.activate"
    const val SESSION_CLOSE = "session.close"

    // Changes / rollback (Milestone D slice)
    const val ROLLBACK_LIST = "rollback.list"
    const val ROLLBACK_DIFF = "rollback.diff"
    const val ROLLBACK_RESTORE = "rollback.restore"
    const val SESSION_UNDO = "session.undo"

    // Projects (read-only browser; projects.discover_repos/record_repos need a
    // client-side filesystem crawl the desktop does and mobile has no
    // equivalent for, so they're deliberately not wired)
    const val PROJECTS_TREE = "projects.tree"
    const val PROJECT_FACTS = "project.facts"
    const val PROJECTS_PROJECT_SESSIONS = "projects.project_sessions"

    // Task Desk: delegation control
    const val DELEGATION_STATUS = "delegation.status"
    const val DELEGATION_PAUSE = "delegation.pause"

    // Chat completion. session.save (server-side JSON dump) and toolsets.list/
    // tools.show (near-duplicates of the already-wired tools.list) evaluated
    // and deliberately not wired — see commit history for why.
    const val PDF_ATTACH = "pdf.attach"
    const val LLM_ONESHOT = "llm.oneshot"

    // Billing (read + auto-reload only; charge/step_up deliberately not wired
    // from mobile — see BillingRepository kdoc)
    const val BILLING_STATE = "billing.state"
    const val BILLING_AUTO_RELOAD = "billing.auto_reload"

    // Pet
    const val PET_GALLERY = "pet.gallery"
    const val PET_THUMB = "pet.thumb"
    const val PET_SELECT = "pet.select"
    const val PET_REMOVE = "pet.remove"
    const val PET_RENAME = "pet.rename"
    const val PET_DISABLE = "pet.disable"
    const val PET_SCALE = "pet.scale"
}

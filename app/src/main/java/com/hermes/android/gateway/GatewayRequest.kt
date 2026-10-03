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
    const val SLASH_EXEC = "slash.exec"
    const val PROMPT_BTW = "prompt.btw"
    const val PROMPT_BACKGROUND = "prompt.background"

    const val CLIENT_CAPABILITIES = "client.capabilities"

    const val SKILLS_MANAGE = "skills.manage"
    const val SKILLS_RELOAD = "skills.reload"
    const val PLUGINS_LIST = "plugins.list"
    const val PLUGINS_MANAGE = "plugins.manage"
    const val CRON_MANAGE = "cron.manage"
    const val AGENTS_LIST = "agents.list"

    const val RELOAD_MCP = "reload.mcp"
    const val RELOAD_ENV = "reload.env"

    // MCP servers (the gateway's mirror of the desktop MCP page)
    const val MCP_CATALOG = "mcp.catalog"
    const val MCP_SERVERS_LIST = "mcp.servers.list"
    const val MCP_SERVERS_STATUS = "mcp.servers.status"
    const val MCP_SERVERS_ADD = "mcp.servers.add"
    const val MCP_SERVERS_SET_API_KEY = "mcp.servers.set_api_key"
    const val MCP_SERVERS_TEST = "mcp.servers.test"
    const val MCP_SERVERS_REMOVE = "mcp.servers.remove"
    const val MCP_OAUTH_START = "mcp.servers.oauth.start"
    const val MCP_OAUTH_POLL = "mcp.servers.oauth.poll"
    const val MCP_OAUTH_CANCEL = "mcp.servers.oauth.cancel"

    const val APPROVAL_RESPOND = "approval.respond"

    const val INSIGHTS_GET = "insights.get"

    const val SHELL_EXEC = "shell.exec"
    /** `python -m hermes_cli.main <argv>` on the gateway host (non-interactive commands only). */
    const val CLI_EXEC = "cli.exec"

    // process.stop is a global kill_all — only the console's explicit
    // "emergency stop" may use it. Per-chat cleanup goes through the
    // session-scoped pair below.
    const val PROCESS_STOP = "process.stop"
    const val PROCESS_LIST = "process.list"
    const val PROCESS_KILL = "process.kill"

    const val SESSION_COMPRESS = "session.compress"
    const val SESSION_CONTEXT_BREAKDOWN = "session.context_breakdown"

    const val SESSION_BRANCH = "session.branch"
    const val SESSION_STEER = "session.steer"

    const val MESSAGE_REACT = "message.react"

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

    // Profiles: separate agents (own config, skills, memory, sessions) on one gateway.
    // Any other call picks one with a `profile` param (gateway/ProfileScope.kt).
    const val PROFILES_LIST = "profiles.list"
    const val PROFILES_CREATE = "profiles.create"
    const val PROFILES_DESCRIBE = "profiles.describe"
    const val PROFILES_CONFIGURE = "profiles.configure"

    // Task Desk: delegation control
    const val DELEGATION_STATUS = "delegation.status"
    const val DELEGATION_PAUSE = "delegation.pause"

    // Chat completion. session.save (server-side JSON dump) and toolsets.list/
    // tools.show (near-duplicates of the already-wired tools.list) evaluated
    // and deliberately not wired — see commit history for why.
    const val LLM_ONESHOT = "llm.oneshot"

}

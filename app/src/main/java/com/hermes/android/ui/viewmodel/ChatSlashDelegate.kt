package com.hermes.android.ui.viewmodel

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.UUID

/** The chat's slash commands: Hermes' catalog for the `/` list, and running one. */
internal class ChatSlashDelegate(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ChatUiState>,
    /** Sends the prompt a command stands for (a skill, a `send`). */
    private val sendPrompt: (text: String, sessionId: String) -> Unit,
) {
    private val _suggestions = MutableStateFlow<List<SlashCommandSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SlashCommandSuggestion>> = _suggestions.asStateFlow()

    /** Every command and alias Hermes knows, lowercase with the slash; empty until the catalog lands. */
    @Volatile
    private var knownCommands: Set<String> = emptySet()

    fun loadCatalog() {
        scope.launch {
            try {
                val result = gatewayClient.request(GatewayMethods.COMMANDS_CATALOG) as? JsonObject
                val pairs = (result?.get("pairs") as? JsonArray)?.mapNotNull { row ->
                    val arr = row as? JsonArray ?: return@mapNotNull null
                    val name = (arr.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    val desc = (arr.getOrNull(1) as? JsonPrimitive)?.contentOrNull ?: ""
                    SlashCommandSuggestion(command = name, description = desc)
                } ?: emptyList()
                if (pairs.isEmpty()) return@launch
                // `canon` holds every name and alias; skills are only in `pairs`.
                val canon = (result?.get("canon") as? JsonObject)?.keys.orEmpty()
                knownCommands = (canon + pairs.map { it.command }).map { it.lowercase() }.toSet()
                _suggestions.value = pairs
                    .filterNot { it.command.lowercase() in HIDDEN_SLASH_COMMANDS }
                    // Stable: everything after the useful ones keeps the catalog's order.
                    .sortedBy { FIRST_SLASH_COMMANDS.indexOf(it.command.lowercase()).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
                Timber.i("[Chat] Loaded ${pairs.size} slash commands from catalog")
            } catch (e: Exception) {
                Timber.w(e, "[Chat] commands.catalog failed — slash command autocomplete will be empty until next retry")
            }
        }
    }

    /**
     * Only a name Hermes knows makes a slash command: `/sdcard/x.txt رو بخون` is a message.
     * Before the catalog arrives every `/…` is still a command, as it always was.
     */
    fun isCommand(text: String): Boolean =
        isSlashCommandText(text, knownCommands)

    /**
     * Runs a slash command the way Hermes' own TUI does (`ui-tui/src/app/createSlashHandler.ts`):
     * `slash.exec` first, because `command.dispatch` alone only knows quick/plugin/skill commands
     * and a handful of built-ins, so `/help`, `/model`, `/status`… came back as error 4018.
     * `command.dispatch` is only the fallback for the refusals where `slash.exec` says the
     * command is not its to run.
     */
    fun run(text: String, sessionId: String, depth: Int = 0) {
        if (depth > 5) {
            state.update { it.copy(errorEvent = ErrorEvent.Error("Command alias loop"), isSending = false) }
            return
        }
        scope.launch {
            try {
                val withoutSlash = text.removePrefix("/").trim()
                val parts = withoutSlash.split(" ", limit = 2)
                val name = parts[0]
                val arg = if (parts.size > 1) parts[1] else ""
                // Hermes' own clients run these two themselves (ui-tui slash/commands/session.ts):
                // the slash worker has no side agent to hand them to.
                SIDE_AGENT_COMMANDS[name.lowercase()]?.let { method ->
                    startSideAgent(method, name, arg, sessionId)
                    return@launch
                }
                val result = try {
                    gatewayClient.request(
                        method = GatewayMethods.SLASH_EXEC,
                        params = buildJsonObject {
                            put("command", withoutSlash)
                            put("session_id", sessionId)
                        },
                    )
                } catch (e: GatewayException) {
                    if (!slashExecDisowns(e)) throw e
                    gatewayClient.request(
                        method = GatewayMethods.COMMAND_DISPATCH,
                        params = buildJsonObject {
                            put("name", name)
                            put("arg", arg)
                            put("session_id", sessionId)
                        },
                    )
                }
                applyResult(result, name, arg, sessionId, depth)
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Slash command failed")
                state.update { it.copy(
                    errorEvent = ErrorEvent.Error("Command failed: ${e.message}"),
                    isSending = false,
                ) }
            }
        }
    }

    private suspend fun startSideAgent(method: String, name: String, arg: String, sessionId: String) {
        if (arg.isBlank()) {
            postStatus(if (method == GatewayMethods.PROMPT_BTW) "/btw <question>" else "/$name <prompt>")
            return
        }
        val result = gatewayClient.request(
            method = method,
            params = buildJsonObject {
                put("session_id", sessionId)
                put("text", arg)
            },
        ) as? JsonObject
        val taskId = (result?.get("task_id") as? JsonPrimitive)?.contentOrNull
        postStatus(
            when {
                taskId == null -> "/$name: no task started"
                method == GatewayMethods.PROMPT_BTW -> "btw $taskId — answering from a snapshot of this chat"
                else -> "bg $taskId started"
            },
        )
    }

    private fun applyResult(
        result: kotlinx.serialization.json.JsonElement?,
        name: String,
        arg: String,
        sessionId: String,
        depth: Int,
    ) {
        val obj = result as? JsonObject
        fun str(key: String) = (obj?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
        val notice = str("notice")?.takeIf { it.isNotBlank() }
        val message = str("message")
        when (str("type")) {
            "alias" -> {
                val target = str("target")
                if (!target.isNullOrBlank()) {
                    run(if (arg.isNotBlank()) "/$target $arg" else "/$target", sessionId, depth + 1)
                    return
                }
            }
            "exec", "plugin" -> {
                postStatus(str("output")?.takeIf { it.isNotBlank() } ?: "(no output)")
                return
            }
            "skill", "send" -> {
                notice?.let { postStatus(it) }
                if (!message.isNullOrBlank()) {
                    sendPrompt(message, sessionId)
                } else {
                    postStatus("/$name: empty message", isError = true)
                }
                return
            }
            "prefill" -> {
                notice?.let { postStatus(it) }
                state.update { it.copy(inputText = message ?: it.inputText, isSending = false) }
                return
            }
        }
        val output = extractCommandOutput(result)?.trim().orEmpty().ifBlank { "/$name: no output" }
        val warning = str("warning")?.takeIf { it.isNotBlank() }
        postStatus(if (warning != null) "warning: $warning\n$output" else output)
    }

    private fun postStatus(text: String, isError: Boolean = false) {
        state.update { it.copy(
            messages = it.messages + ChatMessage.Status(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                text = text.trim(),
                isError = isError,
            ),
            isSending = false,
        ) }
    }

    private fun extractCommandOutput(result: kotlinx.serialization.json.JsonElement?): String? {
        if (result == null) return null
        (result as? JsonPrimitive)?.let { if (it.isString) return it.content }
        val obj = result as? JsonObject ?: return null
        for (key in listOf("output", "text", "message", "markdown", "result", "detail")) {
            val v = obj[key]
            if (v is JsonPrimitive && v.isString && v.content.isNotBlank()) return v.content
        }
        (obj["lines"] as? JsonArray)?.let { arr ->
            val joined = arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("\n")
            if (joined.isNotBlank()) return joined
        }
        return null
    }

    companion object {
        /**
         * `slash.exec` answers 4018 with exactly these texts when the command is not its own.
         * Every other 4018 comes from a `command.dispatch` handler it already forwarded to
         * (/retry, /undo, /compress…): dispatching again would run a mutating command twice.
         */
        private val SLASH_EXEC_NOT_MINE = Regex(
            "^skill command: use command\\.dispatch for /|use command\\.dispatch for /snapshot restore",
        )

        internal fun slashExecDisowns(e: GatewayException): Boolean =
            e.code == 4018 && SLASH_EXEC_NOT_MINE.containsMatchIn(e.rpcMessage.orEmpty())

        private val SIDE_AGENT_COMMANDS = mapOf(
            "btw" to GatewayMethods.PROMPT_BTW,
            "bg" to GatewayMethods.PROMPT_BACKGROUND,
            "background" to GatewayMethods.PROMPT_BACKGROUND,
        )

        internal fun isSlashCommandText(text: String, known: Set<String>): Boolean {
            if (!text.startsWith("/")) return false
            if (known.isEmpty()) return true
            val name = "/" + text.removePrefix("/").trimStart().substringBefore(' ').substringBefore('\n').lowercase()
            return name in known
        }

        /** Shown first in the `/` list: what the app has no button for and people reach for. */
        internal val FIRST_SLASH_COMMANDS = listOf(
            "/plan", "/goal", "/subgoal", "/btw", "/bg", "/loop", "/review", "/learn", "/status", "/help",
        )

        /**
         * Left out of the `/` list (typing them in full still runs them): the app has a
         * button or screen for them, or they only mean something in a terminal.
         */
        internal val HIDDEN_SLASH_COMMANDS = setOf(
            "/new", "/clear", "/retry", "/undo", "/branch", "/model", "/approve", "/deny",
            "/sessions", "/resume", "/stop", "/image",
            "/context", "/queue", "/compress", "/title", "/save", "/history",
            // Settings, Tools, Task Desk, Changes and the composer already do these.
            "/steer", "/reasoning", "/personality", "/approvals", "/yolo", "/config",
            "/reload", "/reload-mcp", "/tools", "/toolsets", "/skills", "/reload-skills",
            "/plugins", "/cron", "/platforms", "/agents", "/insights", "/usage",
            "/rollback", "/skin",
            "/redraw", "/prompt", "/palette", "/statusbar", "/battery", "/indicator",
            "/copy", "/paste", "/quit", "/wake", "/voice",
        )
    }
}

package com.hermes.android.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * MCP servers, as on Hermes desktop's MCP page but over the gateway's mcp.* RPCs (the built-in
 * Linux has no dashboard server): configured servers with a live probe each, the curated
 * catalog, adding by URL or command, API keys, OAuth sign-in and removal.
 */
@HiltViewModel
class McpViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(McpUiState())
    val uiState: StateFlow<McpUiState> = _uiState.asStateFlow()

    private var signInJob: Job? = null
    private var probeSeq = 0L

    init {
        load()
    }

    /** [force] (the refresh button) re-probes every server, as the desktop's manual refresh does. */
    fun load(force: Boolean = false) {
        viewModelScope.launch {
            if (force) {
                probeCache.clear()
                _uiState.update { it.copy(probes = emptyMap()) }
            }
            _uiState.update { it.copy(isLoading = it.servers.isEmpty() && it.catalog.isEmpty()) }
            try {
                val runtime = runCatching { rpc(GatewayMethods.MCP_SERVERS_STATUS) }.getOrNull()
                    ?.array("servers")?.associateBy { it.str("name") }.orEmpty()
                val servers = rpc(GatewayMethods.MCP_SERVERS_LIST).array("servers")
                    .map { parseServer(it, runtime[it.str("name")]) }
                val catalog = runCatching {
                    rpc(GatewayMethods.MCP_CATALOG).array("servers").map(::parseCatalogEntry)
                }.getOrElse {
                    Timber.w(it, "[MCP] Catalog unavailable")
                    emptyList()
                }
                // A probe stays valid only for the exact config it tested.
                val kept = _uiState.value.probes.filter { (name, probe) ->
                    servers.any { it.name == name && it.fingerprint == probe.fingerprint }
                }
                _uiState.update { it.copy(servers = servers, catalog = catalog, probes = kept, isLoading = false) }
                // Each probe is a real connect (a stdio server gets spawned), so a result is
                // reused for five minutes, like the desktop's probe cache.
                servers.filter { it.enabled && it.name !in kept }.forEach { server ->
                    val cached = probeCache[server.name]
                    if (cached != null && cached.second.fingerprint == server.fingerprint &&
                        System.currentTimeMillis() - cached.first < PROBE_TTL_MS
                    ) {
                        setProbe(server.name, cached.second)
                    } else {
                        launch { probe(server) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[MCP] Failed to load")
                _uiState.update { it.copy(isLoading = false, notice = failed(e)) }
            }
        }
    }

    fun test(name: String) {
        val server = _uiState.value.servers.firstOrNull { it.name == name } ?: return
        viewModelScope.launch { probe(server) }
    }

    private suspend fun probe(server: McpServer) {
        val pending = McpProbe(probing = true, fingerprint = server.fingerprint, seq = ++probeSeq)
        setProbe(server.name, pending)
        val result = try {
            val r = rpc(GatewayMethods.MCP_SERVERS_TEST, params("name" to server.name))
            McpProbe(
                ok = r.bool("ok"),
                tools = r.array("tools").map { it.str("name") },
                prompts = r.int("prompts"),
                resources = r.int("resources"),
                error = r.str("error").ifBlank { null },
                fingerprint = server.fingerprint,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            McpProbe(error = e.message ?: "Test failed", fingerprint = server.fingerprint)
        }
        // A newer probe, or a change to this server, replaced this one while it ran: its result
        // describes a config that's gone and must not overwrite the current status.
        var current = false
        _uiState.update { state ->
            current = state.probes[server.name] === pending
            if (current) state.copy(probes = state.probes + (server.name to result)) else state
        }
        if (current) probeCache[server.name] = System.currentTimeMillis() to result
    }

    /** Forgets [name]'s status so the next load probes it afresh: a new key can leave its config looking the same. */
    private fun invalidate(name: String) {
        probeCache.remove(name)
        _uiState.update { it.copy(probes = it.probes - name) }
    }

    private fun setProbe(name: String, probe: McpProbe) {
        _uiState.update { it.copy(probes = it.probes + (name to probe)) }
    }

    /** A server by URL (http) or by command (stdio); a bearer token goes to .env, not config.yaml. */
    fun addServer(name: String, url: String, command: String, args: String, bearerToken: String) = mutate(
        McpNotice("Added ${name.trim()}", "${name.trim()} اضافه شد"),
        server = name.trim(),
    ) {
        val config = JsonObject(
            if (url.isNotBlank()) {
                mapOf("url" to JsonPrimitive(url.trim()))
            } else {
                mapOf(
                    "command" to JsonPrimitive(command.trim()),
                    "args" to JsonArray(splitArgs(args).map { JsonPrimitive(it) }),
                )
            },
        )
        rpc(
            GatewayMethods.MCP_SERVERS_ADD,
            params(
                "name" to name.trim(),
                "config" to config,
                "bearer_token" to bearerToken.trim().takeIf { url.isNotBlank() && it.isNotEmpty() },
            ),
        )
    }

    /** A catalog preset, then each key it needs, as Hermes desktop's setup card does. */
    fun addFromCatalog(entry: McpCatalogEntry, keys: Map<String, String>) = mutate(
        McpNotice("Added ${entry.name}", "${entry.name} اضافه شد"),
        server = entry.name,
    ) {
        rpc(GatewayMethods.MCP_SERVERS_ADD, params("name" to entry.name, "preset" to entry.name))
        keys.filterValues { it.isNotBlank() }.forEach { (envVar, value) ->
            rpc(
                GatewayMethods.MCP_SERVERS_SET_API_KEY,
                params("name" to entry.name, "env_var" to envVar, "value" to value.trim()),
            )
        }
    }

    /** Stores [value] in .env; a blank [envVar] lets Hermes pick MCP_<NAME>_API_KEY. */
    fun setApiKey(name: String, value: String, envVar: String) = mutate(
        McpNotice("Key saved for $name", "کلید $name ذخیره شد"),
        server = name,
    ) {
        rpc(
            GatewayMethods.MCP_SERVERS_SET_API_KEY,
            params("name" to name, "value" to value.trim(), "env_var" to envVar.trim().ifEmpty { null }),
        )
    }

    fun remove(name: String) = mutate(McpNotice("Removed $name", "$name حذف شد"), server = name) {
        rpc(GatewayMethods.MCP_SERVERS_REMOVE, params("name" to name))
    }

    /**
     * Runs a config change to [server], then reloads MCP so live chats get the new tools, and
     * re-reads the list. A failed reload doesn't undo the change, so it isn't reported as one.
     */
    private fun mutate(done: McpNotice, server: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                block()
                val notice = try {
                    reloadMcp()
                    done
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "[MCP] reload.mcp failed")
                    val reason = reasonOf(e)
                    McpNotice(
                        "${done.en}, but reloading MCP failed: $reason",
                        "${done.fa}، ولی بارگذاری دوبارهٔ MCP انجام نشد: $reason",
                    )
                }
                _uiState.update { it.copy(notice = notice) }
            } catch (e: Exception) {
                Timber.e(e, "[MCP] Change failed")
                _uiState.update { it.copy(notice = failed(e)) }
            } finally {
                invalidate(server)
                _uiState.update { it.copy(isBusy = false) }
                load()
            }
        }
    }

    private suspend fun reloadMcp() {
        rpc(GatewayMethods.RELOAD_MCP, params("confirm" to true))
    }

    /**
     * OAuth sign-in (lib/mcp-dashboard-oauth.ts): the gateway runs on this phone, so its own
     * loopback receiver takes the browser's redirect; the app opens the URL and polls.
     */
    fun signIn(name: String) {
        if (signInJob?.isActive == true) return
        signInJob = viewModelScope.launch {
            _uiState.update { it.copy(signingIn = name) }
            var flowId: String? = null
            var approved = false
            try {
                val started = rpcOk(GatewayMethods.MCP_OAUTH_START, params("name" to name))
                flowId = started.str("session_id").ifBlank { null }
                val authUrl = started.str("auth_url")
                if (flowId == null || authUrl.isBlank()) {
                    error("OAuth server did not provide an authorization URL and session")
                }
                // Open only a flow that returns to the gateway's own loopback, as the desktop does.
                val redirect = Uri.parse(Uri.parse(authUrl).getQueryParameter("redirect_uri").orEmpty())
                if (redirect.scheme != "http" || redirect.host != "127.0.0.1" || redirect.port <= 0 ||
                    redirect.path != "/callback"
                ) {
                    error("OAuth server did not provide a local loopback callback URL")
                }
                _uiState.update { it.copy(openUrl = authUrl) }
                val tools = awaitApproval(name, flowId)
                approved = true
                probeCache.clear()
                _uiState.update { it.copy(
                    probes = it.probes - name,
                    notice = McpNotice("Signed in to $name — $tools tools", "ورود به $name انجام شد — $tools ابزار"),
                ) }
                runCatching { reloadMcp() }.onFailure { Timber.w(it, "[MCP] reload after sign-in failed") }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[MCP] Sign-in to $name failed")
                _uiState.update { it.copy(notice = failed(e)) }
            } finally {
                val flow = flowId
                if (!approved && flow != null) {
                    withContext(NonCancellable) {
                        runCatching {
                            rpc(GatewayMethods.MCP_OAUTH_CANCEL, params("name" to name, "session_id" to flow))
                        }
                    }
                }
                _uiState.update { it.copy(signingIn = null, openUrl = null) }
            }
        }
    }

    /** Polls each second until approved (the tool count) or failed; six minutes at most. */
    private suspend fun awaitApproval(name: String, flowId: String): Int {
        val deadline = System.currentTimeMillis() + SIGN_IN_TIMEOUT_MS
        var failures = 0
        while (true) {
            if (System.currentTimeMillis() >= deadline) error("Timed out waiting for sign-in")
            val current = try {
                rpcOk(GatewayMethods.MCP_OAUTH_POLL, params("name" to name, "session_id" to flowId))
                    .also { failures = 0 }
            } catch (e: GatewayException) {
                if (++failures >= 3) throw e
                delay(1000)
                continue
            }
            when (current.str("status")) {
                "approved" -> return (current["tools"] as? JsonArray)?.size ?: 0
                "error" -> error(current.str("error_message").ifBlank { "OAuth authorization failed" })
            }
            delay(1000)
        }
    }

    fun cancelSignIn() {
        signInJob?.cancel()
    }

    fun urlOpened() {
        _uiState.update { it.copy(openUrl = null) }
    }

    fun browserMissing() {
        cancelSignIn()
        _uiState.update { it.copy(notice = McpNotice("No browser to sign in with", "مرورگری برای ورود پیدا نشد")) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun failed(e: Exception) = reasonOf(e).let { McpNotice("Failed: $it", "انجام نشد: $it") }

    private fun reasonOf(e: Exception) = e.message ?: e.javaClass.simpleName

    // ── RPC and parsing ──────────────────────────────────────────────────

    private suspend fun rpc(method: String, params: Map<String, JsonElement> = emptyMap()): JsonObject {
        val result = gatewayClient.request(method, params) as? JsonObject ?: JsonObject(emptyMap())
        // Some gateway builds wrap the body in a second `result` envelope (desktop mcp-setup.tsx).
        return (result["result"] as? JsonObject)?.takeIf { result.size == 1 } ?: result
    }

    /** OAuth replies carry `ok: false` with the reason instead of an RPC error. */
    private suspend fun rpcOk(method: String, params: Map<String, JsonElement>): JsonObject {
        val result = rpc(method, params)
        if ((result["ok"] as? JsonPrimitive)?.booleanOrNull == false) {
            error(result.str("error_message").ifBlank { result.str("error") }.ifBlank { "MCP OAuth request failed" })
        }
        return result
    }

    private fun params(vararg pairs: Pair<String, Any?>): Map<String, JsonElement> = pairs.mapNotNull { (key, value) ->
        when (value) {
            null -> null
            is JsonElement -> key to value
            is Boolean -> key to JsonPrimitive(value)
            is Number -> key to JsonPrimitive(value)
            else -> key to JsonPrimitive(value.toString())
        }
    }.toMap()

    private fun parseServer(o: JsonObject, runtime: JsonObject?): McpServer {
        val command = (listOf(o.str("command")) + o.strings("args")).filter { it.isNotBlank() }.joinToString(" ")
        val url = o.str("url").ifBlank { null }
        val env = o.strings("env")
        val auth = o.str("auth").ifBlank { null }
        return McpServer(
            name = o.str("name"),
            url = url,
            command = command.ifBlank { null },
            env = env,
            auth = auth,
            enabled = (o["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
            plugin = if (o.str("source") == "plugin") o.str("plugin").ifBlank { "plugin" } else null,
            runtimeStatus = runtime?.str("status")?.ifBlank { null },
            runtimeTools = runtime?.int("tools"),
            fingerprint = listOf(o.str("name"), url, command, env, auth).toString(),
        )
    }

    private fun parseCatalogEntry(o: JsonObject) = McpCatalogEntry(
        name = o.str("name"),
        description = o.str("description"),
        requires = o.strings("requires"),
        installed = o.bool("installed"),
        transport = o.str("transport"),
    )

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull ?: 0
    private fun JsonObject.array(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    private companion object {
        const val PROBE_TTL_MS = 5 * 60_000L
        const val SIGN_IN_TIMEOUT_MS = 6 * 60_000L

        /** Probe results outlive the screen, per server and valid for its config fingerprint (desktop mcp-probe-cache.ts). */
        val probeCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, McpProbe>>()
    }
}

data class McpUiState(
    val servers: List<McpServer> = emptyList(),
    val catalog: List<McpCatalogEntry> = emptyList(),
    val probes: Map<String, McpProbe> = emptyMap(),
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val signingIn: String? = null,
    val openUrl: String? = null,
    val notice: McpNotice? = null,
)

data class McpNotice(val en: String, val fa: String)

data class McpServer(
    val name: String,
    val url: String?,
    /** Command and args, for display. */
    val command: String?,
    val env: List<String>,
    val auth: String?,
    val enabled: Boolean,
    /** The plugin that provides this server; its config isn't editable here. */
    val plugin: String?,
    val runtimeStatus: String?,
    val runtimeTools: Int?,
    val fingerprint: String,
)

data class McpCatalogEntry(
    val name: String,
    val description: String,
    val requires: List<String>,
    val installed: Boolean,
    val transport: String,
)

data class McpProbe(
    val probing: Boolean = false,
    val ok: Boolean = false,
    val tools: List<String> = emptyList(),
    val prompts: Int = 0,
    val resources: Int = 0,
    val error: String? = null,
    val fingerprint: String = "",
    /** Tells two runs apart, so a stale result can't pass for the current one. */
    val seq: Long = 0,
)

enum class McpStatus { OK, ERROR, NEEDS_AUTH, PROBING, OFF, UNKNOWN }

private val NEEDS_AUTH = Regex("""\b(401|unauthorized|forbidden|invalid[_ ]?token|authentication|oauth)\b""", RegexOption.IGNORE_CASE)

/** desktop mcp-status.ts statusOf. */
fun McpServer.statusWith(probe: McpProbe?): McpStatus = when {
    !enabled -> McpStatus.OFF
    probe == null -> McpStatus.UNKNOWN
    probe.probing -> McpStatus.PROBING
    probe.ok -> McpStatus.OK
    NEEDS_AUTH.containsMatchIn(probe.error.orEmpty()) -> McpStatus.NEEDS_AUTH
    else -> McpStatus.ERROR
}

/** desktop mcp-status.ts canAuthenticate. */
fun McpServer.canSignIn(status: McpStatus): Boolean =
    url != null && auth != "header" && plugin == null && if (auth == "oauth") {
        status == McpStatus.NEEDS_AUTH || status == McpStatus.ERROR
    } else {
        auth == null && status == McpStatus.NEEDS_AUTH
    }

/** Shell-style words for a command's arguments: whitespace splits, "…" or '…' keeps spaces. */
internal fun splitArgs(line: String): List<String> {
    val args = mutableListOf<String>()
    val word = StringBuilder()
    var quote: Char? = null
    var inWord = false
    for (c in line) {
        when {
            quote != null -> if (c == quote) quote = null else word.append(c)
            c == '"' || c == '\'' -> { quote = c; inWord = true }
            c.isWhitespace() -> if (inWord) { args += word.toString(); word.clear(); inWord = false }
            else -> { word.append(c); inWord = true }
        }
    }
    if (inWord) args += word.toString()
    return args
}

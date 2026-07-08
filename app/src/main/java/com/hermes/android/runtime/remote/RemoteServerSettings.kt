package com.hermes.android.runtime.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-entered connection settings for the remote Hermes server.
 *
 * ## Storage
 * Persisted in SharedPreferences (same pattern as TermuxBridge's session
 * token). The token is entered by the user in the Runtime Setup screen and
 * must match the `HERMES_DASHBOARD_SESSION_TOKEN` the server was started with.
 *
 * ## URL format
 * [serverUrl] is the base URL of the reverse proxy in front of
 * `hermes dashboard`, e.g. `wss://example.com:2083`. The full WebSocket
 * URL is derived as `<serverUrl>/api/ws?token=<token>` — the `/api/ws`
 * path is Hermes' FastAPI WebSocket endpoint and never changes, so the
 * user only enters host/port.
 *
 * Reference: server-side setup — `hermes dashboard --host 127.0.0.1 --port 9119`
 * behind a TLS reverse proxy (Caddy) on the public port.
 */
@Singleton
class RemoteServerSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())

    /** Current config, observable so the setup UI stays in sync. */
    val config: StateFlow<RemoteServerConfig> = _config.asStateFlow()

    /** True when both URL and token have been entered. */
    val isConfigured: Boolean get() = _config.value.isComplete

    fun save(serverUrl: String, token: String) {
        val normalized = normalizeUrl(serverUrl)
        prefs.edit()
            .putString(KEY_SERVER_URL, normalized)
            .putString(KEY_TOKEN, token.trim())
            .apply()
        _config.value = RemoteServerConfig(normalized, token.trim())
    }

    /**
     * Full WebSocket URL for [com.hermes.android.gateway.GatewayClient.connect],
     * or null when not configured.
     */
    fun webSocketUrl(): String? {
        val c = _config.value
        if (!c.isComplete) return null
        return "${c.serverUrl}$WS_PATH?token=${c.token}"
    }

    private fun load(): RemoteServerConfig = RemoteServerConfig(
        serverUrl = prefs.getString(KEY_SERVER_URL, "") ?: "",
        token = prefs.getString(KEY_TOKEN, "") ?: "",
    )

    companion object {
        private const val PREFS_NAME = "hermes_remote_server"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "token"
        private const val WS_PATH = "/api/ws"

        /**
         * Normalize user input to a `wss://host[:port]` base URL:
         * - trims whitespace and trailing slashes
         * - strips an accidentally-pasted `/api/ws...` suffix
         * - adds `wss://` when no scheme was given
         * - maps `https`/`http` to `wss`/`ws`
         */
        fun normalizeUrl(input: String): String {
            var url = input.trim().trimEnd('/')
            // User pasted the full WS URL — keep only the base.
            url = url.substringBefore(WS_PATH)
            url = when {
                url.startsWith("wss://") || url.startsWith("ws://") -> url
                url.startsWith("https://") -> "wss://" + url.removePrefix("https://")
                url.startsWith("http://") -> "ws://" + url.removePrefix("http://")
                url.isEmpty() -> url
                else -> "wss://$url"
            }
            return url.trimEnd('/')
        }
    }
}

/** Immutable snapshot of the remote server connection settings. */
data class RemoteServerConfig(
    val serverUrl: String,
    val token: String,
) {
    val isComplete: Boolean get() = serverUrl.isNotBlank() && token.isNotBlank()
}

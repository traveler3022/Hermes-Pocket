package com.hermes.android.runtime.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the remote-server connection settings (URL + token) and exposes
 * them as an observable [StateFlow] for the UI.
 *
 * Backed by [android.content.SharedPreferences], matching the persistence
 * pattern used elsewhere in the app (e.g. ConfigViewModel, TaskRegistry).
 * Hilt constructs it automatically via the [Inject] constructor — no module
 * binding required.
 */
@Singleton
class RemoteServerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(
        RemoteServerConfig(
            serverUrl = prefs.getString(KEY_SERVER_URL, "").orEmpty(),
            token = prefs.getString(KEY_TOKEN, "").orEmpty(),
        )
    )

    /** Current remote-server connection settings. */
    val config: StateFlow<RemoteServerConfig> = _config.asStateFlow()

    /** Persist a new server URL + token and notify observers. */
    suspend fun save(serverUrl: String, token: String) {
        prefs.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_TOKEN, token)
            .apply()
        _config.value = RemoteServerConfig(serverUrl = serverUrl, token = token)
    }

    private companion object {
        const val PREFS_NAME = "hermes_remote_server"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_TOKEN = "token"
    }
}

package com.hermes.android.runtime

import com.hermes.android.runtime.remote.RemoteServerSettings
import com.hermes.android.runtime.remote.remoteHttpBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which Hermes the app talks to right now, as a stable key: the built-in Linux, Termux, or one
 * remote server (two spellings of its address are one key). Settings that belong to one
 * server, such as the Hermes profile picked on it, are kept under this key.
 */
@Singleton
class ServerIdentity @Inject constructor(
    private val selection: RuntimeSelection,
    private val remote: RemoteServerSettings,
) {
    fun current(): String = keyOf(selection.selected.value, remote.config.value.serverUrl)

    val changes: Flow<String> = combine(selection.selected, remote.config) { type, config -> keyOf(type, config.serverUrl) }
        .distinctUntilChanged()

    private fun keyOf(type: RuntimeType, url: String): String =
        if (type == RuntimeType.REMOTE) "remote:" + (remoteHttpBase(url) ?: url.trim()) else type.name.lowercase()
}

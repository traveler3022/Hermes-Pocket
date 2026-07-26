package com.hermes.android.runtime.remote

/**
 * Connection settings for the remote-server runtime.
 *
 * The remote runtime hosts the Hermes agent on an external machine; the app
 * connects to its gateway over a WebSocket. These settings capture everything
 * needed to establish that connection.
 *
 * @param serverUrl Base URL of the remote Hermes gateway (e.g. "https://host:9119").
 * @param token     Bearer token appended as the `?token=` query param on the WS URL.
 */
data class RemoteServerConfig(
    val serverUrl: String = "",
    val token: String = "",
)

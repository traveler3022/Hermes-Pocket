package com.hermes.android.data

import com.hermes.android.domain.tools.McpServerSwitch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** The gateway has no RPC for this; the dashboard's `PUT /api/mcp/servers/{name}/enabled` does it. */
@Singleton
class McpServerSwitchRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : McpServerSwitch {

    override suspend fun setEnabled(name: String, enabled: Boolean) {
        dashboard.call(
            "PUT",
            "/api/mcp/servers/${URLEncoder.encode(name, "UTF-8").replace("+", "%20")}/enabled",
            buildJsonObject { put("enabled", enabled) },
        )
    }
}

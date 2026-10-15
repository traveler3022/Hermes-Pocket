package com.hermes.android.domain.tools

/** Turns a configured MCP server on or off; it stays in config either way (dashboard PUT …/enabled). */
interface McpServerSwitch {
    suspend fun setEnabled(name: String, enabled: Boolean)
}

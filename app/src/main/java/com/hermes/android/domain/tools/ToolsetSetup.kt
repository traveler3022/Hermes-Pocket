package com.hermes.android.domain.tools

/** A toolset's backends: which service does the work (e.g. which web search) and its keys. */
data class ToolsetConfig(
    val name: String,
    val providers: List<ToolsetProvider>,
    val activeProvider: String?,
)

data class ToolsetProvider(
    val name: String,
    val badge: String?,
    val tag: String?,
    val envVars: List<ToolsetEnvVar>,
    val isActive: Boolean,
    // ready / needs_keys / needs_auth …, as the server words it.
    val status: String?,
    val requiresNousAuth: Boolean,
)

data class ToolsetEnvVar(
    val key: String,
    val prompt: String?,
    val url: String?,
    val isSet: Boolean,
)

/** Toolset backends and their keys, as the dashboard's Tools page sets them. */
interface ToolsetSetup {
    suspend fun config(toolset: String): ToolsetConfig

    /** Makes [provider] the toolset's backend; true when it still needs a Nous Portal sign-in. */
    suspend fun selectProvider(toolset: String, provider: String): Boolean

    suspend fun saveKey(toolset: String, key: String, value: String)

    suspend fun clearKey(key: String)
}

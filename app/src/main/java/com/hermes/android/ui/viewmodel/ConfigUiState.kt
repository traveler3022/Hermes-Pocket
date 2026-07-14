package com.hermes.android.ui.viewmodel

/**
 * UI-facing state models for the Configuration screen.
 *
 * Per Phase 1.5 Rule 1 (Strict Layer Dependency):
 *   ui.screen → ui.viewmodel ONLY
 *   ui.screen must NOT import from gateway or runtime packages
 *
 * The ViewModel converts GatewayEvent → [ConfigUiState] and exposes it.
 */

data class ConfigUiState(
    val configYaml: String = "",
    val isLoadingConfig: Boolean = false,
    val activeProvider: String? = null,
    val activeModel: String? = null,
    val availableModels: List<ModelOption> = emptyList(),
    val isLoadingModels: Boolean = false,
    val availableTools: List<ToolOption> = emptyList(),
    val isLoadingTools: Boolean = false,
    val memoryUserMd: String = "",
    val memoryMd: String = "",
    val isLoadingMemory: Boolean = false,
    val errorMessage: String? = null,
    // Provider management
    val providers: List<HermesProviderConfig> = emptyList(),
    val isLoadingProviders: Boolean = false,
    val credentialPool: Map<String, List<CredentialEntry>> = emptyMap(),
    val isLoadingCredentials: Boolean = false,
    val expandedProviderSlug: String? = null,
    // Agent behavior config — real Hermes config.yaml keys, verified against
    // the official docs (see setApprovalMode/setReasoning/etc. comments).
    // approvals.mode replaces the old fictional "yolo" boolean — "off" is
    // the documented equivalent of --yolo. TUI-only cosmetics the old screen
    // exposed (skin/compact/mouse/indicator/statusbar/details_mode) are gone:
    // they only restyle the terminal UI on the server, which an Android
    // client never sees.
    val approvalMode: String = "manual", // approvals.mode: manual | smart | off
    val reasoning: String = "medium", // agent.reasoning_effort: none|minimal|low|medium|high|xhigh|max
    val personality: String = "", // display.personality — system-prompt overlay name
    // SOUL.md — the agent's persistent identity/voice (first slot in the
    // system prompt). Replaces the old free-text "prompt" field, which sent
    // a config.set key ("prompt") that doesn't exist anywhere in Hermes.
    val soulMd: String = "",
    val isLoadingSoul: Boolean = false,
    // ~/.hermes/.env raw contents, editable.
    val envText: String = "",
    val isLoadingEnv: Boolean = false,
    // mcp_servers section of config.yaml, as pretty-printed JSON, editable.
    val mcpServersText: String = "",
    val isLoadingMcp: Boolean = false,
    // Client-side avatar image (local file path, null = default icon).
    val avatarUri: String? = null,
    // ── Control Center stats (design E) ──
    // First balance line from credits.view, or null (not logged in / failed).
    val creditsSummary: String? = null,
    // 30-day aggregate from insights.get (reuses SessionsViewModel's type).
    val insights: InsightsData? = null,
    // ── Advanced: command console + gateway log (design I) ──
    val consoleEntries: List<ConsoleEntry> = emptyList(),
    val isConsoleRunning: Boolean = false,
    val gatewayLog: List<String> = emptyList(),
)

/** One command + its result in the Advanced screen's console. */
data class ConsoleEntry(
    val command: String,
    val output: String,
    val isError: Boolean,
)

data class ModelOption(
    val provider: String,
    val modelId: String,
    val name: String,
    val requiresApiKey: Boolean,
)

data class ToolOption(
    val name: String,
    val description: String,
    val enabled: Boolean,
    val toolset: String?,
    val toolCount: Int = 0,
    val tools: List<String> = emptyList(),
)

// ── Provider / Credential models ────────────────────────────────────────
// Maps 1:1 to Hermes Agent config.yaml structure:
//   providers.<slug>.base_url
//   providers.<slug>.default_model
// The provider's key lives in auth.json → credential_pool.<slug>[]
// (one entry — the app registers a single key per provider)

data class HermesProviderConfig(
    val slug: String,
    val baseUrl: String,
    val defaultModel: String = "",
    val credentials: List<CredentialEntry> = emptyList(),
    val isPrimary: Boolean = false,
)

data class CredentialEntry(
    val index: Int,                           // 1-based (matches REST API)
    val id: String?,
    val label: String?,
    val authType: String?,                    // api_key | oauth
    val tokenPreview: String,                 // redacted
    val priority: Int,
    val lastStatus: String?,                  // ok | fail | null
    val requestCount: Int,
)

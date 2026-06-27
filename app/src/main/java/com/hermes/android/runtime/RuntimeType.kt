package com.hermes.android.runtime

/**
 * Identifies the runtime implementation hosting the Hermes Python agent.
 *
 * The enum value names identify specific runtime implementations
 * (e.g. TERMUX, EMBEDDED_PYTHON). This is intentional — the enum's
 * purpose is to distinguish between concrete implementations, just as
 * `EMBEDDED_PYTHON` does. Naming the value `MIGRATION_ADAPTER` instead
 * of `TERMUX` would obscure which concrete runtime is active and break
 * the enum's semantic contract.
 *
 * Reference:
 * - ADR-001: Termux wrapper approved for migration phase only
 * - ADR-009: Production builds must NOT require Termux interaction
 *
 * The long-term goal is [EMBEDDED_PYTHON] (or eventually a native Kotlin port),
 * but [TERMUX] is the migration adapter for now.
 */
enum class RuntimeType {
    /** Migration adapter — runs Hermes inside Termux (ADR-001, ADR-007). */
    TERMUX,

    /** Future production runtime — Python bundled in APK (ADR-009). */
    EMBEDDED_PYTHON,

    /** Hypothetical far-future: native Kotlin rewrite of the agent loop. */
    NATIVE_KOTLIN,
}

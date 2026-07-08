package com.hermes.android.runtime

/**
 * Identifies the runtime implementation hosting the Hermes Python agent.
 *
 * The enum value names identify specific runtime implementations.
 * This is intentional — the enum's purpose is to distinguish between
 * concrete implementations.
 *
 * Reference:
 * - ADR-009: Production builds must NOT require Termux interaction
 */
enum class RuntimeType {
    /**
     * Remote server runtime — Hermes runs on a VPS/server and the app
     * connects over `wss://` through a TLS reverse proxy. No on-device
     * agent. This is the production runtime.
     */
    REMOTE,

    /** Future production runtime — Python bundled in APK (ADR-009). */
    EMBEDDED_PYTHON,

    /** Hypothetical far-future: native Kotlin rewrite of the agent loop. */
    NATIVE_KOTLIN,
}

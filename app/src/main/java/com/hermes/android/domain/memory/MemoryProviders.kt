package com.hermes.android.domain.memory

/**
 * Hermes' memory: the built-in files (MEMORY.md, USER.md), always on, plus at most one
 * external provider plugin (ByteRover, RetainDB, Honcho, Mem0…) that the agent also
 * reads and writes. Each profile has its own.
 */
interface MemoryProviders {

    suspend fun status(): Status

    /** The provider's settings form, with what is saved now (secrets are never sent back). */
    suspend fun config(name: String): List<ConfigField>

    /**
     * Saves [values] (field key → value; a blank secret keeps the saved one) and makes
     * [name] the active provider. Hermes refuses when the provider is not ready yet
     * (packages missing, a required key empty); the values are saved either way.
     */
    suspend fun saveAndActivate(name: String, values: Map<String, String>)

    /**
     * Installs what the provider declares it needs (Python packages, CLI tools) on the
     * Hermes machine, after saving [values]. Takes a while.
     */
    suspend fun install(name: String, values: Map<String, String>): InstallResult

    /** Back to built-in memory only. */
    suspend fun turnOff()

    /** Deletes the built-in memory files: "all", "memory" (MEMORY.md) or "user" (USER.md). */
    suspend fun resetBuiltIn(target: String)

    data class Status(
        /** Name of the active external provider; blank = built-in only. */
        val active: String,
        val providers: List<Provider>,
        val memoryFileBytes: Long,
        val userFileBytes: Long,
    )

    data class Provider(
        val name: String,
        val description: String,
        val state: ProviderState,
        /** False: [install] still has work to do. */
        val dependenciesInstalled: Boolean,
        /** Install commands of the tools it needs outside Python, to show the user. */
        val externalTools: List<String>,
        val requiredEnv: List<String>,
    )

    enum class ProviderState { READY, NEEDS_CONFIG, UNAVAILABLE, MISSING }

    data class ConfigField(
        val key: String,
        val label: String,
        val kind: FieldKind,
        val description: String,
        val placeholder: String,
        val required: Boolean,
        /** Saved value; always blank for a secret. */
        val value: String,
        /** A secret is saved (its value is not shown). */
        val isSet: Boolean,
        val options: List<Option>,
        val url: String,
        /** Shown only while every key here has that value (field key → value). */
        val showWhen: Map<String, String>,
    )

    data class Option(val value: String, val label: String)

    enum class FieldKind { TEXT, SECRET, SELECT, BOOLEAN, INTEGER, NUMBER }

    data class InstallResult(val ok: Boolean, val steps: List<InstallStep>)

    data class InstallStep(val name: String, val status: String, val detail: String)
}

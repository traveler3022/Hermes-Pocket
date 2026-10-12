package com.hermes.android.domain.setup

import java.io.IOException

/**
 * First-run provider setup and the provider page: API keys, sign-in (OAuth) providers and the
 * default model, on whichever runtime the user picked.
 */
interface ProviderSetup {

    val providers: List<SetupProvider>

    /** Live-probes the key (or, for custom endpoints, the base URL) before it is saved. */
    suspend fun checkKey(provider: SetupProvider, apiKey: String, baseUrl: String = ""): KeyCheck

    /** Custom endpoints store their key with the model assignment instead of in .env. */
    suspend fun saveKey(provider: SetupProvider, apiKey: String)

    /** Removes an env-var key everywhere Hermes keeps it. */
    suspend fun removeKey(envVar: String)

    /** Sign-in providers and their status. */
    suspend fun oauthProviders(): List<OAuthProvider>

    /** Signs out of provider [id]. */
    suspend fun disconnectOAuth(id: String)

    /**
     * Signs in to [id] with the device-code flow: [onCode] gets the code and the page to confirm
     * it on, then this returns once approved, or throws [DeviceSignInException].
     */
    suspend fun deviceSignIn(id: String, onCode: (DeviceCode) -> Unit)

    suspend fun models(provider: SetupProvider): List<String>

    suspend fun setDefaultModel(
        provider: SetupProvider,
        model: String,
        apiKey: String = "",
        baseUrl: String = "",
        confirmed: Boolean = false,
    ): DefaultModelResult

    /** True once config.yaml names a concrete provider (i.e. setup already happened elsewhere). */
    suspend fun hasConfiguredModel(): Boolean
}

/** Whether first-run setup (runtime + provider) has been completed. */
interface SetupProgress {
    val isComplete: Boolean

    fun markComplete()
}

/** A provider offered in first-run setup; [envVar] is where Hermes reads its key. */
data class SetupProvider(
    val slug: String,
    val name: String,
    val envVar: String,
    val keyUrl: String,
    val needsBaseUrl: Boolean = false,
)

data class KeyCheck(val ok: Boolean, val reachable: Boolean, val message: String, val models: List<String> = emptyList())

sealed interface DefaultModelResult {
    data object Applied : DefaultModelResult
    data class NeedsConfirm(val message: String) : DefaultModelResult
}

/**
 * A sign-in (OAuth) provider, one row of the dashboard's GET /api/providers/oauth — the list the
 * desktop's Accounts page shows. [flow] is `device_code` (code + link, Hermes polls) or `external`
 * (the provider's own CLI signs in); [cliCommand] is the terminal command that signs in either way.
 */
data class OAuthProvider(
    val id: String,
    val name: String,
    val flow: String,
    val cliCommand: String,
    val docsUrl: String,
    val loggedIn: Boolean,
    val disconnectable: Boolean,
    val disconnectHint: String?,
)

/** A device-code sign-in waiting for the user: confirm [userCode] at [url] (some links carry it already). */
data class DeviceCode(val userCode: String, val url: String)

/** A device-code sign-in that ended without approval; [status] is Hermes' session status (expired, denied, error…). */
class DeviceSignInException(val status: String, message: String) : IOException(message)

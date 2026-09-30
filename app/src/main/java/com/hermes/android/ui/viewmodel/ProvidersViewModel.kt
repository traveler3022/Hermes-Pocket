package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.OAuthProvider
import com.hermes.android.data.ProviderSetupRepository
import com.hermes.android.data.SetupProvider
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import javax.inject.Inject

/** One provider from `model.options` with `include_unconfigured` ([parseProviderRows]). */
data class ProviderRow(
    val slug: String,
    val name: String,
    val connected: Boolean,
    /** Only on rows that aren't connected yet: `api_key`, `oauth_device_code`, `oauth_external`, … */
    val authType: String? = null,
    val keyEnv: String? = null,
    val modelCount: Int = 0,
    val isCurrent: Boolean = false,
    /** A `custom_providers` endpoint; the Providers page lists those from config.yaml instead. */
    val isUserDefined: Boolean = false,
)

data class ProviderNotice(val en: String, val fa: String)

/** The skeleton row `model.options` adds for "Custom endpoint"; the custom server dialog covers it. */
private const val CUSTOM_SLUG = "custom"

data class ProvidersUiState(
    val rows: List<ProviderRow> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: String? = null,
    /** Sign-in providers, loaded on demand: listing them runs a script, seconds on a phone. */
    val accounts: List<OAuthProvider> = emptyList(),
    val accountsLoaded: Boolean = false,
    val accountsLoading: Boolean = false,
    val accountsError: String? = null,
    /** Slug or account id with a save, disconnect or recheck in flight. */
    val busy: String? = null,
    val notice: ProviderNotice? = null,
) {
    /** Hermes' own providers that are set up; custom endpoints are listed from config.yaml. */
    val connected: List<ProviderRow>
        get() = rows.filter { it.connected && !it.isUserDefined && it.slug != CUSTOM_SLUG }

    /** Providers that connect with an API key and aren't connected yet. */
    val keyProviders: List<ProviderRow>
        get() = rows.filter { !it.connected && it.authType == "api_key" && !it.isUserDefined && it.slug != CUSTOM_SLUG }

    fun signedInAccount(slug: String): OAuthProvider? = accounts.firstOrNull { it.id == slug && it.loggedIn }
}

/**
 * Settings → Models → Providers: Hermes' own providers, connected with an API key or an account,
 * like the desktop's Providers page. Keys go through the gateway (`model.save_key`,
 * `model.disconnect`); accounts through the dashboard's OAuth routes, or the same code run as a
 * script on the built-in Linux, which has no web server ([ProviderSetupRepository]).
 */
@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val setup: ProviderSetupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProvidersUiState())
    val uiState: StateFlow<ProvidersUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.rows.isEmpty(), loadError = null) }
            try {
                val result = gatewayClient.request(
                    GatewayMethods.MODEL_OPTIONS,
                    mapOf("include_unconfigured" to JsonPrimitive(true)),
                )
                _uiState.update { it.copy(rows = parseProviderRows(result), isLoading = false) }
            } catch (e: Exception) {
                Timber.w(e, "[Providers] model.options failed")
                _uiState.update { it.copy(isLoading = false, loadError = reasonOf(e)) }
            }
        }
    }

    /** Loads the sign-in providers once; [force] re-reads their status. */
    fun loadAccounts(force: Boolean = false) {
        val state = _uiState.value
        if (state.accountsLoading || (state.accountsLoaded && !force)) return
        viewModelScope.launch { refreshAccounts() }
    }

    /** Where to get a key for [slug], for the providers first-run setup knows. */
    fun keyUrl(slug: String): String? =
        setup.providers.firstOrNull { it.slug == slug }?.keyUrl?.takeIf { it.isNotBlank() }

    /** Connects [row] with [apiKey]; [onConnected] runs once Hermes has it. */
    fun saveKey(row: ProviderRow, apiKey: String, onConnected: () -> Unit = {}) {
        val key = apiKey.trim()
        if (key.isEmpty() || _uiState.value.busy != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = row.slug) }
            try {
                try {
                    gatewayClient.request(
                        GatewayMethods.MODEL_SAVE_KEY,
                        mapOf("slug" to JsonPrimitive(row.slug), "api_key" to JsonPrimitive(key)),
                    )
                } catch (e: GatewayException) {
                    // model.save_key knows only registry providers and OpenRouter isn't one: write
                    // its env var the way first-run setup and the desktop's key page do.
                    val envVar = envVarOf(row)
                    if (e.code != UNKNOWN_PROVIDER || envVar == null) throw e
                    setup.saveKey(SetupProvider(row.slug, row.name, envVar, ""), key)
                }
                _uiState.update { it.copy(busy = null, notice = ProviderNotice("${row.name} connected", "${row.name} وصل شد")) }
                onConnected()
                load()
            } catch (e: Exception) {
                Timber.w(e, "[Providers] saving the key for ${row.slug} failed")
                _uiState.update { it.copy(busy = null, notice = failed(e)) }
            }
        }
    }

    /**
     * Disconnects [row]. One signed in with an account signs out as the desktop's Accounts page
     * does; otherwise `model.disconnect` removes its keys. An account Hermes can't sign out of
     * (its own CLI owns it) reports where to do it instead.
     */
    fun disconnect(row: ProviderRow) {
        if (_uiState.value.busy != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = row.slug) }
            try {
                if (!_uiState.value.accountsLoaded) refreshAccounts()
                val account = _uiState.value.signedInAccount(row.slug)
                when {
                    account?.disconnectable == true -> setup.disconnectOAuth(account.id)
                    account != null -> try {
                        disconnectKeys(row)
                    } catch (e: GatewayException) {
                        if (e.code != NOTHING_TO_DISCONNECT) throw e
                        error(account.disconnectHint ?: e.message.orEmpty())
                    }
                    else -> disconnectKeys(row)
                }
                _uiState.update { it.copy(busy = null, notice = ProviderNotice("${row.name} disconnected", "اتصال ${row.name} قطع شد")) }
                load()
                if (account != null) refreshAccounts()
            } catch (e: Exception) {
                Timber.w(e, "[Providers] disconnecting ${row.slug} failed")
                _uiState.update { it.copy(busy = null, notice = failed(e)) }
            }
        }
    }

    /** "I've signed in": re-reads [account] after its sign-in command ran in a terminal. */
    fun recheck(account: OAuthProvider, onConnected: () -> Unit = {}) {
        if (_uiState.value.busy != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = account.id) }
            val fresh = refreshAccounts()?.firstOrNull { it.id == account.id }
            val notice = when {
                fresh == null -> failed(IllegalStateException(_uiState.value.accountsError ?: "no status for ${account.name}"))
                fresh.loggedIn -> ProviderNotice("${account.name} connected", "${account.name} وصل شد")
                // The desktop's wording for the same check.
                else -> ProviderNotice(
                    "Hermes still cannot reach ${account.name}. Run `${account.cliCommand}` in a terminal first.",
                    "هرمس هنوز به ${account.name} دسترسی ندارد. اول `${account.cliCommand}` را در ترمینال اجرا کن.",
                )
            }
            _uiState.update { it.copy(busy = null, notice = notice) }
            if (fresh?.loggedIn == true) {
                onConnected()
                load()
            }
        }
    }

    /** Signs out of [account] (the Accounts page's disconnect). */
    fun signOut(account: OAuthProvider) {
        if (_uiState.value.busy != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = account.id) }
            try {
                setup.disconnectOAuth(account.id)
                _uiState.update { it.copy(busy = null, notice = ProviderNotice("Signed out of ${account.name}", "از ${account.name} خارج شدی")) }
                refreshAccounts()
                load()
            } catch (e: Exception) {
                Timber.w(e, "[Providers] signing out of ${account.id} failed")
                _uiState.update { it.copy(busy = null, notice = failed(e)) }
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private suspend fun disconnectKeys(row: ProviderRow) {
        try {
            gatewayClient.request(GatewayMethods.MODEL_DISCONNECT, mapOf("slug" to JsonPrimitive(row.slug)))
        } catch (e: GatewayException) {
            // Same gap as model.save_key: OpenRouter has no registry entry, so its key goes by env var.
            val envVar = envVarOf(row)
            if (e.code != NOTHING_TO_DISCONNECT || envVar == null) throw e
            setup.removeKey(envVar)
        }
    }

    /** Null when listing failed; [ProvidersUiState.accountsError] then says why. */
    private suspend fun refreshAccounts(): List<OAuthProvider>? {
        _uiState.update { it.copy(accountsLoading = true, accountsError = null) }
        return try {
            setup.oauthProviders().also { accounts ->
                _uiState.update { it.copy(accounts = accounts, accountsLoaded = true, accountsLoading = false) }
            }
        } catch (e: Exception) {
            Timber.w(e, "[Providers] listing sign-in providers failed")
            _uiState.update { it.copy(accountsLoading = false, accountsError = reasonOf(e)) }
            null
        }
    }

    private fun envVarOf(row: ProviderRow): String? = row.keyEnv ?: KEY_ENV_FALLBACK[row.slug]

    private fun failed(e: Exception) = reasonOf(e).let { ProviderNotice("Failed: $it", "انجام نشد: $it") }

    private fun reasonOf(e: Exception) = e.message ?: e.javaClass.simpleName

    private companion object {
        /** model.save_key: the slug isn't in Hermes' PROVIDER_REGISTRY. */
        const val UNKNOWN_PROVIDER = 4002

        /** model.disconnect: no credentials found for the slug. */
        const val NOTHING_TO_DISCONNECT = 4005

        /** Key env vars `model.options` leaves blank (the provider has no registry entry). */
        val KEY_ENV_FALLBACK = mapOf("openrouter" to "OPENROUTER_API_KEY")
    }
}

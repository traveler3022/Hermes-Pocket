package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.messaging.TelegramSaveResult
import com.hermes.android.domain.messaging.TelegramSetup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Where the Telegram wizard is. */
sealed interface TelegramStep {
    data object Closed : TelegramStep
    /** Step 1: the token of the bot the user made with @BotFather. */
    data object Token : TelegramStep
    /** Step 2: the numeric user ids allowed to talk to it. */
    data object Users : TelegramStep
    data object Saving : TelegramStep
    data class Done(val result: TelegramSaveResult) : TelegramStep
}

data class TelegramSetupUiState(
    val step: TelegramStep = TelegramStep.Closed,
    val token: String = "",
    val allowedIds: List<String> = emptyList(),
    val error: String? = null,
)

/** Connects the user's own Telegram bot in two steps: token, then allowed user ids. */
@HiltViewModel
class TelegramSetupViewModel @Inject constructor(
    private val setup: TelegramSetup,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TelegramSetupUiState())
    val uiState: StateFlow<TelegramSetupUiState> = _uiState.asStateFlow()

    val available: Boolean get() = setup.available

    fun start() {
        _uiState.value = TelegramSetupUiState(step = TelegramStep.Token)
    }

    fun setToken(token: String) {
        _uiState.update { it.copy(token = token.trim(), error = null) }
    }

    /** A BotFather token is `<digits>:<secret>`; the server checks it in full when saving. */
    fun tokenLooksRight(token: String): Boolean = TOKEN_SHAPE.matches(token.trim())

    fun toUsers() {
        if (!tokenLooksRight(_uiState.value.token)) return
        _uiState.update { it.copy(step = TelegramStep.Users, error = null) }
    }

    fun backToToken() {
        _uiState.update { it.copy(step = TelegramStep.Token, error = null) }
    }

    fun addAllowedId(raw: String): Boolean {
        val id = raw.trim()
        if (id.isEmpty() || !id.all(Char::isDigit)) return false
        _uiState.update { if (id in it.allowedIds) it else it.copy(allowedIds = it.allowedIds + id) }
        return true
    }

    fun removeAllowedId(id: String) {
        _uiState.update { it.copy(allowedIds = it.allowedIds - id) }
    }

    fun save() {
        val state = _uiState.value
        if (state.step != TelegramStep.Users || state.allowedIds.isEmpty()) return
        _uiState.update { it.copy(step = TelegramStep.Saving, error = null) }
        viewModelScope.launch {
            try {
                val result = setup.save(state.token, state.allowedIds)
                // The token isn't kept once it's on the server.
                _uiState.update { it.copy(step = TelegramStep.Done(result), token = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("[Telegram] save failed: ${e.javaClass.simpleName}")
                // The server's reason (e.g. a wrong token) belongs to step 1 or 2, so go back there.
                val step = if (e.message?.contains("token", ignoreCase = true) == true) TelegramStep.Token else TelegramStep.Users
                _uiState.update { it.copy(step = step, error = e.message) }
            }
        }
    }

    fun close() {
        if (_uiState.value.step == TelegramStep.Saving) return
        _uiState.value = TelegramSetupUiState()
    }

    private companion object {
        val TOKEN_SHAPE = Regex("^\\d{5,}:[A-Za-z0-9_-]{30,}$")
    }
}

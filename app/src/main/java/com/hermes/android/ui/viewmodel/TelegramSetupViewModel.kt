package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.messaging.TelegramApplyResult
import com.hermes.android.domain.messaging.TelegramPairing
import com.hermes.android.domain.messaging.TelegramPairingStatus
import com.hermes.android.domain.messaging.TelegramSetup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** Where the Telegram wizard is. */
sealed interface TelegramStep {
    data object Idle : TelegramStep
    data object Starting : TelegramStep
    /** The bot is being made in Telegram; the app polls until it exists. */
    data class Waiting(val pairing: TelegramPairing) : TelegramStep
    data class Ready(val pairing: TelegramPairing, val botUsername: String?, val ownerUserId: String?) : TelegramStep
    data class Saving(val pairing: TelegramPairing) : TelegramStep
    data class Done(val result: TelegramApplyResult, val botUsername: String?) : TelegramStep
}

data class TelegramSetupUiState(
    val step: TelegramStep = TelegramStep.Idle,
    val allowedIds: List<String> = emptyList(),
    val error: String? = null,
    // The pairing ran out before the bot was made; a new one has to be started.
    val expired: Boolean = false,
)

/** The step-by-step Telegram bot setup (Hy4ri/hermes-mobile's onboarding flow). */
@HiltViewModel
class TelegramSetupViewModel @Inject constructor(
    private val setup: TelegramSetup,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TelegramSetupUiState())
    val uiState: StateFlow<TelegramSetupUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    val available: Boolean get() = setup.available

    fun start() {
        if (_uiState.value.step !is TelegramStep.Idle && _uiState.value.step !is TelegramStep.Done) return
        _uiState.value = TelegramSetupUiState(step = TelegramStep.Starting)
        job?.cancel()
        job = viewModelScope.launch {
            val pairing = try {
                setup.start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Telegram] start failed")
                _uiState.update { it.copy(step = TelegramStep.Idle, error = e.message) }
                return@launch
            }
            _uiState.update { it.copy(step = TelegramStep.Waiting(pairing)) }
            poll(pairing)
        }
    }

    private suspend fun poll(pairing: TelegramPairing) {
        while (_uiState.value.step is TelegramStep.Waiting) {
            delay(POLL_MS)
            try {
                val status = setup.status(pairing.pairingId)
                if (status is TelegramPairingStatus.Ready) {
                    _uiState.update {
                        it.copy(
                            step = TelegramStep.Ready(pairing, status.botUsername, status.ownerUserId),
                            allowedIds = listOfNotNull(status.ownerUserId),
                            error = null,
                        )
                    }
                    return
                }
                _uiState.update { it.copy(error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isExpired(pairing.expiresAt)) {
                    _uiState.update { it.copy(step = TelegramStep.Idle, error = null, expired = true) }
                    return
                }
                Timber.w(e, "[Telegram] status poll failed")
                _uiState.update { it.copy(error = e.message) }
            }
        }
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

    fun apply() {
        val ready = _uiState.value.step as? TelegramStep.Ready ?: return
        val ids = _uiState.value.allowedIds
        if (ids.isEmpty()) return
        _uiState.update { it.copy(step = TelegramStep.Saving(ready.pairing), error = null) }
        job = viewModelScope.launch {
            try {
                val result = setup.apply(ready.pairing.pairingId, ids)
                _uiState.update { it.copy(step = TelegramStep.Done(result, ready.botUsername)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Telegram] apply failed")
                _uiState.update { it.copy(step = ready, error = e.message) }
            }
        }
    }

    /** Closes the wizard; a pairing still open on the server is cancelled there too. */
    fun close() {
        val pairing = when (val step = _uiState.value.step) {
            is TelegramStep.Waiting -> step.pairing
            is TelegramStep.Ready -> step.pairing
            else -> null
        }
        job?.cancel()
        _uiState.value = TelegramSetupUiState()
        if (pairing != null) {
            viewModelScope.launch {
                withContext(NonCancellable) { runCatching { setup.cancel(pairing.pairingId) } }
            }
        }
    }

    private fun isExpired(expiresAt: String): Boolean {
        val at = runCatching { isoParser().parse(expiresAt.take(19))?.time }.getOrNull() ?: return false
        return at < System.currentTimeMillis()
    }

    private fun isoParser() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private companion object {
        const val POLL_MS = 2_000L
    }
}

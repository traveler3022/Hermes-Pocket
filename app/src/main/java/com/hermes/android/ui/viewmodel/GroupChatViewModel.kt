package com.hermes.android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.data.GroupsRepository
import com.hermes.android.gateway.GatewayException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * One Group Chat. The gateway drives the discussion; this reads the room log while the
 * screen is open ([watch]) and sends what the user writes.
 */
@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val groups: GroupsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val roomId: String = checkNotNull(savedStateHandle["roomId"])

    data class UiState(
        val room: GroupsRepository.Room? = null,
        val rows: List<GroupRow> = emptyList(),
        /** The members are answering the newest message. */
        val discussing: Boolean = false,
        val approvals: List<GroupsRepository.Approval> = emptyList(),
        val retryTaskIds: List<String> = emptyList(),
        val input: String = "",
        val isSending: Boolean = false,
        val isLoading: Boolean = true,
        val error: String? = null,
        /** The group was deleted (here or elsewhere): the screen leaves. */
        val gone: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val events = mutableListOf<GroupsRepository.Event>()
    private var lastSeq = 0L

    /** One log read at a time: the poll and a send's follow-up read must not both append a page. */
    private val reading = Mutex()

    /**
     * Reads the room until cancelled (the screen stops it when it leaves the foreground):
     * often while members are answering, rarely when the room is quiet. The log is
     * durable, so a gap while the app was away is caught up on the next read.
     */
    suspend fun watch() {
        while (true) {
            refresh()
            delay(if (_uiState.value.discussing) ACTIVE_POLL_MS else IDLE_POLL_MS)
        }
    }

    private suspend fun refresh() {
        try {
            reading.withLock {
                if (_uiState.value.room == null) {
                    val room = groups.room(roomId)
                    if (room == null) {
                        _uiState.update { it.copy(gone = true) }
                        return
                    }
                    _uiState.update { it.copy(room = room) }
                }
                val page = groups.log(roomId, lastSeq)
                if (page.events.isNotEmpty()) {
                    events += page.events.filter { it.seq > lastSeq }
                    lastSeq = events.maxOf { it.seq }
                }
                val status = groups.status(roomId)
                val members = _uiState.value.room?.members.orEmpty()
                _uiState.update {
                    it.copy(
                        rows = GroupTranscript.rows(events, members),
                        discussing = status?.working == true || GroupTranscript.awaitingReplies(events),
                        approvals = status?.approvals.orEmpty(),
                        retryTaskIds = status?.retryTaskIds.orEmpty(),
                        isLoading = false,
                    )
                }
            }
        } catch (e: GatewayException) {
            Timber.w(e, "[Group] read failed")
            // 4112/4114: the room is unknown or disbanded (groups.log / groups.state).
            if (e.code == 4112 || e.code == 4114) {
                _uiState.update { it.copy(gone = true) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = e.userMessage()) }
            }
        } catch (e: Exception) {
            Timber.w(e, "[Group] read failed")
            _uiState.update { it.copy(isLoading = false, error = e.userMessage()) }
        }
    }

    fun updateInput(text: String) = _uiState.update { it.copy(input = text) }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.isSending) return
        // One id per message: if the answer is lost and the user sends again, the
        // server keeps the first copy instead of posting it twice.
        val clientId = "app-${UUID.randomUUID()}"
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            try {
                groups.send(roomId, text, clientId)
                _uiState.update { it.copy(input = "", isSending = false, discussing = true) }
                refresh()
            } catch (e: Exception) {
                Timber.w(e, "[Group] send failed")
                _uiState.update { it.copy(isSending = false, error = e.userMessage()) }
            }
        }
    }

    fun stop() = act { groups.stop(roomId) }

    fun answer(approval: GroupsRepository.Approval, allow: Boolean) = act {
        groups.approve(roomId, approval, allow)
    }

    fun retry(taskId: String) = act { groups.retry(roomId, taskId) }

    fun rename(name: String) {
        if (name.isBlank()) return
        act {
            groups.rename(roomId, name)
            _uiState.update { state -> state.copy(room = state.room?.copy(name = name.trim())) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                groups.delete(roomId)
                _uiState.update { it.copy(gone = true) }
            } catch (e: Exception) {
                Timber.w(e, "[Group] delete failed")
                _uiState.update { it.copy(error = e.userMessage()) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun act(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                refresh()
            } catch (e: Exception) {
                Timber.w(e, "[Group] action failed")
                _uiState.update { it.copy(error = e.userMessage()) }
            }
        }
    }

    private fun Exception.userMessage(): String =
        (this as? GatewayException)?.rpcMessage ?: message ?: toString()

    private companion object {
        const val ACTIVE_POLL_MS = 1_500L
        const val IDLE_POLL_MS = 6_000L
    }
}

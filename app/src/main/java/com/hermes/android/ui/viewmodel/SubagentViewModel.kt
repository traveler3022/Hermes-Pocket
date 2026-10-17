package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.session.AgentTasks
import com.hermes.android.domain.session.LiveSubagent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The sub-agent sheet in a chat: one child's live log, what it is doing now, and
 * steering or stopping it (`subagent.list` / `tail` / `steer` / `interrupt`). The log
 * is read every couple of seconds while the sheet is open.
 */
@HiltViewModel
class SubagentViewModel @Inject constructor(
    private val tasks: AgentTasks,
) : ViewModel() {

    /** [sessionId] is the chat's live id; the sheet is closed while this is null. */
    data class Sheet(
        val sessionId: String,
        val subagentId: String,
        val goal: String,
        val live: LiveSubagent? = null,
        /** Seen in the chat's list at least once, then gone: it has ended. */
        val ended: Boolean = false,
        val log: String = "",
        val logAvailable: Boolean = false,
        val truncated: Boolean = false,
        val steering: Boolean = false,
        val stopping: Boolean = false,
    )

    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var pollJob: Job? = null

    fun open(sessionId: String, subagentId: String, goal: String) {
        _sheet.value = Sheet(sessionId, subagentId, goal)
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var seen = false
            while (true) {
                val current = _sheet.value ?: break
                try {
                    val live = tasks.subagents(current.sessionId).firstOrNull { it.subagentId == current.subagentId }
                    if (live != null) seen = true
                    val tail = tasks.subagentTail(current.sessionId, current.subagentId)
                    _sheet.update { sheet ->
                        sheet?.copy(
                            live = live ?: sheet.live,
                            ended = seen && live == null,
                            // An ended child's log is cleaned up; keep the last one read.
                            log = if (tail.available) tail.text else sheet.log,
                            logAvailable = tail.available || sheet.logAvailable,
                            truncated = if (tail.available) tail.truncated else sheet.truncated,
                        )
                    }
                    if (seen && live == null) break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "[Subagent] read failed")
                }
                delay(POLL_MS)
            }
        }
    }

    fun close() {
        pollJob?.cancel()
        _sheet.value = null
    }

    fun steer(text: String) {
        val sheet = _sheet.value ?: return
        val message = text.trim()
        if (message.isEmpty() || sheet.steering) return
        _sheet.update { it?.copy(steering = true) }
        viewModelScope.launch {
            try {
                val queued = tasks.steerSubagent(sheet.sessionId, sheet.subagentId, message)
                _messages.tryEmit(if (queued) QUEUED else REJECTED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Subagent] steer failed")
                _messages.tryEmit(e.message ?: e.javaClass.simpleName)
            } finally {
                _sheet.update { it?.copy(steering = false) }
            }
        }
    }

    fun stop() {
        val sheet = _sheet.value ?: return
        if (sheet.stopping) return
        _sheet.update { it?.copy(stopping = true) }
        viewModelScope.launch {
            try {
                val found = tasks.interruptSubagent(sheet.sessionId, sheet.subagentId)
                _messages.tryEmit(if (found) STOPPED else ALREADY_ENDED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Subagent] interrupt failed")
                _messages.tryEmit(e.message ?: e.javaClass.simpleName)
            } finally {
                _sheet.update { it?.copy(stopping = false) }
            }
        }
    }

    companion object {
        const val POLL_MS = 2_000L

        /** Message keys; the screen shows them in the app's language. */
        const val QUEUED = "subagent:queued"
        const val REJECTED = "subagent:rejected"
        const val STOPPED = "subagent:stopped"
        const val ALREADY_ENDED = "subagent:ended"
    }
}

package com.hermes.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.domain.profiles.HermesProfiles
import com.hermes.android.domain.session.ArchivedChats
import com.hermes.android.domain.session.AttachedSession
import com.hermes.android.domain.session.ChatSessions
import com.hermes.android.domain.topics.ChatTopic
import com.hermes.android.domain.topics.ChatTopics
import com.hermes.android.domain.topics.movedPast
import com.hermes.android.domain.topics.newTopicId
import com.hermes.android.domain.topics.pointedAt
import com.hermes.android.domain.topics.renamed
import com.hermes.android.domain.topics.withTopic
import com.hermes.android.domain.topics.withoutTopic
import com.hermes.android.gateway.GatewayException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * The drawer's Topics section: reads the topics of the open profile and runs every change to
 * them. A topic's chat is renamed with it; starting a topic fresh files its chat away in the
 * archive under the topic's name and the time, and a new chat takes its place.
 *
 * The steps and their order follow Kyllene's ThreadsRepository (rename, startFresh, undo):
 * Copyright (c) 2026 Giton22, MIT License.
 */
@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val repository: ChatTopics,
    private val sessions: ChatSessions,
    private val archive: ArchivedChats,
    private val profiles: HermesProfiles,
) : ViewModel() {

    val topics: StateFlow<List<ChatTopic>> = repository.topics

    /** What happened that the screen shows: a chat to open, or a change that didn't go through. */
    sealed interface Event {
        /** Open the chat [sessionId]: the new chat of a topic started fresh. */
        data class Open(val sessionId: String) : Event

        /** [action] failed; [message] is the server's words, when there are any. */
        data class Failed(val action: Action, val message: String?) : Event

        /** The chat list changed (titles, the archive): read it again. */
        data object ListChanged : Event
    }

    enum class Action { Make, Rename, StartFresh, Move, Remove }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /** Topics being started fresh right now: a second tap would make a second new chat. */
    private val starting = mutableSetOf<String>()

    init {
        // Each profile keeps its own topics.
        viewModelScope.launch { profiles.active.collect { repository.refresh(it) } }
    }

    /** Reads the topics again, as the drawer opens: another device may have changed them. */
    fun refresh() {
        viewModelScope.launch { repository.refresh(profiles.active.value) }
    }

    /** Makes [session] a topic, named after it, at the end of the section. */
    fun make(session: SessionItem) = act(Action.Make) {
        val topic = ChatTopic(newTopicId(), session.title.trim().ifEmpty { session.id }, session.id)
        repository.change(profiles.active.value) { it.withTopic(topic) }
    }

    /** Takes [topic] out of the section; its chat stays in the list. */
    fun remove(topic: ChatTopic) = act(Action.Remove) {
        repository.change(profiles.active.value) { it.withoutTopic(topic.id) }
    }

    /** Moves [topic] to where [neighbour] is: one place up or down. */
    fun move(topic: ChatTopic, neighbour: ChatTopic) = act(Action.Move) {
        repository.change(profiles.active.value) { it.movedPast(topic.id, neighbour.id) }
    }

    /**
     * Names [topic] [name], and its chat the same. The chat is renamed first, since that is the
     * step a server can refuse (a title another chat has); if saving the topic then fails, the
     * chat gets [chatTitle], its old title, back.
     */
    fun rename(topic: ChatTopic, name: String, chatTitle: String?) {
        val clean = name.trim()
        if (clean.isEmpty() || clean == topic.name) return
        act(Action.Rename) {
            if (chatTitle != clean) sessions.rename(topic.sessionId, clean)
            try {
                repository.change(profiles.active.value) { it.renamed(topic.id, clean) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                chatTitle?.let { old -> runCatching { sessions.rename(topic.sessionId, old) } }
                throw e
            }
            _events.emit(Event.ListChanged)
        }
    }

    /**
     * Starts [topic] fresh, in an order that never loses its chat: the chat it has now is
     * renamed "<name> · <date time>" and archived; a new chat takes the topic's name; then the
     * topic is pointed at it. If a later step fails, the new chat is deleted and the old one put
     * back as it was. [chatTitle] is the old chat's title now, to put back.
     */
    fun startFresh(topic: ChatTopic, chatTitle: String?) {
        if (!starting.add(topic.id)) return
        viewModelScope.launch {
            try {
                val opened = startFreshSteps(topic, chatTitle ?: topic.name)
                _events.emit(Event.ListChanged)
                _events.emit(Event.Open(opened))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Topics] start fresh failed")
                _events.emit(Event.Failed(Action.StartFresh, e.message))
                _events.emit(Event.ListChanged)
            } finally {
                starting.remove(topic.id)
            }
        }
    }

    private suspend fun startFreshSteps(topic: ChatTopic, oldTitle: String): String {
        val oldId = topic.sessionId
        fileAway(oldId, topic.name)
        var made: AttachedSession? = null
        try {
            archive.setArchived(oldId, true)
            val created = newChat(topic).also { made = it }
            val openId = created.storedId ?: created.liveId
            repository.change(profiles.active.value) { it.pointedAt(topic.id, openId) }
            return openId
        } catch (e: Exception) {
            // Undone even when cancelled half way, so nothing is left in the archive with no topic to hold it.
            withContext(NonCancellable) {
                made?.let { sessions.discardNewChat(it) }
                runCatching { archive.setArchived(oldId, false) }
                runCatching { sessions.rename(oldId, oldTitle) }
            }
            throw e
        }
    }

    /** Renames the chat [id] "<name> · <date time>", with seconds when that title is taken. */
    private suspend fun fileAway(id: String, name: String) {
        try {
            sessions.rename(id, "$name · ${stamp("yyyy-MM-dd HH:mm")}")
        } catch (e: GatewayException) {
            if (!e.titleTaken()) throw e
            sessions.rename(id, "$name · ${stamp("yyyy-MM-dd HH:mm:ss")}")
        }
    }

    /** A new chat named after [topic], or "<name> (2)" and on while another chat has the name. */
    private suspend fun newChat(topic: ChatTopic): AttachedSession {
        val titles = listOf(topic.name) + (2..MAX_SUFFIX).map { "${topic.name} ($it)" }
        for (title in titles) {
            try {
                return sessions.createTitledChat(title, topic.cwd)
                    ?: throw IllegalStateException("The server made no new chat.")
            } catch (e: GatewayException) {
                // Only a taken title moves on to the next name; anything else is a real failure.
                if (!e.titleTaken()) throw e
            }
        }
        throw IllegalStateException("Every name for the new chat is taken.")
    }

    /** `session.title` answers a title another chat has with 4022 (`set_session_title`'s ValueError). */
    private fun GatewayException.titleTaken(): Boolean = code == 4022

    private fun stamp(pattern: String): String = SimpleDateFormat(pattern, Locale.US).format(Date())

    private fun act(action: Action, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[Topics] $action failed")
                _events.emit(Event.Failed(action, e.message))
            }
        }
    }

    private companion object {
        /** The highest number tried after a taken name. */
        const val MAX_SUFFIX = 9
    }
}

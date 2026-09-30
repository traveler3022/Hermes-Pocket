package com.hermes.android.ui.viewmodel

import android.content.SharedPreferences
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.gateway.asText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/** Reactions on the chat's messages: the user's own, the agent's, and the switch for them. */
internal class ChatReactionsDelegate(
    private val gatewayClient: GatewayClient,
    private val sessionDelegate: ChatSessionDelegate,
    /** ThemeModeState's file, where the Appearance switches live. */
    private val appearancePrefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ChatUiState>,
) {
    // Settings → Appearance → Message reactions. The switch also decides whether the agent
    // gets react_to_message and hears about the user's reactions, and that is decided where
    // the agent runs, so the gateway is told: when it moves, and on every connect once the
    // user has set it (the desktop's display-toggles.ts).
    private val switchMirror = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MESSAGE_REACTIONS_PREF) pushSwitch()
    }

    init {
        appearancePrefs.registerOnSharedPreferenceChangeListener(switchMirror)
    }

    /** The gateway came up: it hears the switch again, once the user has set it. */
    fun onConnected() {
        if (appearancePrefs.contains(MESSAGE_REACTIONS_PREF)) pushSwitch()
    }

    fun close() {
        appearancePrefs.unregisterOnSharedPreferenceChangeListener(switchMirror)
    }

    /**
     * Sets, switches or retracts the user's reaction on a message (message.react: the
     * same emoji again retracts, null clears). Painted at once, then replaced by the list
     * the server stored, or put back if the write fails (the desktop's reactions.ts).
     */
    fun react(messageId: String, emoji: String?) {
        val sessionId = state.value.activeSessionId ?: return
        val target = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val before = target.reactionsOrNull ?: return
        paint(messageId, applyReaction(before, emoji))
        scope.launch {
            try {
                val address = addressOf(sessionId, target)
                if (address == null) {
                    paint(messageId, before)
                    state.update { it.copy(errorEvent = ErrorEvent.Warning(REACTION_NOT_LOCATED)) }
                    return@launch
                }
                val result = gatewayClient.request(
                    GatewayMethods.MESSAGE_REACT,
                    mapOf(
                        "session_id" to JsonPrimitive(sessionId),
                        address,
                        "emoji" to (emoji?.let { JsonPrimitive(it) } ?: JsonNull),
                        "author" to JsonPrimitive(AUTHOR_USER),
                    ),
                ) as? JsonObject
                paint(
                    messageId,
                    parseReactions(result?.get("reactions")),
                    result?.get("row_id").asText()?.toLongOrNull(),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "[Chat] message.react failed")
                paint(messageId, before)
                state.update { it.copy(errorEvent = ErrorEvent.Warning("Could not react: ${e.message}")) }
            }
        }
    }

    /**
     * How message.react names [target]: by its row id, which a bubble drawn live only has
     * once it is found in the stored chat (see [findUserRow]). A reply whose text does not
     * match is still the newest stored reply once nothing is running, and the server finds
     * that one itself (newest_role, what the desktop sends for a live message). Null when
     * the message cannot be told apart for certain.
     */
    private suspend fun addressOf(sessionId: String, target: ChatMessage): Pair<String, JsonElement>? {
        target.rowIdOrNull?.let { return "row_id" to JsonPrimitive(it) }
        val local = state.value.messages
        val rowId = sessionDelegate.serverTurns(sessionId)?.let { server ->
            when (target) {
                is ChatMessage.User -> findUserRow(local, target, server.filterIsInstance<ChatMessage.User>())
                is ChatMessage.Assistant -> findAssistantRow(local, target, server.filterIsInstance<ChatMessage.Assistant>())
                else -> null
            }
        }
        if (rowId != null) return "row_id" to JsonPrimitive(rowId)
        val newestReply = local.lastOrNull { it is ChatMessage.Assistant }
        return if (target is ChatMessage.Assistant && newestReply?.id == target.id && !state.value.isSending) {
            "newest_role" to JsonPrimitive("assistant")
        } else {
            null
        }
    }

    private fun paint(messageId: String, reactions: List<MessageReaction>, rowId: Long? = null) {
        state.update { current ->
            current.copy(messages = current.messages.updateFirst({ it.id == messageId }) { it.withReactions(reactions, rowId) })
        }
    }

    private fun pushSwitch() {
        val enabled = appearancePrefs.getBoolean(MESSAGE_REACTIONS_PREF, false)
        scope.launch {
            try {
                gatewayClient.request(
                    GatewayMethods.CONFIG_SET,
                    mapOf(
                        "key" to JsonPrimitive("display.message_reactions"),
                        "value" to JsonPrimitive(enabled.toString()),
                    ),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Not connected, or a Hermes too old to know the key: the next change
                // and the next connect both send it again.
                Timber.w(e, "[Chat] display.message_reactions not sent")
            }
        }
    }

    /** The agent reacted (react_to_message). */
    fun onAgentReaction(event: GatewayEvent.AgentReaction) {
        // Already stored; this only paints it now rather than at the next reload.
        // A message drawn live has no row id yet, and the agent reacts to the newest
        // message of that role unless told otherwise, so it lands there
        // (desktop-bridge.ts). The id is not stamped on it: a guess must not become
        // the address the user's own reactions are sent to.
        val reactions = parseReactions(event.reactions)
        state.update { current ->
            val messages = current.messages
            val byRow = messages.indexOfFirst { it.rowIdOrNull == event.rowId }
            val index = if (byRow >= 0) byRow else messages.indexOfLast { msg ->
                msg.rowIdOrNull == null &&
                    if (event.role == "assistant") msg is ChatMessage.Assistant else msg is ChatMessage.User
            }
            if (index < 0) current
            else current.copy(messages = messages.toMutableList().also { it[index] = it[index].withReactions(reactions) })
        }
    }

    private companion object {
        const val REACTION_NOT_LOCATED =
            "Could not find this message in the stored chat. Reopen the chat and try again."
    }
}

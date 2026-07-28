package com.hermes.android.ui.viewmodel

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

internal class ChatDrawerDelegate(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
    private val loadSessionList: suspend (MutableStateFlow<ChatUiState>) -> Unit,
    private val createNewSession: suspend (MutableStateFlow<ChatUiState>) -> Unit,
) {
    fun updateSearch(state: MutableStateFlow<ChatUiState>, query: String) {
        state.update { it.copy(drawerSearchQuery = query) }
    }

    fun toggleSort(state: MutableStateFlow<ChatUiState>) {
        state.update { it.copy(drawerSortNewest = !state.value.drawerSortNewest) }
    }

    fun togglePin(state: MutableStateFlow<ChatUiState>, sessionId: String) {
        val pins = state.value.drawerPinnedIds
        state.update { it.copy(
            drawerPinnedIds = if (sessionId in pins) pins - sessionId else pins + sessionId,
        ) }
    }

    fun showRename(state: MutableStateFlow<ChatUiState>, sessionId: String, currentTitle: String) {
        state.update { it.copy(
            drawerRenameTarget = DrawerRenameState(sessionId, currentTitle),
        ) }
    }

    fun updateRenameText(state: MutableStateFlow<ChatUiState>, text: String) {
        state.update { it.copy(
            drawerRenameTarget = state.value.drawerRenameTarget?.copy(inputText = text),
        ) }
    }

    fun hideRename(state: MutableStateFlow<ChatUiState>) {
        state.update { it.copy(drawerRenameTarget = null) }
    }

    fun confirmRename(state: MutableStateFlow<ChatUiState>) {
        val target = state.value.drawerRenameTarget ?: return
        val newTitle = target.inputText.trim().ifEmpty { return }
        state.update { it.copy(drawerRenameTarget = null) }
        scope.launch {
            try {
                val params = buildJsonObject {
                    put("session_id", target.sessionId)
                    put("title", newTitle)
                }
                gatewayClient.request(GatewayMethods.SESSION_TITLE, jsonToElementMap(params))
                Timber.i("[Chat] Renamed ${target.sessionId} -> $newTitle")
                loadSessionList(state)
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Rename failed")
                state.update { it.copy(errorEvent = ErrorEvent.Error("Rename failed: ${e.message}")) }
            }
        }
    }

    fun showDelete(state: MutableStateFlow<ChatUiState>, sessionId: String) {
        state.update { it.copy(drawerDeleteTarget = sessionId) }
    }

    fun hideDelete(state: MutableStateFlow<ChatUiState>) {
        state.update { it.copy(drawerDeleteTarget = null) }
    }

    fun confirmDelete(state: MutableStateFlow<ChatUiState>) {
        val sessionId = state.value.drawerDeleteTarget ?: return
        state.update { it.copy(drawerDeleteTarget = null) }
        scope.launch {
            try {
                val params = buildJsonObject { put("session_id", sessionId) }
                gatewayClient.request(GatewayMethods.SESSION_DELETE, jsonToElementMap(params))
                Timber.i("[Chat] Deleted $sessionId")
                if (state.value.activeSessionId == sessionId) {
                    state.update { it.copy(
                        activeSessionId = null,
                        messages = emptyList(),
                    ) }
                    createNewSession(state)
                }
                loadSessionList(state)
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Delete failed")
                state.update { it.copy(errorEvent = ErrorEvent.Error("Delete failed: ${e.message}")) }
            }
        }
    }

    private fun jsonToElementMap(obj: kotlinx.serialization.json.JsonObject): Map<String, kotlinx.serialization.json.JsonElement> = obj.toMap()
}

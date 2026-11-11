package com.hermes.android.ui.viewmodel

/**
 * A message the user asked to send once the chat's running turn has finished, without
 * interrupting it. It waits in the app, not on the server: Hermes keeps its own queue for a
 * mid-turn prompt.submit, but it cannot edit or drop an entry, and a Stop
 * (session.interrupt) empties it.
 */
data class QueuedSend(
    val id: String,
    /** What the user typed, shown in the panel and in the bubble once it is sent. */
    val text: String,
    /** Fully expanded text (prompt plus attachment refs) to hand the gateway. */
    val outgoing: String,
    val attachments: List<PendingAttachment> = emptyList(),
)

/** How a turn ended, from message.complete's `status`. */
enum class TurnOutcome { COMPLETE, INTERRUPTED, ERROR }

/**
 * Hermes sends `complete`, `interrupted` or `error` (prompt_turn._result_status), and
 * `failed`/`error` from a few early refusals. No status at all is an older server: a turn
 * that ended normally.
 */
fun turnOutcomeOf(status: String?): TurnOutcome = when (status?.trim()?.lowercase()) {
    null, "", "complete", "completed", "settled" -> TurnOutcome.COMPLETE
    "interrupted", "cancelled", "canceled" -> TurnOutcome.INTERRUPTED
    else -> TurnOutcome.ERROR
}

/**
 * The per-chat queues behind "Send when done". A chat is known by more than one id (its
 * stored key, and the live id before the key arrives or after a gateway restart), so every
 * call takes the ids the chat may be under; the first one is the preferred key.
 * Not thread-safe: used from the main thread only, like the rest of the chat state.
 */
class ChatSendQueue {
    private val byChat = LinkedHashMap<String, MutableList<QueuedSend>>()

    /** Adds [item] at the end of the chat's queue. */
    fun enqueue(keys: List<String>, item: QueuedSend) {
        val key = existingKey(keys) ?: keys.firstOrNull() ?: return
        byChat.getOrPut(key) { mutableListOf() }.add(item)
    }

    /** Puts [item] back at the front, when sending it failed. */
    fun pushFront(keys: List<String>, item: QueuedSend) {
        val key = existingKey(keys) ?: keys.firstOrNull() ?: return
        byChat.getOrPut(key) { mutableListOf() }.add(0, item)
    }

    /** Everything waiting for this chat, oldest first. */
    fun items(keys: List<String>): List<QueuedSend> =
        keys.distinct().flatMap { byChat[it].orEmpty() }

    /** Takes [id] out of the chat's queue (edit or cancel); null when it is no longer there. */
    fun remove(keys: List<String>, id: String): QueuedSend? {
        for (key in keys.distinct()) {
            val list = byChat[key] ?: continue
            val index = list.indexOfFirst { it.id == id }
            if (index < 0) continue
            val item = list.removeAt(index)
            if (list.isEmpty()) byChat.remove(key)
            return item
        }
        return null
    }

    /**
     * A turn of this chat ended. Only a turn that finished normally lets the next message
     * go: after a Stop or an error the queue stays as it is, so nothing is sent on top of a
     * turn the user cut short or one that failed.
     */
    fun onTurnEnded(keys: List<String>, outcome: TurnOutcome): QueuedSend? {
        if (outcome != TurnOutcome.COMPLETE) return null
        return takeNext(keys)
    }

    /** The oldest message waiting for this chat, removed from the queue. */
    fun takeNext(keys: List<String>): QueuedSend? {
        for (key in keys.distinct()) {
            val list = byChat[key] ?: continue
            val item = list.removeFirstOrNull() ?: continue
            if (list.isEmpty()) byChat.remove(key)
            return item
        }
        return null
    }

    fun isEmpty(): Boolean = byChat.isEmpty()

    private fun existingKey(keys: List<String>): String? = keys.firstOrNull { byChat.containsKey(it) }
}

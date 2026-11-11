package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatDraftsTest {

    private val store = mutableMapOf<String, String>()
    private val drafts = ChatDrafts(
        read = { store[it] },
        write = { key, text -> if (text == null) store.remove(key) else store[key] = text },
    )

    private val chatA = ChatDrafts.Chat("live-A", "stored-A")
    private val chatB = ChatDrafts.Chat("live-B", "stored-B")
    private val newChat = ChatDrafts.Chat(null, null)

    /** Plays the screen: the composer takes what [ChatDrafts.onChatChanged] hands back. */
    private var composer = ""
    private fun open(chat: ChatDrafts.Chat, created: Boolean = false) {
        drafts.onChatChanged(chat, composer, created)?.let { composer = it }
    }

    @Test
    fun `switching A to B to A restores each chat's text`() {
        open(chatA)
        composer = "for A"
        open(chatB)
        assertEquals("", composer)
        composer = "for B"
        open(chatA)
        assertEquals("for A", composer)
        open(chatB)
        assertEquals("for B", composer)
    }

    @Test
    fun `text typed in a new chat moves to it once it has its id`() {
        open(newChat)
        composer = "first words"
        open(ChatDrafts.Chat("live-N", "stored-N"), created = true)
        assertEquals("first words", composer)
        drafts.save(composer)
        assertEquals("first words", store["stored-N"])
        assertNull(store[ChatDrafts.NEW_CHAT])
    }

    @Test
    fun `leaving a new chat for an old one keeps the new chat's text apart`() {
        open(newChat)
        composer = "draft for a new chat"
        open(chatA)
        assertEquals("", composer)
        open(newChat)
        assertEquals("draft for a new chat", composer)
    }

    @Test
    fun `the stored id arriving later is the same chat`() {
        open(ChatDrafts.Chat("live-A", null))
        composer = "typing"
        drafts.save(composer)
        open(chatA)
        assertEquals("typing", composer)
        assertEquals("typing", store["stored-A"])
        assertNull(store["live-A"])
    }

    @Test
    fun `sending clears only that chat's draft`() {
        open(chatA)
        composer = "for A"
        open(chatB)
        composer = "for B"
        drafts.save(composer)
        drafts.clear()
        assertNull(store["stored-B"])
        assertEquals("for A", store["stored-A"])
    }

    @Test
    fun `the old single draft goes to the first real chat`() {
        composer = "old draft"
        open(newChat)
        assertEquals("old draft", composer)
        assertNull(drafts.owner)
        open(chatA)
        assertEquals("old draft", composer)
        assertEquals("old draft", store["stored-A"])
    }

    @Test
    fun `reopening the same chat leaves the composer alone`() {
        open(chatA)
        composer = "half typed"
        open(chatA)
        assertEquals("half typed", composer)
    }

    @Test
    fun `a draft deleted to empty is removed, not restored later`() {
        open(chatA)
        composer = "will delete this"
        drafts.save(composer)
        composer = ""
        drafts.save(composer)
        assertNull(store["stored-A"])
        open(chatB)
        open(chatA)
        assertEquals("", composer)
    }

    @Test
    fun `leaving a chat with an empty composer removes its draft`() {
        open(chatA)
        composer = "x"
        drafts.save(composer)
        composer = ""
        open(chatB)
        assertNull(store["stored-A"])
    }
}

package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalCardModelTest {

    private fun req(id: String, sid: String?, key: String? = null) =
        PendingApprovalUi(requestId = id, sessionId = sid, command = "ls $id", description = "", sessionKey = key)

    private fun chat(id: String, title: String) = SessionItem(id = id, title = title, lastMessagePreview = null, updatedAt = 0L)

    @Test
    fun `nothing waiting means no card`() {
        assertNull(ApprovalCardModel.of(emptyList(), "live-A", "A", emptyList()))
    }

    @Test
    fun `one request of the open chat has no position and no chat line`() {
        val card = ApprovalCardModel.of(listOf(req("a", "live-A")), "live-A", "A", emptyList())
        assertEquals("a", card?.request?.requestId)
        assertEquals(1, card?.total)
        assertFalse(card?.showsPosition ?: true)
        assertNull(card?.otherChatTitle)
    }

    @Test
    fun `the open chat's request comes first and the card says 1 of N`() {
        val queue = listOf(req("b", "live-B", "B"), req("a", "live-A"), req("c", "live-C"))
        val card = ApprovalCardModel.of(queue, "live-A", "A", emptyList())
        assertEquals("a", card?.request?.requestId)
        assertEquals(1, card?.position)
        assertEquals(3, card?.total)
        assertTrue(card?.showsPosition ?: false)
        assertNull(card?.otherChatTitle)
    }

    @Test
    fun `another chat's request names that chat`() {
        val card = ApprovalCardModel.of(listOf(req("b", "live-B", "B")), "live-A", "A", listOf(chat("B", "Deploy")))
        assertEquals("b", card?.request?.requestId)
        assertEquals("Deploy", card?.otherChatTitle)
    }

    @Test
    fun `another chat the drawer doesn't list reads as another chat`() {
        val card = ApprovalCardModel.of(listOf(req("b", "live-B")), "live-A", "A", listOf(chat("Z", "Other")))
        assertEquals("", card?.otherChatTitle)
    }

    @Test
    fun `a request with no chat id belongs to the open chat`() {
        val card = ApprovalCardModel.of(listOf(req("b", "live-B", "B"), req("x", null)), "live-A", "A", emptyList())
        assertEquals("x", card?.request?.requestId)
        assertNull(card?.otherChatTitle)
        assertEquals(2, card?.total)
    }
}

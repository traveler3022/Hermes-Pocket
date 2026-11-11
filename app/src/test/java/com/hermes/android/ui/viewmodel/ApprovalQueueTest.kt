package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApprovalQueueTest {

    private fun req(id: String, sid: String?, key: String? = null, srq: String = "srq-$id") =
        PendingApprovalUi(
            requestId = id,
            sessionId = sid,
            command = "rm -rf /tmp/$id",
            description = "",
            serverRequestId = srq,
            sessionKey = key,
        )

    @Test
    fun `a second chat's request is appended, not replacing the first`() {
        val q = ApprovalQueue.add(ApprovalQueue.add(emptyList(), req("a", "live-A")), req("b", "live-B"))
        assertEquals(listOf("a", "b"), q.map { it.requestId })
    }

    @Test
    fun `a re-delivered request is not queued twice`() {
        val q = ApprovalQueue.add(listOf(req("a", "live-A")), req("a", "live-A"))
        assertEquals(1, q.size)
    }

    @Test
    fun `a re-delivery that knows the stored id fills it in`() {
        val q = ApprovalQueue.add(listOf(req("a", "live-A")), req("a", "live-A", key = "stored-A"))
        assertEquals(1, q.size)
        assertEquals("stored-A", q.single().sessionKey)
    }

    @Test
    fun `answering removes only that request`() {
        val q = ApprovalQueue.remove(listOf(req("a", "A"), req("b", "B")), "a")
        assertEquals(listOf("b"), q.map { it.requestId })
    }

    @Test
    fun `request cancel removes by server id only`() {
        val q = listOf(req("a", "A", srq = "srq-1"), req("b", "B", srq = "srq-2"))
        assertEquals(listOf("a"), ApprovalQueue.removeByServerId(q, "srq-2").map { it.requestId })
        assertEquals(q, ApprovalQueue.removeByServerId(q, "srq-unknown"))
    }

    @Test
    fun `blank server id removes nothing`() {
        val q = listOf(req("a", "A", srq = ""))
        assertEquals(q, ApprovalQueue.removeByServerId(q, ""))
    }

    @Test
    fun `the open chat's oldest request is shown first`() {
        val q = listOf(req("b1", "B"), req("a1", "A"), req("a2", "A"))
        assertEquals("a1", ApprovalQueue.pick(q, activeSessionId = "A", activeSessionKey = null)?.requestId)
    }

    @Test
    fun `the open chat matches by stored id too`() {
        val q = listOf(req("b1", "B", key = "sB"), req("a1", "A-new-live", key = "sA"))
        assertEquals("a1", ApprovalQueue.pick(q, activeSessionId = "A-other", activeSessionKey = "sA")?.requestId)
    }

    @Test
    fun `otherwise the oldest of any chat is shown`() {
        val q = listOf(req("b1", "B"), req("c1", "C"))
        assertEquals("b1", ApprovalQueue.pick(q, activeSessionId = "A", activeSessionKey = "sA")?.requestId)
        assertEquals("b1", ApprovalQueue.pick(q, activeSessionId = null, activeSessionKey = null)?.requestId)
    }

    @Test
    fun `empty queue shows nothing`() {
        assertNull(ApprovalQueue.pick(emptyList(), "A", "sA"))
    }

    @Test
    fun `waiting ids hold every queued chat's live and stored ids`() {
        val q = listOf(req("a", "A", key = "sA"), req("b", "B"), req("c", null))
        assertEquals(setOf("A", "sA", "B"), ApprovalQueue.waitingIds(q))
    }

    @Test
    fun `opening a chat drops only its own requests`() {
        val q = listOf(req("a", "A", key = "sA"), req("b", "B", key = "sB"), req("a2", "A-old", key = "sA"))
        assertEquals(listOf("b"), ApprovalQueue.withoutChat(q, "A", "sA").map { it.requestId })
        assertEquals(q, ApprovalQueue.withoutChat(q, null, null))
    }

    @Test
    fun `another chat's request names that chat`() {
        val sessions = listOf(SessionItem(id = "sB", title = "Deploy script", lastMessagePreview = null, updatedAt = 0))
        val r = req("b", "B", key = "sB")
        assertEquals("Deploy script", ApprovalQueue.otherChatTitle(r, "A", "sA", sessions))
    }

    @Test
    fun `an unlisted other chat gives an empty title and the open chat gives none`() {
        val r = req("b", "B", key = "sB")
        assertEquals("", ApprovalQueue.otherChatTitle(r, "A", "sA", emptyList()))
        assertNull(ApprovalQueue.otherChatTitle(r, "B", null, emptyList()))
        assertNull(ApprovalQueue.otherChatTitle(req("x", null), "A", "sA", emptyList()))
    }
}

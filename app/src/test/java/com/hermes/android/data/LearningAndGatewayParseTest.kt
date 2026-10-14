package com.hermes.android.data

import com.hermes.android.domain.memory.LearningNode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningAndGatewayParseTest {

    @Test
    fun `learning graph from GET api learning graph`() {
        // Trimmed from Hermes 0.21.7.
        val graph = parseLearningGraph(
            Json.parseToJsonElement(
                """
                {"nodes": [
                   {"id": "local-model-inference", "label": "local-model-inference", "kind": "skill",
                    "timestamp": 1791372848, "category": "skills", "useCount": 2, "state": "active",
                    "createdBy": "agent", "pinned": false},
                   {"id": "memory:profile:6:9a08e7a91545", "label": "Prefers Persian", "kind": "memory",
                    "memorySource": "profile", "timestamp": null, "category": "memory", "useCount": 0,
                    "state": "active", "createdBy": "memory", "pinned": false},
                   {"label": "no id, dropped"}
                 ],
                 "edges": [{"source": "memory:profile:6:9a08e7a91545", "target": "local-model-inference"}],
                 "clusters": [], "memory": [], "stats": {}}
                """,
            ),
        )
        assertEquals(2, graph.nodes.size)
        val skill = graph.nodes[0]
        assertEquals(LearningNode.Kind.SKILL, skill.kind)
        assertEquals(1791372848L, skill.timestamp)
        assertEquals(2, skill.useCount)
        assertEquals("agent", skill.createdBy)
        val memory = graph.nodes[1]
        assertEquals(LearningNode.Kind.MEMORY, memory.kind)
        assertNull(memory.timestamp)
        assertEquals("profile", memory.memorySource)
        assertEquals(setOf("local-model-inference"), graph.links["memory:profile:6:9a08e7a91545"])
        assertEquals(setOf("memory:profile:6:9a08e7a91545"), graph.links["local-model-inference"])
    }

    @Test
    fun `gateway status from GET api status`() {
        val status = parseGatewayStatus(
            Json.parseToJsonElement(
                """
                {"version": "0.21.7", "gateway_running": true, "gateway_state": "running",
                 "gateway_platforms": {"telegram": {"state": "connected", "error_code": null, "error_message": null},
                                       "discord": {"state": "fatal", "error_message": "bad token"}},
                 "gateway_exit_reason": null, "gateway_shared_with": null}
                """,
            ),
        )
        assertTrue(status.running)
        assertEquals("running", status.state)
        assertNull(status.exitReason)
        assertEquals(listOf("telegram", "discord"), status.platforms.map { it.name })
        assertNull(status.platforms[0].error)
        assertEquals("bad token", status.platforms[1].error)
        assertTrue(status.sharedWith.isEmpty())
    }

    @Test
    fun `stopped gateway on an older Hermes`() {
        val status = parseGatewayStatus(Json.parseToJsonElement("""{"gateway_running": false}"""))
        assertFalse(status.running)
        assertNull(status.state)
        assertTrue(status.platforms.isEmpty())
    }
}

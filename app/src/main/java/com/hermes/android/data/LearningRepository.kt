package com.hermes.android.data

import com.hermes.android.domain.memory.LearningGraph
import com.hermes.android.domain.memory.LearningJourney
import com.hermes.android.domain.memory.LearningNode
import com.hermes.android.domain.memory.LearningResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The desktop's learning panel routes: `GET /api/learning/graph`, `GET|PUT|DELETE
 * /api/learning/node` (hermes_cli/web_routers/status.py → agent/learning_graph.py,
 * agent/learning_mutations.py). Not the `learning.*` RPCs: those take no profile and act on
 * the gateway's own home, and there is no RPC for the graph itself. The built-in Linux
 * serves these routes in-process (android.dashboard), so one path covers every runtime.
 */
@Singleton
class LearningRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : LearningJourney {

    override suspend fun graph(): LearningGraph = parseLearningGraph(dashboard.call("GET", "/api/learning/graph"))

    override suspend fun detail(id: String): String {
        val root = dashboard.call("GET", "/api/learning/node?id=${id.urlPart()}") as? JsonObject
            ?: throw IOException("No answer from Hermes")
        return (root["content"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    override suspend fun edit(id: String, content: String): LearningResult = mutate {
        dashboard.call(
            "PUT",
            "/api/learning/node",
            JsonObject(mapOf("id" to JsonPrimitive(id), "content" to JsonPrimitive(content))),
        )
    }

    override suspend fun delete(id: String): LearningResult = mutate {
        dashboard.call("DELETE", "/api/learning/node", JsonObject(mapOf("id" to JsonPrimitive(id))))
    }

    /** Hermes answers `{ok, message}`, or 400 with the reason (pinned skill, stale memory id…). */
    private suspend fun mutate(call: suspend () -> JsonElement): LearningResult = try {
        val message = ((call() as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty()
        LearningResult.Done(message)
    } catch (e: IOException) {
        LearningResult.Failed(e.message ?: "Hermes refused")
    }

    private fun String.urlPart(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

/** `GET /api/learning/graph`: `{nodes: [{id, label, kind, timestamp, category, useCount, …}], edges: [{source, target}], …}`. */
internal fun parseLearningGraph(result: JsonElement): LearningGraph {
    val root = result as? JsonObject ?: JsonObject(emptyMap())
    val nodes = (root["nodes"] as? JsonArray).orEmpty().mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val id = row.text("id") ?: return@mapNotNull null
        val kind = if (row.text("kind") == "memory" || id.startsWith("memory:")) {
            LearningNode.Kind.MEMORY
        } else {
            LearningNode.Kind.SKILL
        }
        LearningNode(
            id = id,
            label = row.text("label") ?: id,
            kind = kind,
            category = row.text("category") ?: if (kind == LearningNode.Kind.MEMORY) "memory" else "other",
            timestamp = (row["timestamp"] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() },
            useCount = (row["useCount"] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }?.toInt() ?: 0,
            state = row.text("state") ?: "active",
            createdBy = row.text("createdBy").orEmpty(),
            pinned = (row["pinned"] as? JsonPrimitive)?.booleanOrNull == true,
            memorySource = row.text("memorySource"),
        )
    }
    val links = mutableMapOf<String, MutableSet<String>>()
    (root["edges"] as? JsonArray).orEmpty().forEach { element ->
        val edge = element as? JsonObject ?: return@forEach
        val a = edge.text("source") ?: return@forEach
        val b = edge.text("target") ?: return@forEach
        links.getOrPut(a) { mutableSetOf() }.add(b)
        links.getOrPut(b) { mutableSetOf() }.add(a)
    }
    return LearningGraph(nodes = nodes, links = links)
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

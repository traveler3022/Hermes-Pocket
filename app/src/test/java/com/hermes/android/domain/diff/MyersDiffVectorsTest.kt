package com.hermes.android.domain.diff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adapted from slopus/happy (MIT License, Copyright (c) 2026 Happy Coder Contributors):
 * DiffEngineVectorsTest.jsdiffVectors and its resource diff/jsdiff-vectors.json, which
 * happy's tools/diff/generate-vectors.mjs produced by running jsdiff 8.0.3 over seeded
 * cases: diffWordsWithSpace, diffLines (with and without maxEditLength) and diffArrays
 * with a whitespace-blind comparator. The port must give jsdiff's answers exactly.
 */
class MyersDiffVectorsTest {
    private fun cases(): List<JsonObject> {
        val url = requireNotNull(javaClass.classLoader?.getResource("diff/jsdiff-vectors.json")) { "missing diff/jsdiff-vectors.json" }
        return Json.parseToJsonElement(url.readText()).jsonObject.getValue("cases").jsonArray.map { it.jsonObject }
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private fun <T> changesJson(changes: List<MyersDiff.Change<T>>?, value: (T) -> JsonElement): JsonElement =
        changes?.let { list ->
            JsonArray(
                list.map {
                    buildJsonObject {
                        put("value", value(it.value))
                        put("count", it.count)
                        put("added", it.added)
                        put("removed", it.removed)
                    }
                },
            )
        } ?: JsonNull

    @Test
    fun `myers port matches jsdiff on every vector`() {
        val all = cases()
        assertTrue(all.size > 300)
        for ((index, case) in all.withIndex()) {
            val actual = when (case.str("kind")) {
                "words" -> changesJson(MyersDiff.diffWordsWithSpace(case.str("old"), case.str("new"))) { JsonPrimitive(it) }
                "lines" -> {
                    val budget = MyersDiff.Budget(maxEditLength = case["maxEditLength"]?.jsonPrimitive?.int)
                    changesJson(MyersDiff.diffLines(case.str("old"), case.str("new"), budget)) { JsonPrimitive(it) }
                }
                "arrays" -> {
                    val old = case.getValue("old").jsonArray.map { it.jsonPrimitive.content }
                    val new = case.getValue("new").jsonArray.map { it.jsonPrimitive.content }
                    changesJson(MyersDiff.diffArrays(old, new, ::stripJsWhitespace)) { list -> JsonArray(list.map(::JsonPrimitive)) }
                }
                else -> error("unknown case ${case.str("kind")}")
            }
            assertEquals("case $index: $case", normalize(case.getValue("expected")), actual)
        }
    }

    /** Booleans written as absent read as false. */
    private fun normalize(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map(::normalize))
        is JsonObject -> if ("count" in element) {
            buildJsonObject {
                put("value", element.getValue("value"))
                put("count", element.getValue("count").jsonPrimitive.int)
                put("added", element["added"]?.jsonPrimitive?.booleanOrNull ?: false)
                put("removed", element["removed"]?.jsonPrimitive?.booleanOrNull ?: false)
            }
        } else {
            element
        }
        else -> element
    }
}

package com.hermes.android.data.syntax

import com.hermes.android.domain.syntax.CodeTokenizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adapted from slopus/happy (MIT License, Copyright (c) 2026 Happy Coder Contributors).
 *
 * The port of Prism's tokenizer against Prism 1.30.0 itself: test resource
 * prism/prism-reference.json holds, for one sample file per grammar, the runs
 * happy-app's highlightLines got from PrismJS (kind and length, per line).
 * [runs] is that function over our [PrismTokenizer], so a mismatch is an engine or
 * grammar-table bug, not a palette choice.
 */
class PrismCorpusTest {
    private val prism = shared


    private data class Run(val kind: String, val length: Int)

    /** happy-app highlight.ts classify(): Prism token names to its wire kinds. */
    private fun classify(type: String): String = when (type) {
        "keyword", "boolean", "null", "important", "atrule", "rule", "directive", "entity" -> "keyword"
        "string", "char", "regex", "template-string", "attr-value", "url" -> "string"
        "comment", "prolog", "doctype", "cdata", "docstring" -> "comment"
        "number", "hexcode", "unit" -> "number"
        "function", "method", "function-variable", "macro" -> "function"
        "class-name", "builtin", "symbol", "namespace", "selector" -> "type"
        "operator", "arrow" -> "operator"
        "punctuation", "interpolation-punctuation" -> "punctuation"
        "tag", "script" -> "tag"
        "attr-name", "property", "parameter" -> "attr"
        "variable", "constant" -> "variable"
        else -> "plain"
    }

    /** highlightLines: one run list per `\n`-separated line. */
    private fun runs(text: String, language: String): List<List<Run>> {
        val tokens = prism.tokenize(text, language) ?: error("no grammar $language")
        val out = ArrayList<List<Run>>()
        var current = ArrayList<Run>()
        fun push(kind: String, length: Int) {
            if (length <= 0) return
            val last = current.lastOrNull()
            if (last != null && last.kind == kind) current[current.lastIndex] = Run(kind, last.length + length) else current.add(Run(kind, length))
        }
        fun emit(location: Int, length: Int, kind: String) {
            var start = location
            val end = location + length
            while (true) {
                val newline = text.indexOf('\n', start).takeIf { it in start until end }
                if (newline == null) {
                    push(kind, end - start)
                    return
                }
                push(kind, newline - start)
                out.add(current)
                current = ArrayList()
                start = newline + 1
            }
        }
        fun walk(nodes: List<CodeTokenizer.Node>, inherited: String) {
            for (node in nodes) when (node) {
                is CodeTokenizer.Node.Text -> emit(node.location, node.length, inherited)
                is CodeTokenizer.Node.Token -> {
                    val kind = classify(node.name)
                    walk(node.content, if (kind == "plain") inherited else kind)
                }
            }
        }
        walk(tokens, "plain")
        out.add(current)
        return out
    }

    @Test
    fun everyPatternCompiles() {
        assertTrue(prism.compileAll() > 1000)
        assertEquals(45, prism.languages.size)
    }

    @Test
    fun matchesPrism() {
        val text = requireNotNull(javaClass.classLoader?.getResource("prism/prism-reference.json")).readText()
        val entries = Json.parseToJsonElement(text).jsonArray
        assertTrue(entries.size >= 46)
        val failures = ArrayList<String>()
        for (entry in entries) {
            val e = entry.jsonObject
            val language = e.getValue("lang").jsonPrimitive.content
            val source = e.getValue("text").jsonPrimitive.content
            val expected = e.getValue("runs").jsonArray.map { line ->
                line.jsonArray.map { run ->
                    val r = run.jsonObject
                    Run(r.getValue("k").jsonPrimitive.content, r.getValue("n").jsonPrimitive.int)
                }
            }
            val actual = runs(source, language)
            if (actual.none { line -> line.any { it.kind != "plain" } }) failures.add("$language colours nothing")
            if (actual != expected) {
                val line = expected.indices.firstOrNull { expected.getOrNull(it) != actual.getOrNull(it) } ?: -1
                failures.add("$language line ${line + 1}: expected ${expected.getOrNull(line)} actual ${actual.getOrNull(line)}")
            }
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    private companion object {
        /** Parsed once for the class, as the app's @Singleton is. */
        val shared = PrismTokenizer()
    }
}

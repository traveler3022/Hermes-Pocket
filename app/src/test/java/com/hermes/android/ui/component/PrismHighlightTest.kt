package com.hermes.android.ui.component

import com.hermes.android.ui.component.syntax.Prism
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CodeHighlighter.spans] over Prism, the diff rules and the fallback. */
class PrismHighlightTest {

    private fun tokens(code: String, lang: String): List<Pair<String, CodeToken>> =
        CodeHighlighter.spans(code, lang).map { code.substring(it.start, it.end) to it.token }

    private fun assertOrderedInside(code: String, spans: List<CodeSpan>) {
        var last = 0
        spans.forEach {
            assertTrue("$it", it.start >= last && it.end > it.start && it.end <= code.length)
            last = it.end
        }
    }

    @Test
    fun `the hard-coded grammar ids match the table`() {
        assertEquals(Prism.languages, CodeHighlighter.PRISM_IDS)
    }

    @Test
    fun `kotlin goes through prism`() {
        val code = "@JvmStatic fun f(x: Int) = \"s\" // c"
        val t = tokens(code, "kt")
        assertTrue(t.toString(), "fun" to CodeToken.Keyword in t)
        assertTrue(t.toString(), "f" to CodeToken.Function in t)
        assertTrue(t.toString(), "\"s\"" to CodeToken.Str in t)
        assertTrue(t.toString(), "// c" to CodeToken.Comment in t)
        assertTrue(t.toString(), t.any { it.second == CodeToken.Annotation && it.first.startsWith("@JvmStatic") })
    }

    @Test
    fun `python keeps a hash inside a string as string`() {
        val t = tokens("x = \"a # b\"  # real", "python")
        assertEquals(listOf("\"a # b\"" to CodeToken.Str, "# real" to CodeToken.Comment), t)
    }

    @Test
    fun `typescript template interpolation is inside the string colour or its own`() {
        val code = "const s = `a \${b} c`;"
        val spans = CodeHighlighter.spans(code, "ts")
        assertOrderedInside(code, spans)
        assertTrue(tokens(code, "ts").first() == ("const" to CodeToken.Keyword))
        assertTrue(spans.any { it.token == CodeToken.Str })
    }

    @Test
    fun `json keys are attributes and values strings`() {
        val t = tokens("{\"a\": \"b\", \"n\": 12, \"t\": true}", "json")
        assertTrue(t.toString(), "\"a\"" to CodeToken.Attr in t)
        assertTrue(t.toString(), "\"b\"" to CodeToken.Str in t)
        assertTrue(t.toString(), "12" to CodeToken.Number in t)
        assertTrue(t.toString(), "true" to CodeToken.Literal in t)
    }

    @Test
    fun `html tags and attributes`() {
        val t = tokens("<a href=\"x\">y</a>", "html")
        assertTrue(t.toString(), t.any { it.second == CodeToken.Tag })
        assertTrue(t.toString(), "href" to CodeToken.Attr in t)
    }

    @Test
    fun `a diff colours added removed and header lines`() {
        val code = "diff --git a/f b/f\n--- a/f\n+++ b/f\n@@ -1,2 +1,2 @@\n ctx\n-old\n+new\n\\ No newline at end of file"
        assertEquals(
            listOf(
                "diff --git a/f b/f" to CodeToken.Keyword,
                "--- a/f" to CodeToken.Keyword,
                "+++ b/f" to CodeToken.Keyword,
                "@@ -1,2 +1,2 @@" to CodeToken.Annotation,
                "-old" to CodeToken.Deleted,
                "+new" to CodeToken.Inserted,
                "\\ No newline at end of file" to CodeToken.Comment,
            ),
            tokens(code, "diff"),
        )
        assertEquals(tokens(code, "diff"), tokens(code, " Patch "))
    }

    @Test
    fun `a diff with a trailing newline and empty lines`() {
        assertEquals(listOf("+a" to CodeToken.Inserted, "-b" to CodeToken.Deleted), tokens("+a\n\n-b\n", "diff"))
    }

    @Test
    fun `languages prism lacks fall back to the rules`() {
        assertFalse(CodeHighlighter.usesPrism("groovy"))
        assertFalse(CodeHighlighter.usesPrism(""))
        assertFalse(CodeHighlighter.usesPrism("diff"))
        val code = "def x = 'a' // c"
        assertEquals(CodeHighlighter.ruleSpans(code, "groovy"), CodeHighlighter.spans(code, "groovy"))
        assertEquals(CodeHighlighter.ruleSpans(code, ""), CodeHighlighter.spans(code, ""))
    }

    @Test
    fun `aliases reach their grammar`() {
        listOf("kt", "py", "sh", "yml", "ts", "rs", "golang", "c++", "html", "dockerfile", "console", "md").forEach {
            assertTrue(it, CodeHighlighter.usesPrism(it))
            assertTrue(it, CodeHighlighter.prismId(it) != null)
        }
    }

    @Test
    fun `a long unterminated string near the cap does not throw`() {
        val code = "val s = \"" + "a".repeat(CodeHighlighter.MAX_LENGTH - 20)
        val spans = CodeHighlighter.spans(code, "kotlin")
        assertOrderedInside(code, spans)
    }

    @Test
    fun `spans stay ordered for every grammar on mixed input`() {
        val code = "/* x */ # y\n<a b='c'> \"s\" `t \${u}` 0x1F fun def\n-- z\n@@ +a"
        CodeHighlighter.PRISM_IDS.forEach { id -> assertOrderedInside(code, CodeHighlighter.spans(code, id)) }
    }

    @Test
    fun `huge code is left plain for prism too`() {
        assertTrue(CodeHighlighter.spans("val x = 1\n".repeat(4000), "kotlin").isEmpty())
        assertTrue(CodeHighlighter.spans("+a\n".repeat(20_000), "diff").isEmpty())
    }
}

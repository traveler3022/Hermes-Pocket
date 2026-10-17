package com.hermes.android.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {

    private fun tokens(code: String, lang: String): List<Pair<String, CodeToken>> =
        CodeHighlighter.spans(code, lang).map { code.substring(it.start, it.end) to it.token }

    @Test
    fun `a comment marker inside a string stays string`() {
        val t = tokens("val url = \"http://x\" // note", "kotlin")
        assertEquals(
            listOf("val" to CodeToken.Keyword, "\"http://x\"" to CodeToken.Str, "// note" to CodeToken.Comment),
            t,
        )
    }

    @Test
    fun `python uses hash comments and its own literals`() {
        val t = tokens("def f():\n    return None  # done", "python")
        assertEquals(
            listOf(
                "def" to CodeToken.Keyword,
                "return" to CodeToken.Keyword,
                "None" to CodeToken.Literal,
                "# done" to CodeToken.Comment,
            ),
            t,
        )
    }

    @Test
    fun `shell variables and a hash inside a word`() {
        val t = tokens("echo \$HOME a#b \${X}", "bash")
        assertEquals(listOf("echo" to CodeToken.Keyword, "\$HOME" to CodeToken.Type, "\${X}" to CodeToken.Type), t)
    }

    @Test
    fun `json keys differ from values`() {
        val t = tokens("{\"a\": \"b\", \"n\": 12}", "json")
        assertEquals(
            listOf("\"a\"" to CodeToken.Keyword, "\"b\"" to CodeToken.Str, "\"n\"" to CodeToken.Keyword, "12" to CodeToken.Number),
            t,
        )
    }

    @Test
    fun `spans never overlap and stay inside the text`() {
        val code = "/* open\nfun x() = \"unterminated\n1..10 @Ann 0x1F 'c'"
        val spans = CodeHighlighter.spans(code, "kotlin")
        var last = 0
        spans.forEach {
            assertTrue(it.start >= last && it.end > it.start && it.end <= code.length)
            last = it.end
        }
    }

    @Test
    fun `huge code is left plain`() {
        assertTrue(CodeHighlighter.spans("val x = 1\n".repeat(4000), "kotlin").isEmpty())
    }
}

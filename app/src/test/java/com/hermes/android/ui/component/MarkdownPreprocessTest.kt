package com.hermes.android.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cases mirror Hermes Desktop's markdown-preprocess and markdown-code tests. */
class MarkdownPreprocessTest {

    private fun pre(text: String) = preprocessAssistantMarkdown(text)

    // ── Reasoning ──

    @Test
    fun `a stripped reasoning block does not fuse the words around it`() {
        assertEquals("no Hermes Desktop", pre("no<thinking>hmm</thinking> Hermes Desktop"))
        assertEquals("backend e o useSmoothReveal", pre("backend e o <thinking>x</thinking>useSmoothReveal"))
        assertEquals("Resposta final.", pre("<thought>x</thought>\n\nResposta final."))
        assertEquals("no Hermes", pre("no<thinking>a</thinking><think>b</think>Hermes"))
    }

    @Test
    fun `an unterminated reasoning block is hidden only at a block boundary`() {
        assertEquals("", pre("<thinking>let me think about the render pipeline"))
        assertEquals("Resposta final.\n", pre("Resposta final.\n<reasoning_scratchpad>checking"))
        assertEquals("Answer.\n", pre("Answer.\n<thin"))
        assertEquals("Answer.\n<div", pre("Answer.\n<div"))
        val quoted = "The text above explains the <thinking> block without closing it"
        assertEquals(quoted, pre(quoted))
    }

    // ── Fences ──

    @Test
    fun `listings that contain fence characters stay intact`() {
        val listings = listOf(
            listOf("```python", "def is_fence(line):", "    return line.startswith(\"```\")", "first = parts[0]", "```"),
            listOf("````markdown", "```js", "const a = arr[0];", "```", "````"),
            listOf("~~~md", "```sh", "export A=\$5; echo x", "```", "~~~"),
            listOf("```js", "const tilde = \"~~~ and \$5 [1]\"", "```"),
        ).map { it.joinToString("\n") }
        for (listing in listings) {
            val output = pre("Example:\n\n$listing\n\nAfter the example.")
            assertTrue(output, output.endsWith("$listing\n\nAfter the example."))
        }
    }

    @Test
    fun `an empty fence is dropped`() {
        val out = pre("Before\n\n```\n```\n\nAfter")
        assertFalse(out, out.contains("`"))
        assertEquals("Before\n\nAfter", out.replace(Regex("\n{3,}"), "\n\n"))
    }

    @Test
    fun `a fence around urls only is unwrapped`() {
        assertEquals("See:\nhttps://example.com/a\nhttps://example.com/b", pre("See:\n```\nhttps://example.com/a\nhttps://example.com/b\n```"))
    }

    @Test
    fun `a fence around a plain paragraph is unwrapped, in Persian too`() {
        val english = "This is a sentence about rendering.\nIt keeps going here.\nAnd a third line of prose."
        assertEquals(english, pre("```\n$english\n```"))
        val persian = "این یک جمله دربارهٔ رندر است.\nادامهٔ همان متن.\nو خط سوم متن."
        assertEquals(persian, pre("```text\n$persian\n```"))
    }

    @Test
    fun `real code and config keep their fence`() {
        val code = "```ts\nconst value = { bunny: true };\nreturn value\n```"
        assertEquals(code, pre(code))
        val ssh = "```\nHost myserver\n    HostName 10.0.0.1\n    Port 22\n```"
        assertEquals(ssh, pre(ssh))
        val env = "```\nAPI_KEY=abc123\nPORT=8080\nDEBUG=true\n```"
        assertEquals(env, pre(env))
    }

    @Test
    fun `a code fence keeps only its language`() {
        assertEquals("```python\nprint(1)\n```", pre("```python title=\"x.py\"\nprint(1)\n```"))
    }

    @Test
    fun `stray backticks outside fences are scrubbed`() {
        assertEquals("Done.", pre("Done.```"))
        assertEquals("a  b", pre("a ```` b").replace("`", ""))
    }

    @Test
    fun `a streaming code fence with a language is kept open`() {
        assertEquals("```kotlin\nval x = 1", pre("```kotlin\nval x = 1"))
    }

    @Test
    fun `prose detection matches Desktop's cases`() {
        assertFalse(isLikelyProseFence("", "Host myserver\n    HostName 10.0.0.1"))
        assertTrue(isLikelyStructuredText("Host myserver\nUser teknium\nPort 22"))
        assertTrue(isLikelyStructuredText("API_KEY=abc123\nPORT=8080\nDEBUG=true"))
        assertFalse(isLikelyStructuredText("Port 22"))
    }

    // ── Citations ──

    @Test
    fun `transport citation markers are stripped`() {
        assertEquals(
            "Ice floats because it is less dense than liquid water.",
            pre("Ice floats because it is less dense than liquid water.citeturn0search0"),
        )
        assertEquals("answer here and continues", pre("answer hereciteturn0search11turn2search0 and continues"))
        assertEquals("| Firm | US |", pre("| Firmciteturn0search1 | US |"))
        assertEquals("See  for details.", pre("See citeturn3search7 for details."))
        assertEquals("Still typing", pre("Still typingciteturn0search0"))
        assertEquals("wordword2", pre("wordciteturn0search0word2"))
        assertEquals("Label  stays", pre("Label  stays"))
    }

    @Test
    fun `citation markers inside code and math are kept`() {
        assertTrue(pre("run `x = citeturn0search0` please").contains("citeturn"))
        assertEquals("\$\\sqrt[3]{8}\$ and ", pre("\$\\sqrt[3]{8}\$ and citeturn0search0"))
    }

    @Test
    fun `numeric markers stay only when the Sources list has them`() {
        assertEquals("Both shapes and  go away.", pre("Both shapes[1] and citeturn0search0 go away."))
        val withSources = "Water boils at 100C.[1]\n\n## Sources\n[1] https://example.com/water"
        assertEquals(withSources, pre(withSources))
        assertEquals(
            "Water boils at 100C.\n\n## Sources\n[1] https://example.com/water",
            pre("Water boils at 100C.[2]\n\n## Sources\n[1] https://example.com/water"),
        )
    }

    @Test
    fun `plain text passes through unchanged`() {
        val text = "سلام! این یک پاسخ عادی است با **بولد** و `code` و [لینک](https://example.com)."
        assertEquals(text, pre(text))
        assertEquals("", pre(""))
    }
}

package com.hermes.android.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The markdown renderer is what every reply, tool result and release note goes
 * through, so its parser is pinned here without Compose: blocks, inline syntax,
 * the streaming shortcut, and which way a block reads.
 */
class HermesMarkdownParserTest {

    private val style = InlineStyle(linkColor = Color.Blue, codeBg = Color.Gray, onCodeBg = Color.Black)

    // ── Blocks ───────────────────────────────────────────────────────────

    @Test
    fun `a blank line ends a paragraph and single newlines stay inside it`() {
        assertEquals(
            listOf(MdBlock.Para("a\nb"), MdBlock.Para("c")),
            parseMdBlocks("a\nb\n\nc"),
        )
    }

    @Test
    fun `headings carry their level`() {
        assertEquals(listOf(MdBlock.Heading(2, "Title")), parseMdBlocks("## Title"))
        // No space after the hashes: a hashtag, not a heading.
        assertEquals(listOf(MdBlock.Para("#tag")), parseMdBlocks("#tag"))
    }

    @Test
    fun `nested bullets take their depth from the indent`() {
        assertEquals(
            listOf(
                MdBlock.ListItem(0, null, null, "one"),
                MdBlock.ListItem(1, null, null, "two"),
            ),
            parseMdBlocks("- one\n  - two"),
        )
    }

    @Test
    fun `an over-indented item is capped instead of pushing text off screen`() {
        val item = parseMdBlocks(" ".repeat(20) + "- x").single() as MdBlock.ListItem
        assertEquals(6, item.depth)
    }

    @Test
    fun `numbered items keep their number`() {
        assertEquals(
            listOf(
                MdBlock.ListItem(0, 1, null, "a"),
                MdBlock.ListItem(0, 2, null, "b"),
            ),
            parseMdBlocks("1. a\n2) b"),
        )
    }

    @Test
    fun `task items know whether they are checked`() {
        assertEquals(
            listOf(
                MdBlock.ListItem(0, null, true, "done"),
                MdBlock.ListItem(0, null, false, "todo"),
            ),
            parseMdBlocks("- [x] done\n- [ ] todo"),
        )
    }

    @Test
    fun `a line that opens with bold is a paragraph, not a bullet`() {
        assertEquals(listOf(MdBlock.Para("**bold** text")), parseMdBlocks("**bold** text"))
    }

    @Test
    fun `a table needs its separator row and ends at the first non-table line`() {
        assertEquals(
            listOf(MdBlock.Table(listOf(listOf("a", "b"), listOf("1", "2")), hasHeader = true)),
            parseMdBlocks("| a | b |\n|---|---|\n| 1 | 2 |"),
        )
        // Without the separator it is just text.
        assertEquals(listOf(MdBlock.Para("| a | b |")), parseMdBlocks("| a | b |"))
    }

    @Test
    fun `a thematic break is its own block`() {
        assertEquals(
            listOf(MdBlock.Para("text"), MdBlock.Rule, MdBlock.Para("more")),
            parseMdBlocks("text\n\n---\n\nmore"),
        )
    }

    @Test
    fun `fenced code keeps its language and an unfinished fence still renders`() {
        assertEquals(
            listOf(MdBlock.Code("kotlin", "val x = 1")),
            parseMdBlocks("```kotlin\nval x = 1\n```"),
        )
        assertEquals(listOf(MdBlock.Code("", "abc")), parseMdBlocks("```\nabc"))
    }

    @Test
    fun `quotes carry their depth`() {
        assertEquals(listOf(MdBlock.Quote(2, "deep")), parseMdBlocks("> > deep"))
    }

    @Test
    fun `windows line endings parse like unix ones`() {
        assertEquals(
            listOf(MdBlock.Heading(1, "T"), MdBlock.Para("body")),
            parseMdBlocks("# T\r\n\r\nbody"),
        )
    }

    @Test
    fun `nothing to parse gives nothing`() {
        assertTrue(parseMdBlocks("").isEmpty())
        assertTrue(parseMdBlocks("  \n \n").isEmpty())
    }

    // ── Streaming ────────────────────────────────────────────────────────

    private val document = "# Title\n\nFirst para\nline two\n\n- a\n- b\n\n" +
        "```kotlin\nval x = 1\n\nval y = 2\n```\n\n" +
        "| a | b |\n|---|---|\n| 1 | 2 |\n\nend"

    @Test
    fun `parsing a reply as it grows gives what parsing it whole gives, at every length`() {
        val incremental = IncrementalMdParser()
        for (length in 1..document.length) {
            val prefix = document.substring(0, length)
            assertEquals("prefix of $length chars", parseMdBlocks(prefix), incremental.parse(prefix))
        }
    }

    @Test
    fun `text that is not a continuation is parsed from scratch`() {
        val incremental = IncrementalMdParser()
        incremental.parse("first\n\nsecond")
        assertEquals(parseMdBlocks("x\n\ny"), incremental.parse("x\n\ny"))
        // Shorter than what came before: an edit, not growth.
        assertEquals(parseMdBlocks("x"), incremental.parse("x"))
    }

    // ── Inline ───────────────────────────────────────────────────────────

    @Test
    fun `identifiers and globs are not emphasis`() {
        val out = inline("snake_case_name and *.kt", style)
        assertEquals("snake_case_name and *.kt", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun `bold is bold and its markers are gone`() {
        val out = inline("a **b** c", style)
        assertEquals("a b c", out.text)
        val span = out.spanStyles.single()
        assertEquals(FontWeight.Bold, span.item.fontWeight)
        assertEquals(2, span.start)
        assertEquals(3, span.end)
    }

    @Test
    fun `an escaped star is a star`() {
        val out = inline("\\*not italic\\*", style)
        assertEquals("*not italic*", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun `inline code drops its backticks and is styled`() {
        val out = inline("run `ls -la` now", style)
        assertEquals("run ls -la now", out.text)
        assertEquals(1, out.spanStyles.size)
    }

    @Test
    fun `a markdown link becomes a link over its label`() {
        val out = inline("[docs](https://x.dev/a) ok", style)
        assertEquals("docs ok", out.text)
        val link = out.getLinkAnnotations(0, out.text.length).single().item as LinkAnnotation.Url
        assertEquals("https://x.dev/a", link.url)
    }

    @Test
    fun `a bare url becomes a link without the punctuation that follows it`() {
        val out = inline("see https://a.io/x, ok", style)
        assertEquals("see https://a.io/x, ok", out.text)
        val link = out.getLinkAnnotations(0, out.text.length).single().item as LinkAnnotation.Url
        assertEquals("https://a.io/x", link.url)
    }

    // ── Direction ────────────────────────────────────────────────────────

    @Test
    fun `persian reads right to left and english left to right`() {
        assertEquals(true, isRtlText("سلام دنیا"))
        assertEquals(false, isRtlText("hello world"))
    }

    @Test
    fun `the language with more letters wins, not whichever came first`() {
        assertEquals(true, isRtlText("برای نصب باید git را اجرا کنید"))
        assertEquals(false, isRtlText("Install the package پایتون now"))
    }

    @Test
    fun `code and urls do not count toward direction`() {
        assertEquals(true, isRtlText("`git status` نمایش وضعیت"))
        assertEquals(true, isRtlText("https://example.com سلام"))
    }

    @Test
    fun `a tie goes to the first letter, and no letters means no opinion`() {
        assertEquals(false, isRtlText("ab آب"))
        assertEquals(true, isRtlText("آب ab"))
        assertNull(isRtlText("12345 !?"))
    }
}

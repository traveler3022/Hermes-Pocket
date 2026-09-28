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

    private fun t(text: String) = listOf(MdSpan.Text(text))
    private fun para(text: String) = MdBlock.Para(t(text))
    private fun item(depth: Int, ordinal: Int?, checked: Boolean?, text: String) =
        MdBlock.ListItem(depth, ordinal, checked, t(text))
    private fun inline(text: String, s: InlineStyle) = inline(parseInline(text), s)

    // ── Blocks ───────────────────────────────────────────────────────────

    @Test
    fun `a blank line ends a paragraph and single newlines stay inside it`() {
        assertEquals(
            listOf(para("a\nb"), para("c")),
            parseMdBlocks("a\nb\n\nc"),
        )
    }

    @Test
    fun `headings carry their level`() {
        assertEquals(listOf(MdBlock.Heading(2, t("Title"))), parseMdBlocks("## Title"))
        // No space after the hashes: a hashtag, not a heading.
        assertEquals(listOf(para("#tag")), parseMdBlocks("#tag"))
    }

    @Test
    fun `nested bullets take their depth from the indent`() {
        assertEquals(
            listOf(
                item(0, null, null, "one"),
                item(1, null, null, "two"),
            ),
            parseMdBlocks("- one\n  - two"),
        )
    }

    @Test
    fun `a deeply nested item is capped instead of pushing text off screen`() {
        val md = (0..8).joinToString("\n") { "  ".repeat(it) + "- x$it" }
        val last = parseMdBlocks(md).last() as MdBlock.ListItem
        assertEquals(6, last.depth)
    }

    @Test
    fun `a list keeps counting across blank lines even when every item says one`() {
        assertEquals(
            listOf(item(0, 1, null, "a"), item(0, 2, null, "b")),
            parseMdBlocks("1. a\n\n1. b"),
        )
    }

    @Test
    fun `what an item holds after its first line sits one level deeper`() {
        assertEquals(
            listOf(item(0, null, null, "a"), item(1, null, null, "b"), para("more")),
            parseMdBlocks("- a\n\n  - b\n\n  more"),
        )
    }

    @Test
    fun `numbered items keep their number`() {
        assertEquals(
            listOf(
                item(0, 1, null, "a"),
                item(0, 2, null, "b"),
            ),
            parseMdBlocks("1. a\n2) b"),
        )
    }

    @Test
    fun `task items know whether they are checked`() {
        assertEquals(
            listOf(
                item(0, null, true, "done"),
                item(0, null, false, "todo"),
            ),
            parseMdBlocks("- [x] done\n- [ ] todo"),
        )
    }

    @Test
    fun `a line that opens with bold is a paragraph, not a bullet`() {
        assertEquals(
            listOf(MdBlock.Para(listOf(MdSpan.Styled(MdStyle.Bold, t("bold")), MdSpan.Text(" text")))),
            parseMdBlocks("**bold** text"),
        )
    }

    @Test
    fun `a table needs its separator row and ends at the first non-table line`() {
        assertEquals(
            listOf(MdBlock.Table(listOf(listOf(t("a"), t("b")), listOf(t("1"), t("2"))), hasHeader = true)),
            parseMdBlocks("| a | b |\n|---|---|\n| 1 | 2 |"),
        )
        // Without the separator it is just text.
        assertEquals(listOf(para("| a | b |")), parseMdBlocks("| a | b |"))
    }

    @Test
    fun `a thematic break is its own block`() {
        assertEquals(
            listOf(para("text"), MdBlock.Rule, para("more")),
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
        assertEquals(listOf(MdBlock.Quote(2, t("deep"))), parseMdBlocks("> > deep"))
        // Lines of one quote are one block.
        assertEquals(listOf(MdBlock.Quote(1, t("a\nb"))), parseMdBlocks("> a\n> b"))
    }

    @Test
    fun `windows line endings parse like unix ones`() {
        assertEquals(
            listOf(MdBlock.Heading(1, t("T")), para("body")),
            parseMdBlocks("# T\r\n\r\nbody"),
        )
    }

    @Test
    fun `nothing to parse gives nothing`() {
        assertTrue(parseMdBlocks("").isEmpty())
        assertTrue(parseMdBlocks("  \n \n").isEmpty())
    }

    // ── Math ─────────────────────────────────────────────────────────────

    @Test
    fun `a dollar-dollar block is a formula, on one line or several`() {
        assertEquals(listOf(MdBlock.Math("x^2", closed = true)), parseMdBlocks("\$\$x^2\$\$"))
        assertEquals(
            listOf(MdBlock.Math("\\frac{a}{b}\n+ c", closed = true), para("after")),
            parseMdBlocks("\$\$\n\\frac{a}{b}\n+ c\n\$\$\nafter"),
        )
    }

    @Test
    fun `a formula block still being streamed says so`() {
        assertEquals(listOf(MdBlock.Math("x +", closed = false)), parseMdBlocks("\$\$\nx +"))
    }

    @Test
    fun `dollars inside a sentence are inline formulas`() {
        assertEquals(
            listOf(MdBlock.Para(listOf(MdSpan.Text("area "), MdSpan.Math("\\pi r^2"), MdSpan.Text(" ok")))),
            parseMdBlocks("area \$\\pi r^2\$ ok"),
        )
        assertEquals(
            listOf(MdBlock.Para(listOf(MdSpan.Math("E=mc^2"), MdSpan.Text(" holds")))),
            parseMdBlocks("\$\$E=mc^2\$\$ holds"),
        )
    }

    @Test
    fun `prices, shell variables, escapes and code are not formulas`() {
        for (text in listOf("costs \$5 and \$10", "\$HOME and \$PATH", "\\\$x\\\$", "`\$x\$`")) {
            assertTrue(text, parseInline(text).none { it is MdSpan.Math })
        }
    }

    @Test
    fun `an inline formula copies as its source`() {
        val out = inline("so \$x^2\$ is", style)
        assertEquals("so x^2 is", out.text)
    }

    @Test
    fun `a formula JLatexMath cannot draw reads as code`() {
        val out = inline(parseInline("so \$\\bad{\$ is"), style) { false }
        assertEquals("so \\bad{ is", out.text)
        assertEquals(1, out.spanStyles.size)
    }

    // ── Streaming ────────────────────────────────────────────────────────

    private val document = "# Title\n\nFirst para\nline two\n\n- a\n- b\n\n" +
        "```kotlin\nval x = 1\n\nval y = 2\n```\n\n" +
        "| a | b |\n|---|---|\n| 1 | 2 |\n\n" +
        "1. one\n\n1. two\n\n   - nested\n\n   more of two\n\n" +
        "> quote\n\n> another\n\n~~~\ncode\n\n~~~\n\n" +
        "\$\$\n\\frac{1}{2}\n\n+ x\n\$\$\n\nso \$y\$ too\n\nend"

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
    fun `a code span can hold a backtick`() {
        val out = inline("type `` a`b `` here", style)
        assertEquals("type a`b here", out.text)
        assertEquals(1, out.spanStyles.size)
    }

    @Test
    fun `markdown inside a code span stays literal`() {
        val out = inline("`**not bold**` and `[x](y)`", style)
        assertEquals("**not bold** and [x](y)", out.text)
        assertTrue(out.getLinkAnnotations(0, out.text.length).isEmpty())
    }

    @Test
    fun `inline text that looks like a block is still inline`() {
        assertEquals("1. not a list", inline("1. not a list", style).text)
        assertEquals("# not a heading", inline("# not a heading", style).text)
    }

    @Test
    fun `a single tilde is not strikethrough`() {
        val out = inline("cd ~/a and ~/b", style)
        assertEquals("cd ~/a and ~/b", out.text)
        assertTrue(out.spanStyles.isEmpty())
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

    @Test
    fun `a bare url stops before a persian comma`() {
        val out = inline("ببین https://a.io/x، خوب", style)
        assertEquals("ببین https://a.io/x، خوب", out.text)
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
    fun `the language with more words wins, not whichever came first`() {
        assertEquals(true, isRtlText("برای نصب باید git را اجرا کنید"))
        assertEquals(false, isRtlText("Install the package پایتون now"))
    }

    @Test
    fun `a persian sentence listing package names stays right to left`() {
        // More Latin letters (54 to 47) but more Persian words.
        assertEquals(
            true,
            isRtlText(
                "**۷۹ بسته‌ی apk** که برای کروم نصب شده بودند (xvfb, gtk+3.0, mesa, nss, nsspr, " +
                    "fontconfig, wayland, polkit, ttf-dejavu, …): تعداد کل بسته‌ها از **۱۹۴ به ۱۱۵** رسید.",
            ),
        )
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

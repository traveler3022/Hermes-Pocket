package com.hermes.android.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
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
            listOf(
                MdBlock.Table(
                    listOf(listOf("a", "b"), listOf("1", "2")),
                    hasHeader = true,
                    aligns = listOf(TableAlign.None, TableAlign.None),
                ),
            ),
            parseMdBlocks("| a | b |\n|---|---|\n| 1 | 2 |"),
        )
        // Without the separator it is just text.
        assertEquals(listOf(MdBlock.Para("| a | b |")), parseMdBlocks("| a | b |"))
    }

    @Test
    fun `a separator without colons leaves every column at the default alignment`() {
        val table = parseMdBlocks("| a | b |\n| --- | --- |\n| 1 | 2 |").single() as MdBlock.Table
        assertEquals(listOf(TableAlign.None, TableAlign.None), table.aligns)
    }

    @Test
    fun `colons in the separator set left, center and right per column`() {
        val table = parseMdBlocks("| a | b | c | d |\n|:---|:---:|---:|---|\n| 1 | 2 | 3 | 4 |")
            .single() as MdBlock.Table
        assertEquals(
            listOf(TableAlign.Left, TableAlign.Center, TableAlign.Right, TableAlign.None),
            table.aligns,
        )
        assertEquals(listOf(listOf("a", "b", "c", "d"), listOf("1", "2", "3", "4")), table.rows)
        // A separator without outer pipes and with spaces around the cells counts too.
        val bare = parseMdBlocks("| a | b |\n :-- | --: ").single() as MdBlock.Table
        assertEquals(listOf(TableAlign.Left, TableAlign.Right), bare.aligns)
    }

    @Test
    fun `rows with more or fewer cells than the separator keep their own cells`() {
        val table = parseMdBlocks("| a | b |\n|:--|--:|\n| 1 | 2 | 3 |\n| 4 |").single() as MdBlock.Table
        assertEquals(listOf(TableAlign.Left, TableAlign.Right), table.aligns)
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2", "3"), listOf("4")), table.rows)
    }

    @Test
    fun `a table with aligned columns parses the same while it streams in`() {
        val doc = "Intro\n\n| name | size |\n|:-----|-----:|\n| a | 1 |\n| b | 22 |\n\nend"
        val incremental = IncrementalMdParser()
        for (length in 1..doc.length) {
            val prefix = doc.substring(0, length)
            assertEquals("prefix of $length chars", parseMdBlocks(prefix), incremental.parse(prefix))
        }
        val table = incremental.parse(doc).filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf(TableAlign.Left, TableAlign.Right), table.aligns)
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
    fun `lines right under an item continue it, indented or not`() {
        assertEquals(
            listOf(MdBlock.ListItem(0, null, null, "one\ntwo\nthree")),
            parseMdBlocks("- one\n  two\nthree"),
        )
    }

    @Test
    fun `an indented paragraph after a blank line stays inside the item`() {
        assertEquals(
            listOf(
                MdBlock.ListItem(0, 1, null, "Install", listOf(MdBlock.Para("Then restart."))),
                MdBlock.ListItem(0, 2, null, "Done"),
            ),
            parseMdBlocks("1. Install\n\n   Then restart.\n\n2. Done"),
        )
    }

    @Test
    fun `an item can hold a quote and a table`() {
        val item = parseMdBlocks("- a\n  > note\n\n  | x | y |\n  |---|---|\n  | 1 | 2 |")
            .single() as MdBlock.ListItem
        assertEquals("a", item.text)
        assertEquals(
            listOf(
                MdBlock.Quote(1, "note"),
                MdBlock.Table(
                    listOf(listOf("x", "y"), listOf("1", "2")),
                    hasHeader = true,
                    aligns = listOf(TableAlign.None, TableAlign.None),
                ),
            ),
            item.children,
        )
    }

    @Test
    fun `text back at the margin after a blank line ends the list`() {
        assertEquals(
            listOf(MdBlock.ListItem(0, null, null, "a"), MdBlock.Para("after")),
            parseMdBlocks("- a\n\nafter"),
        )
    }

    @Test
    fun `a heading right under an item is not swallowed by it`() {
        assertEquals(
            listOf(MdBlock.ListItem(0, null, null, "a"), MdBlock.Heading(2, "Next")),
            parseMdBlocks("- a\n## Next"),
        )
    }

    @Test
    fun `a nested item is still its own item, not part of its parent`() {
        assertEquals(
            listOf(
                MdBlock.ListItem(0, null, null, "a"),
                MdBlock.ListItem(1, null, null, "b", listOf(MdBlock.Para("more b"))),
            ),
            parseMdBlocks("- a\n  - b\n\n    more b"),
        )
    }

    @Test
    fun `a definition takes the paragraph line above it as its term`() {
        assertEquals(
            listOf(MdBlock.Para("intro"), MdBlock.Definition("Term", listOf("first", "second"))),
            parseMdBlocks("intro\nTerm\n: first\n: second"),
        )
        assertEquals(
            listOf(MdBlock.Definition("Term", listOf("meaning"))),
            parseMdBlocks("Term\n\n: meaning"),
        )
        // With no paragraph above it, a colon line is just text.
        assertEquals(listOf(MdBlock.Para(": alone")), parseMdBlocks(": alone"))
    }

    @Test
    fun `a footnote definition is its own block and takes the indented lines under it`() {
        assertEquals(
            listOf(MdBlock.Para("text[^1]"), MdBlock.Footnote("1", "the note\ngoes on")),
            parseMdBlocks("text[^1]\n\n[^1]: the note\n  goes on"),
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

    private val extendedDocument = "Intro\n\n1. Install\n   it now\n\n   Then restart.\n\n   > careful\n\n" +
        "2. Done\n  - nested\n\n    more nested\n\nback at the margin\n\nTerm\n\n: meaning\n: another\n\n" +
        "Body with a note[^1].\n\n[^1]: The note\n  continues\n\nend"

    @Test
    fun `lists with paragraphs, definitions and footnotes parse the same while they grow`() {
        val incremental = IncrementalMdParser()
        for (length in 1..extendedDocument.length) {
            val prefix = extendedDocument.substring(0, length)
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

    @Test
    fun `highlight, superscript, subscript and kbd lose their marks and keep a style`() {
        val out = inline("==hot== x^2^ H~2~O <kbd>Ctrl</kbd>", style)
        assertEquals("hot x2 H2O \u2009Ctrl\u2009", out.text)
        val spans = out.spanStyles.sortedBy { it.start }
        assertEquals(4, spans.size)
        assertEquals(style.markBg, spans[0].item.background)
        assertEquals(BaselineShift.Superscript, spans[1].item.baselineShift)
        assertEquals(BaselineShift.Subscript, spans[2].item.baselineShift)
    }

    @Test
    fun `tildes and carets that are not a closed word stay as typed`() {
        for (plain in listOf("about ~5 min or ~10", "cd ~/src and ~/bin", "x^2 + y^2", "a == b", "=====")) {
            val out = inline(plain, style)
            assertEquals(plain, out.text)
            assertTrue(plain, out.spanStyles.isEmpty())
        }
    }

    @Test
    fun `a footnote reference is a raised label, a link label starting with a caret is still a link`() {
        val ref = inline("fact[^1].", style)
        assertEquals("fact1.", ref.text)
        assertEquals(BaselineShift.Superscript, ref.spanStyles.single().item.baselineShift)

        val link = inline("[^x](https://a.io)", style)
        assertEquals("^x", link.text)
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

    @Test
    fun `a GitHub alert takes the quote lines under its marker as blocks`() {
        assertEquals(
            listOf(
                MdBlock.Alert(AlertKind.Warning, listOf(MdBlock.Para("Back up first.\nThen run it."), MdBlock.ListItem(0, null, null, "one"))),
                MdBlock.Para("After."),
            ),
            parseMdBlocks("> [!WARNING]\n> Back up first.\n> Then run it.\n>\n> - one\n\nAfter."),
        )
    }

    @Test
    fun `alert text on the marker line is the first body line, and a plain quote stays a quote`() {
        assertEquals(listOf(MdBlock.Alert(AlertKind.Note, listOf(MdBlock.Para("Short note.")))), parseMdBlocks("> [!note] Short note."))
        assertEquals(listOf(MdBlock.Quote(1, "[!NOTES] not an alert")), parseMdBlocks("> [!NOTES] not an alert"))
    }

    // ── Math ─────────────────────────────────────────────────────────────

    private fun mathIds(text: String) =
        inline(text, style).getStringAnnotations(0, text.length)
            .map { it.item }
            .filter { it.startsWith(INLINE_MATH_PREFIX) }
            .map { it.removePrefix(INLINE_MATH_PREFIX) }

    @Test
    fun `with math off dollar signs stay as typed`() {
        val text = "cp \$SRC/a \$DST and \$x\$ and \\(y\\)"
        val out = inline(text, style.copy(math = false))
        assertEquals("cp \$SRC/a \$DST and \$x\$ and (y)", out.text)
        assertTrue(out.getStringAnnotations(0, out.length).none { it.item.startsWith(INLINE_MATH_PREFIX) })
    }

    @Test
    fun `display math on one line or across lines`() {
        assertEquals(listOf(MdBlock.Math("x^2")), parseMdBlocks("\$\$x^2\$\$"))
        assertEquals(
            listOf(MdBlock.Para("Area:"), MdBlock.Math("A = \\pi r^2"), MdBlock.Para("done")),
            parseMdBlocks("Area:\n$$\nA = \\pi r^2\n$$\ndone"),
        )
        assertEquals(listOf(MdBlock.Math("a+b")), parseMdBlocks("\\[\na+b\n\\]"))
    }

    @Test
    fun `unclosed display math mid-stream keeps what has arrived`() {
        assertEquals(listOf(MdBlock.Math("\\frac{1}{2")), parseMdBlocks("$$\n\\frac{1}{2"))
    }

    @Test
    fun `inline math in a sentence, prices stay text`() {
        assertEquals(listOf("x^2"), mathIds("so \$x^2\$ grows"))
        assertEquals(listOf("a+b"), mathIds("and \\(a+b\\) too"))
        assertEquals(emptyList<String>(), mathIds("it costs \$5 and \$10 now"))
        assertEquals(emptyList<String>(), mathIds("cheap: \$ 5 \$"))
        assertEquals(emptyList<String>(), mathIds("escaped \\\$x\$ here"))
        // The source stays in the text, so copying a reply keeps the formula.
        assertEquals("so x^2 grows", inline("so \$x^2\$ grows", style).text)
    }
}

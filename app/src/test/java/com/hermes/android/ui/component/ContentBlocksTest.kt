package com.hermes.android.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentBlocksTest {

    @Test
    fun `plain text is a single text block`() {
        val blocks = parseContentBlocks("hello world")
        assertEquals(listOf(ContentBlock.Text("hello world")), blocks)
    }

    @Test
    fun `markdown image is extracted with surrounding text`() {
        val blocks = parseContentBlocks("Here you go:\n![chart](/home/user/.hermes/images/chart.png)\nDone.")
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is ContentBlock.Text)
        val img = blocks[1] as ContentBlock.Image
        assertEquals("/home/user/.hermes/images/chart.png", img.url)
        assertEquals("chart", img.alt)
        assertTrue(blocks[2] is ContentBlock.Text)
    }

    @Test
    fun `markdown image with extensionless web url is still an image`() {
        val blocks = parseContentBlocks("![random](https://picsum.photos/400)")
        val img = blocks.filterIsInstance<ContentBlock.Image>().single()
        assertEquals("https://picsum.photos/400", img.url)
    }

    @Test
    fun `bare image path becomes an image block`() {
        val blocks = parseContentBlocks("Saved the plot to ~/.hermes/images/plot_1.png for you.")
        assertTrue(blocks.any { it is ContentBlock.Image && it.url == "~/.hermes/images/plot_1.png" })
    }

    @Test
    fun `bare path inside markdown image is not duplicated`() {
        val blocks = parseContentBlocks("![x](/tmp/a.png)")
        assertEquals(1, blocks.filterIsInstance<ContentBlock.Image>().size)
    }

    @Test
    fun `fenced code block with language`() {
        val blocks = parseContentBlocks("Before\n```python\nprint(1)\n```\nAfter")
        val code = blocks.filterIsInstance<ContentBlock.Code>().single()
        assertEquals("python", code.language)
        assertEquals("print(1)", code.code)
    }

    @Test
    fun `mermaid fence becomes mermaid block`() {
        val blocks = parseContentBlocks("```mermaid\ngraph TD; A-->B;\n```")
        assertTrue(blocks.single() is ContentBlock.Mermaid)
    }

    @Test
    fun `image path inside code fence is not extracted`() {
        val blocks = parseContentBlocks("```bash\ncp /tmp/a.png /tmp/b.png\n```")
        assertTrue(blocks.filterIsInstance<ContentBlock.Image>().isEmpty())
        assertTrue(blocks.single() is ContentBlock.Code)
    }

    @Test
    fun `unterminated streaming fence is still code`() {
        val blocks = parseContentBlocks("text\n```python\nprint(")
        assertTrue(blocks.last() is ContentBlock.Code)
    }

    @Test
    fun `html video and file paths are classified`() {
        val blocks = parseContentBlocks(
            "Page: /data/page.html and clip file:///data/clip.mp4 plus /data/report.pdf",
        )
        assertTrue(blocks.any { it is ContentBlock.Html })
        assertTrue(blocks.any { it is ContentBlock.Video })
        assertTrue(blocks.any { it is ContentBlock.FileRef && it.name == "report.pdf" })
    }

    @Test
    fun `a relative path in inline code stays text`() {
        val text = "I changed `app/src/main/Foo.kt` to fix it."
        assertEquals(listOf(ContentBlock.Text(text)), parseContentBlocks(text))
    }

    @Test
    fun `a fraction is not a file`() {
        val text = "Score: 4/5.0 overall, took 2/3.5 seconds"
        assertEquals(listOf(ContentBlock.Text(text)), parseContentBlocks(text))
    }

    @Test
    fun `a link to a web page stays a link`() {
        val text = "Read [the docs](https://docs.python.org/3/library/re.html) first."
        assertEquals(listOf(ContentBlock.Text(text)), parseContentBlocks(text))
    }

    @Test
    fun `a link to a file the agent made is that file`() {
        val blocks = parseContentBlocks("Here it is: [the report](/root/report.pdf).")
        assertEquals(
            listOf(
                ContentBlock.Text("Here it is:"),
                ContentBlock.FileRef(url = "/root/report.pdf", name = "the report"),
                ContentBlock.Text("."),
            ),
            blocks,
        )
    }

    @Test
    fun `a path that is a whole inline code span leaves no stray backticks`() {
        val blocks = parseContentBlocks("Saved to `/root/report.html`.")
        assertEquals(
            listOf(
                ContentBlock.Text("Saved to"),
                ContentBlock.Html(url = "/root/report.html", name = "report.html"),
                ContentBlock.Text("."),
            ),
            blocks,
        )
    }

    @Test
    fun `a path inside a longer inline command stays code`() {
        val text = "Run `python3 /root/app/main.py` now"
        assertEquals(listOf(ContentBlock.Text(text)), parseContentBlocks(text))
    }

    @Test
    fun `an absolute path in prose is still a file`() {
        val blocks = parseContentBlocks("Run it with python3 /root/app/main.py now")
        assertEquals(
            listOf(
                ContentBlock.Text("Run it with python3"),
                ContentBlock.FileRef(url = "/root/app/main.py", name = "main.py"),
                ContentBlock.Text("now"),
            ),
            blocks,
        )
    }

    @Test
    fun `MEDIA tag becomes a file block and leaves no tag text`() {
        val blocks = parseContentBlocks("Here is the report:\nMEDIA:/root/out/report_final.pdf")
        val file = blocks.filterIsInstance<ContentBlock.FileRef>().single()
        assertEquals("/root/out/report_final.pdf", file.url)
        assertTrue(blocks.filterIsInstance<ContentBlock.Text>().none { "MEDIA" in it.markdown })
    }

    @Test
    fun `MEDIA tag image in emphasis becomes an image`() {
        val blocks = parseContentBlocks("**MEDIA:/root/a_b.png** and _MEDIA:/root/c.png_")
        assertEquals(
            listOf("/root/a_b.png", "/root/c.png"),
            blocks.filterIsInstance<ContentBlock.Image>().map { it.url },
        )
    }

    @Test
    fun `quoted MEDIA path with spaces and extensionless path`() {
        val blocks = parseContentBlocks("MEDIA:\"/root/my file.docx\"\nMEDIA:/root/Caddyfile")
        assertEquals(
            listOf("/root/my file.docx", "/root/Caddyfile"),
            blocks.filterIsInstance<ContentBlock.FileRef>().map { it.url },
        )
    }

    @Test
    fun `MEDIA tag inside inline code stays code`() {
        val blocks = parseContentBlocks("write `MEDIA:/root/x.png` to send a file")
        assertTrue(blocks.none { it is ContentBlock.Image })
    }
}

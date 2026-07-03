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
}

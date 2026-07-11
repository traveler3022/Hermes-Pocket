package com.hermes.android.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentBlocksExtendedTest {

    @Test
    fun `empty string produces no blocks`() {
        val blocks = parseContentBlocks("")
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `whitespace only produces no blocks`() {
        // parseContentBlocks treats blank input like empty input — there is
        // nothing to render, so no block is emitted (consistent with the
        // empty-string case above).
        val blocks = parseContentBlocks("   ")
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `multiple images with text between`() {
        val input = "Start\n![img1](/path/1.png)\nMiddle\n![img2](/path/2.png)\nEnd"
        val blocks = parseContentBlocks(input)
        assertTrue(blocks.size >= 5)
        var imageCount = 0
        for (b in blocks) if (b is ContentBlock.Image) imageCount++
        assertEquals(2, imageCount)
    }

    @Test
    fun `image without alt text still parsed`() {
        val blocks = parseContentBlocks("![](/path/no-alt.png)")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is ContentBlock.Image)
        val img = blocks[0] as ContentBlock.Image
        assertEquals("/path/no-alt.png", img.url)
    }

    @Test
    fun `text with no markdown is plain text block`() {
        val blocks = parseContentBlocks("Just plain text here")
        assertEquals(1, blocks.size)
        val text = blocks[0] as ContentBlock.Text
        assertEquals("Just plain text here", text.markdown)
    }

    @Test
    fun `malformed image syntax is treated as text`() {
        val blocks = parseContentBlocks("This is not an image: ![](broken")
        // Should not crash, should produce text blocks
        assertTrue(blocks.isNotEmpty())
        assertFalse(blocks.any { it is ContentBlock.Image })
    }
}

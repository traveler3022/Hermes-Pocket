package com.hermes.android.ui.component

import com.hermes.android.domain.markdown.markdownTableKey
import org.junit.Assert.assertEquals
import org.junit.Test

class TableSeamTest {

    @Test
    fun `a drag trades width between the two columns of the seam`() {
        assertEquals(listOf(152f, 112f, 132f), resizeSeam(listOf(132f, 132f, 132f), 0, 20f, 48f))
        assertEquals(listOf(132f, 112f, 152f), resizeSeam(listOf(132f, 132f, 132f), 1, -20f, 48f))
    }

    @Test
    fun `neither column goes below the minimum`() {
        assertEquals(listOf(216f, 48f), resizeSeam(listOf(132f, 132f), 0, 500f, 48f))
        assertEquals(listOf(48f, 216f), resizeSeam(listOf(132f, 132f), 0, -500f, 48f))
    }

    @Test
    fun `the last column has no seam of its own`() {
        val widths = listOf(132f, 132f)
        assertEquals(widths, resizeSeam(widths, 1, 20f, 48f))
        assertEquals(widths, resizeSeam(widths, -1, 20f, 48f))
    }

    @Test
    fun `table key matches Hermes Desktop's hash`() {
        // Expected values computed with Desktop's markdownTableKey in node.
        assertEquals("1mbvh62", markdownTableKey(listOf("Name", "Value")))
        assertEquals("1aqye3t", markdownTableKey(listOf("نام", "مقدار", "توضیح")))
    }
}

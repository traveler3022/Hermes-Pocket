package com.hermes.android.ui.viewer

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ViewerSourceTest {

    private val linux = "com.hermes.android.linux.documents"

    private fun uriOf(s: String, a: String?): Uri = mockk {
        every { scheme } returns s
        every { authority } returns a
    }

    @Test
    fun `another app's launch opens a Linux Files document`() {
        val document = uriOf("content", linux)
        assertSame(document, viewerSource(external = true, data = document, extraUrl = null, linuxAuthority = linux))
    }

    @Test
    fun `another app's launch opens no file URL, no other provider's document and no extra`() {
        assertNull(viewerSource(external = true, data = uriOf("file", null), extraUrl = null, linuxAuthority = linux))
        assertNull(viewerSource(external = true, data = uriOf("content", "com.other.files"), extraUrl = null, linuxAuthority = linux))
        assertNull(
            viewerSource(
                external = true,
                data = null,
                extraUrl = "file:///data/data/com.hermes.android/files/linux/rootfs/root/.hermes/.env",
                linuxAuthority = linux,
            ),
        )
    }
}

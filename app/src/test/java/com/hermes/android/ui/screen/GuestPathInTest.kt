package com.hermes.android.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuestPathInTest {

    private val rootfs = "/data/user/0/com.hermes.android/files/linux/rootfs"

    @Test
    fun `a file inside the rootfs maps to its guest path`() {
        assertEquals("/root/report.html", guestPathIn(rootfs, "$rootfs/root/report.html"))
    }

    @Test
    fun `a sibling directory with the same prefix is not the rootfs`() {
        assertNull(guestPathIn(rootfs, "${rootfs}2/root/report.html"))
    }

    @Test
    fun `files outside the rootfs and the rootfs itself are not guest files`() {
        assertNull(guestPathIn(rootfs, "/sdcard/Download/report.html"))
        assertNull(guestPathIn(rootfs, rootfs))
    }
}

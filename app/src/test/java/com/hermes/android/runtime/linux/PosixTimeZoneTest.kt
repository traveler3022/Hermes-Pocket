package com.hermes.android.runtime.linux

import org.junit.Assert.assertEquals
import org.junit.Test

class PosixTimeZoneTest {

    @Test
    fun `an offset east of UTC gets a negative POSIX sign`() {
        assertEquals("<+0330>-03:30", posixTimeZone(3 * 3_600_000 + 30 * 60_000))
    }

    @Test
    fun `an offset west of UTC gets a positive POSIX sign`() {
        assertEquals("<-0500>+05:00", posixTimeZone(-5 * 3_600_000))
    }

    @Test
    fun `UTC itself`() {
        assertEquals("<+0000>-00:00", posixTimeZone(0))
    }
}

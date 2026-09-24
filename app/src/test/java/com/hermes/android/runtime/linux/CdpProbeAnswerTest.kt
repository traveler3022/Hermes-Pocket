package com.hermes.android.runtime.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The app and assets/desktop/cdp_pipe.py must compute the same probe answer. */
class CdpProbeAnswerTest {

    @Test
    fun `matches cdp_pipe's probe_answer`() {
        // python3 -c "import hmac,hashlib; print(hmac.new(b'0123456789abcdef0123456789abcdef',
        //     b'challenge-1', hashlib.sha256).hexdigest())"
        assertEquals(
            "e4a9ef49ec46f443949747b44ad40205945d371bbf4528cc8a6dbac24f6259ba",
            cdpProbeAnswer("0123456789abcdef0123456789abcdef", "challenge-1"),
        )
    }

    @Test
    fun `another secret gives another answer`() {
        assertNotEquals(
            cdpProbeAnswer("0123456789abcdef0123456789abcdef", "challenge-1"),
            cdpProbeAnswer("fedcba9876543210fedcba9876543210", "challenge-1"),
        )
    }
}

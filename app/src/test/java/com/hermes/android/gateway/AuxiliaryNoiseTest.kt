package com.hermes.android.gateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxiliaryNoiseTest {

    @Test
    fun `side-job failures are noise`() {
        assertTrue(GatewayEventHelpers.isAuxiliaryNoise("⚠ Auxiliary title generation failed: HTTP 422: Unprocessable Entity"))
        assertTrue(GatewayEventHelpers.isAuxiliaryNoise("⚠ Compression summary failed: upstream error. Inserted a fallback context marker."))
    }

    @Test
    fun `statuses the user acts on stay visible`() {
        assertFalse(GatewayEventHelpers.isAuxiliaryNoise("Compressed: 30 → 12 messages"))
        assertFalse(GatewayEventHelpers.isAuxiliaryNoise("⚠ Compression aborted: auth failure. No messages were dropped"))
        assertFalse(GatewayEventHelpers.isAuxiliaryNoise("Model switched to Atria-Dawn-Preview"))
    }
}

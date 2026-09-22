package com.hermes.android.gateway

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The end-of-turn session.info carries `running: false`; mid-turn ones say true. */
class SettledSessionInfoTest {

    @Test
    fun `running false is settled`() {
        assertTrue(GatewayEventHelpers.isSettledSessionInfo(mapOf("running" to JsonPrimitive(false))))
    }

    @Test
    fun `running true or no running field is not settled`() {
        assertFalse(GatewayEventHelpers.isSettledSessionInfo(mapOf("running" to JsonPrimitive(true))))
        assertFalse(GatewayEventHelpers.isSettledSessionInfo(mapOf("model" to JsonPrimitive("x"))))
    }
}

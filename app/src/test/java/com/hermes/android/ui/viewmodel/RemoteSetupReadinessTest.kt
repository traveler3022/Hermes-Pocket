package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSetupReadinessTest {
    @Test
    fun `connected gateway for signed-in server is ready`() {
        assertTrue(
            isRemoteSetupReady(
                signedInAs = "alice",
                connection = GatewayConnectionUi(ChatConnectionState.Connected),
            ),
        )
    }

    @Test
    fun `connection without matching sign-in is not ready`() {
        assertFalse(
            isRemoteSetupReady(
                signedInAs = null,
                connection = GatewayConnectionUi(ChatConnectionState.Connected),
            ),
        )
    }

    @Test
    fun `sign-in without connected gateway is not ready`() {
        listOf(
            ChatConnectionState.Disconnected,
            ChatConnectionState.Connecting,
            ChatConnectionState.Reconnecting,
            ChatConnectionState.Failed,
        ).forEach { state ->
            assertFalse(
                "Expected $state not to complete remote setup",
                isRemoteSetupReady("alice", GatewayConnectionUi(state)),
            )
        }
    }

    @Test
    fun `blank server identity is not ready`() {
        assertFalse(
            isRemoteSetupReady(
                signedInAs = " ",
                connection = GatewayConnectionUi(ChatConnectionState.Connected),
            ),
        )
    }
}

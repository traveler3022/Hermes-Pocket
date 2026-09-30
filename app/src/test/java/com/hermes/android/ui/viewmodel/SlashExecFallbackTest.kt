package com.hermes.android.ui.viewmodel

import com.hermes.android.gateway.GatewayException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashExecFallbackTest {

    private fun rpc(code: Int, message: String) =
        GatewayException("RPC error $code: $message", code = code, rpcMessage = message)

    @Test
    fun `skill refusal falls back to command dispatch`() {
        assertTrue(ChatSlashDelegate.slashExecDisowns(rpc(4018, "skill command: use command.dispatch for /review")))
    }

    @Test
    fun `snapshot restore refusal falls back to command dispatch`() {
        assertTrue(ChatSlashDelegate.slashExecDisowns(
            rpc(4018, "snapshot restore mutates live config/state; use command.dispatch for /snapshot restore"),
        ))
    }

    @Test
    fun `refusal from a forwarded dispatch handler is not dispatched twice`() {
        assertFalse(ChatSlashDelegate.slashExecDisowns(rpc(4018, "nothing to retry")))
    }

    @Test
    fun `worker failure is shown as itself`() {
        assertFalse(ChatSlashDelegate.slashExecDisowns(rpc(5030, "slash worker start failed: boom")))
    }

    @Test
    fun `transport errors never fall back`() {
        assertFalse(ChatSlashDelegate.slashExecDisowns(GatewayException("Request slash.exec timed out after 120000ms")))
    }
}

class SlashCommandTextTest {

    private val known = setOf("/help", "/goal", "/btw", "/bg", "/background", "/my-skill")

    @Test
    fun `known command is a command`() {
        assertTrue(ChatSlashDelegate.isSlashCommandText("/goal ship the release", known))
        assertTrue(ChatSlashDelegate.isSlashCommandText("/HELP", known))
        assertTrue(ChatSlashDelegate.isSlashCommandText("/my-skill", known))
    }

    @Test
    fun `a path is a message`() {
        assertFalse(ChatSlashDelegate.isSlashCommandText("/sdcard/Download/x.txt رو بخون", known))
        assertFalse(ChatSlashDelegate.isSlashCommandText("/root", known))
    }

    @Test
    fun `plain text is a message`() {
        assertFalse(ChatSlashDelegate.isSlashCommandText("سلام /help", known))
    }

    @Test
    fun `before the catalog arrives every slash is a command`() {
        assertTrue(ChatSlashDelegate.isSlashCommandText("/anything", emptySet()))
    }
}

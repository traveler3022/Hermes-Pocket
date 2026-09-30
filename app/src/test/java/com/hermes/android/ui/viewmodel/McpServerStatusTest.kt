package com.hermes.android.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerStatusTest {

    private fun server(url: String? = "https://mcp.linear.app/mcp", auth: String? = null, enabled: Boolean = true) =
        McpServer(
            name = "linear", url = url, command = if (url == null) "npx -y x" else null, env = emptyList(),
            auth = auth, enabled = enabled, plugin = null, runtimeStatus = null, runtimeTools = null,
            fingerprint = "f",
        )

    @Test
    fun `quoted arguments keep their spaces`() {
        assertEquals(
            listOf("-y", "@scope/server", "/root/My Files", "it's"),
            splitArgs("""  -y @scope/server "/root/My Files" "it's" """),
        )
        assertEquals(listOf("--flag=a b", ""), splitArgs("--flag='a b' ''"))
        assertEquals(emptyList<String>(), splitArgs("   "))
    }

    @Test
    fun `an OAuth server without a token needs sign-in`() {
        // tools/mcp_oauth.py's non-interactive guard, as a stdio gateway reports it.
        val probe = McpProbe(error = "MCP OAuth for 'linear': non-interactive environment and no cached tokens found.")
        val oauth = server(auth = "oauth")
        assertEquals(McpStatus.NEEDS_AUTH, oauth.statusWith(probe))
        assertTrue(oauth.canSignIn(McpStatus.NEEDS_AUTH))
    }

    @Test
    fun `sign-in is offered only where OAuth can work`() {
        assertTrue(server().canSignIn(McpStatus.NEEDS_AUTH))
        assertFalse(server().canSignIn(McpStatus.ERROR))
        assertTrue(server(auth = "oauth").canSignIn(McpStatus.ERROR))
        assertFalse(server(auth = "header").canSignIn(McpStatus.NEEDS_AUTH))
        assertFalse(server(url = null).canSignIn(McpStatus.NEEDS_AUTH))
    }

    @Test
    fun `status follows the probe`() {
        assertEquals(McpStatus.OFF, server(enabled = false).statusWith(McpProbe(ok = true)))
        assertEquals(McpStatus.UNKNOWN, server().statusWith(null))
        assertEquals(McpStatus.PROBING, server().statusWith(McpProbe(probing = true)))
        assertEquals(McpStatus.OK, server().statusWith(McpProbe(ok = true)))
        assertEquals(McpStatus.ERROR, server().statusWith(McpProbe(error = "Connection refused")))
        assertEquals(McpStatus.NEEDS_AUTH, server().statusWith(McpProbe(error = "HTTP 401 Unauthorized")))
    }
}

package com.hermes.android.gateway

import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which profile a call runs in: the selection, or the profile its session was opened in. */
class ProfileScopeTest {

    private val scope = ProfileScope(mockk(relaxed = true))

    private fun params(vararg pairs: Pair<String, String>): Map<String, JsonElement> =
        pairs.associate { (k, v) -> k to JsonPrimitive(v) }

    @Test
    fun `nothing selected sends no profile`() {
        assertNull(scope.profileFor("session.create", params()))
    }

    @Test
    fun `a selection goes on unbound calls only where the server takes it`() {
        scope.select("writer")
        assertEquals("writer", scope.profileFor("session.create", params()))
        assertEquals("writer", scope.profileFor("config.get", params("key" to "model")))
        // These params have no `profile` field: the server answers 4000.
        assertNull(scope.profileFor("tools.list", params()))
        assertNull(scope.profileFor("client.capabilities", params()))
        // The caller already chose.
        assertNull(scope.profileFor("session.create", params("profile" to "other")))
    }

    @Test
    fun `a session keeps the profile it was opened in after a switch`() {
        scope.select("writer")
        scope.remember("writer", buildJsonObject {
            put("session_id", "live-1")
            put("stored_session_id", "stored-1")
        })
        scope.select("coder")

        assertEquals("writer", scope.profileFor("prompt.submit", params("session_id" to "live-1")))
        assertEquals("writer", scope.profileFor("session.resume", params("session_id" to "stored-1")))
        assertEquals("coder", scope.profileFor("session.create", params()))
    }

    @Test
    fun `a session of the launch profile sends nothing even with another selected`() {
        scope.remember(null, buildJsonObject { put("session_id", "live-0") })
        scope.select("coder")
        assertNull(scope.profileFor("prompt.submit", params("session_id" to "live-0")))
    }

    @Test
    fun `an unknown profile falls back to the launch profile`() {
        scope.select("gone")
        scope.forget("gone")
        assertNull(scope.active.value)
        assertNull(scope.profileFor("session.create", params()))
    }

    @Test
    fun `a method the server refused the param on is sent without it`() {
        scope.select("writer")
        scope.refuse("projects.tree")
        assertNull(scope.profileFor("projects.tree", params()))
    }

    @Test
    fun `a rename moves the selection and its sessions without a switch`() {
        scope.select("writer")
        scope.remember("writer", buildJsonObject { put("session_id", "live-1") })
        scope.renamed("writer", "author")
        assertEquals("author", scope.active.value)
        assertEquals("author", scope.profileFor("prompt.submit", params("session_id" to "live-1")))
    }

    @Test
    fun `profile calls name their profile themselves`() {
        scope.select("writer")
        assertNull(scope.profileFor("profiles.describe", params("name" to "other")))
    }

    @Test
    fun `only the profile that was sent counts as unknown`() {
        val gone = GatewayException("x", code = 4064, rpcMessage = "Profile 'writer' does not exist.")
        val lookup = GatewayException("x", code = 4064, rpcMessage = "profile 'other' not found")
        assertEquals(true, gone.isUnknownProfile("writer"))
        assertEquals(false, lookup.isUnknownProfile("writer"))
    }
}

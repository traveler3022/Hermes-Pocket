package com.hermes.android.ui.screen

import com.hermes.android.R
import com.hermes.android.gateway.GatewayEvent
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ChatUiState
import com.hermes.android.ui.viewmodel.withToolStarted
import org.junit.Assert.assertEquals
import org.junit.Test

/** A tool card's title: verb + the server's preview, per kind of tool. */
class ToolLineTest {

    private fun done(name: String, preview: String?) = toolLine(name, preview, isRunning = false, failed = false)

    @Test
    fun `each group gets its verb and the preview as subject`() {
        // Previews as tui_gateway sends them (agent/display.py build_tool_preview).
        assertEquals(ToolLine(R.string.tool_verb_read, "README.md", mono = true), done("read_file", "README.md"))
        assertEquals(ToolLine(R.string.tool_verb_searched, "TODO", mono = true), done("search_files", "TODO"))
        assertEquals(ToolLine(R.string.tool_verb_ran, "ls -la", mono = true), done("terminal", "ls -la"))
        assertEquals(ToolLine(R.string.tool_verb_ran, "print(1)", mono = true), done("execute_code", "print(1)"))
        assertEquals(ToolLine(R.string.tool_verb_edited, "a.kt", mono = true), done("patch", "a.kt"))
        assertEquals(ToolLine(R.string.tool_verb_wrote, "b.md", mono = true), done("write_file", "b.md"))
        assertEquals(ToolLine(R.string.tool_verb_searched_web, "hermes", mono = false), done("web_search", "hermes"))
        assertEquals(
            ToolLine(R.string.tool_verb_opened, "https://example.com", mono = false),
            done("web_extract", "https://example.com"),
        )
    }

    @Test
    fun `running, done and failed pick their tense`() {
        assertEquals(R.string.tool_verb_reading, toolLine("read_file", "x", isRunning = true, failed = false).verb)
        assertEquals(R.string.tool_verb_read, toolLine("read_file", "x", isRunning = false, failed = false).verb)
        assertEquals(R.string.tool_verb_read_failed, toolLine("read_file", "x", isRunning = false, failed = true).verb)
        assertEquals(R.string.tool_verb_running, toolLine("terminal", "x", isRunning = true, failed = false).verb)
        assertEquals(R.string.tool_verb_run_failed, toolLine("terminal", "x", isRunning = false, failed = true).verb)
        assertEquals(R.string.tool_verb_editing, toolLine("patch", "x", isRunning = true, failed = false).verb)
        assertEquals(R.string.tool_verb_edit_failed, toolLine("patch", "x", isRunning = false, failed = true).verb)
        assertEquals(R.string.tool_verb_using, toolLine("memory", null, isRunning = true, failed = false).verb)
        assertEquals(R.string.tool_verb_use_failed, toolLine("memory", null, isRunning = false, failed = true).verb)
    }

    @Test
    fun `no preview falls back to the tool name, as before`() {
        assertEquals(ToolLine(null, "read_file", mono = false), done("read_file", null))
        assertEquals(ToolLine(null, "terminal", mono = false), done("terminal", "   "))
    }

    @Test
    fun `other tools are named in words`() {
        assertEquals(ToolLine(R.string.tool_verb_used, "Image generate", mono = false), done("image_generate", "a cat"))
        assertEquals(ToolLine(R.string.tool_verb_used, "Browser click", mono = false), done("browser_click", "@e3"))
        assertEquals(ToolLine(R.string.tool_verb_used, "Memory", mono = false), done("memory", null))
    }

    @Test
    fun `mcp names read as server and tool`() {
        assertEquals("Github: create issue", humanizeToolName("mcp__github__create_issue"))
        assertEquals("My server: list files", humanizeToolName("mcp__my-server__list-files"))
        assertEquals("Odd", humanizeToolName("mcp__odd"))
        assertEquals(ToolLine(R.string.tool_verb_used, "Github: create issue", mono = false), done("mcp__github__create_issue", "x"))
        assertEquals("", humanizeToolName(""))
    }

    @Test
    fun `tool start keeps the preview, and a restart without one keeps the old`() {
        val start = GatewayEvent.ToolStart("s", "t1", "read_file", "{\"path\":\"/r/README.md\"}", "README.md")
        val state = ChatUiState().withToolStarted(start, replyId = null)
        assertEquals("README.md", (state.messages.single() as ChatMessage.ToolCall).preview)
        val again = state.withToolStarted(start.copy(context = null), replyId = null)
        assertEquals("README.md", (again.messages.single() as ChatMessage.ToolCall).preview)
    }
}

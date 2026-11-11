package com.hermes.android.ui.screen

import androidx.annotation.StringRes
import com.hermes.android.R

/**
 * A tool card's one-line title: a verb and what it acted on, "Read README.md",
 * "Ran ls -la", "Edited a.kt". [verb] is null when there is nothing to phrase
 * (a known tool with no preview): the line is then just [subject], the tool name.
 * [mono] marks a path or a command, set in a monospace face.
 */
internal data class ToolLine(
    @StringRes val verb: Int?,
    val subject: String,
    val mono: Boolean,
)

/** One verb in its three tenses: while the tool runs, once it is done, and when it failed. */
private class ToolVerb(@StringRes val running: Int, @StringRes val done: Int, @StringRes val failed: Int) {
    fun pick(isRunning: Boolean, failed: Boolean): Int = when {
        isRunning -> running
        failed -> this.failed
        else -> done
    }
}

private val READ = ToolVerb(R.string.tool_verb_reading, R.string.tool_verb_read, R.string.tool_verb_read_failed)
private val SEARCH = ToolVerb(R.string.tool_verb_searching, R.string.tool_verb_searched, R.string.tool_verb_search_failed)
private val RUN = ToolVerb(R.string.tool_verb_running, R.string.tool_verb_ran, R.string.tool_verb_run_failed)
private val EDIT = ToolVerb(R.string.tool_verb_editing, R.string.tool_verb_edited, R.string.tool_verb_edit_failed)
private val WRITE = ToolVerb(R.string.tool_verb_writing, R.string.tool_verb_wrote, R.string.tool_verb_write_failed)
private val WEB_SEARCH = ToolVerb(R.string.tool_verb_searching_web, R.string.tool_verb_searched_web, R.string.tool_verb_search_web_failed)
private val OPEN = ToolVerb(R.string.tool_verb_opening, R.string.tool_verb_opened, R.string.tool_verb_open_failed)
private val USE = ToolVerb(R.string.tool_verb_using, R.string.tool_verb_used, R.string.tool_verb_use_failed)

/**
 * The title for a tool card. [preview] is tool.start's `context`: the server's
 * short argument preview (tui_gateway/server.py `_tool_ctx`, agent/display.py
 * `build_tool_preview`), the path, the command or the query. The server leaves
 * the phrasing to clients, so the verb is ours. Tools are grouped as
 * [HxTraceActivity.of] groups them.
 *
 * Adapted in idea from slopus/happy ToolWords (MIT License, Copyright (c) 2026 Happy Coder Contributors).
 */
internal fun toolLine(toolName: String, preview: String?, isRunning: Boolean, failed: Boolean): ToolLine {
    val subject = preview?.trim()?.takeIf { it.isNotEmpty() }
    val n = toolName.lowercase()
    val verb: ToolVerb? = when (HxTraceActivity.of(toolName)) {
        HxTraceActivity.Terminal -> RUN
        HxTraceActivity.FileRead -> if (n == "search_files") SEARCH else READ
        HxTraceActivity.FileWrite -> if (n == "write_file") WRITE else EDIT
        HxTraceActivity.Web -> when {
            n == "web_search" || n == "x_search" -> WEB_SEARCH
            n == "web_extract" || n == "browser_navigate" || n.startsWith("fetch") || n == "browse" -> OPEN
            else -> null
        }
        HxTraceActivity.Think, HxTraceActivity.Other -> null
    }
    return when {
        // A grouped tool with its preview: "Read README.md".
        verb != null && subject != null -> ToolLine(
            verb = verb.pick(isRunning, failed),
            subject = subject,
            mono = n != "web_search" && n != "x_search" && verb !== OPEN,
        )
        // Grouped, but no preview to name (an older server, a history row without one): the tool's own name.
        verb != null -> ToolLine(verb = null, subject = toolName, mono = false)
        // Anything else is named in words: "Used Github: create issue".
        else -> ToolLine(verb = USE.pick(isRunning, failed), subject = humanizeToolName(toolName), mono = false)
    }
}

/**
 * `image_generate` -> "Image generate"; an MCP tool, `mcp__<server>__<tool>`
 * (tools/mcp_tool_schema.py `mcp_prefixed_tool_name`), -> "Server: tool".
 */
internal fun humanizeToolName(toolName: String): String {
    fun words(s: String) = s.replace('_', ' ').replace('-', ' ').trim().replace(Regex("\\s+"), " ")
    fun capital(s: String) = s.replaceFirstChar { it.uppercaseChar() }
    if (toolName.startsWith("mcp__")) {
        val rest = toolName.removePrefix("mcp__")
        val cut = rest.indexOf("__")
        if (cut > 0 && cut + 2 < rest.length) {
            val server = words(rest.substring(0, cut))
            val tool = words(rest.substring(cut + 2))
            if (server.isNotEmpty() && tool.isNotEmpty()) return "${capital(server)}: $tool"
        }
        words(rest).takeIf { it.isNotEmpty() }?.let { return capital(it) }
    }
    return capital(words(toolName)).ifEmpty { toolName }
}

package com.hermes.android.ui.component

/**
 * Cleanup an assistant reply gets before [parseContentBlocks] sees it, ported
 * from Hermes Desktop's `apps/desktop/src/lib/markdown-preprocess.ts` and
 * `markdown-code.ts`:
 *
 * 1. Reasoning blocks (`<think>…</think>` and friends) are removed, and an
 *    unterminated one at a block boundary is hidden while it streams.
 * 2. Stray backtick runs outside real fences are scrubbed.
 * 3. Fences are normalized: an empty one is dropped, one that only wraps URLs
 *    or reads as prose is unwrapped, and the opener keeps just its language.
 * 4. Citation noise is removed from prose: `[n]` markers with no matching
 *    entry under a `Sources` header, and Gemini-style `citeturn…` markers.
 *
 * Runs on the whole text on every streaming flush, so it must stay linear.
 * Pure and Compose-free, so it is unit-tested directly.
 */
fun preprocessAssistantMarkdown(text: String): String {
    if (text.isEmpty()) return text
    val cleaned = stripEmptyFenceBlocks(normalizeFenceBlocks(scrubBacktickNoise(stripReasoningBlocks(text))))
    val sourceIds = collectSourceListIds(cleaned)
    return splitFencedCode(cleaned).joinToString("") { segment ->
        if (segment.code) segment.text else rewriteVisibleProse(segment.text, sourceIds)
    }
}

// ── Reasoning blocks ─────────────────────────────────────────────────────

// Same tag set as Hermes' agent/think_scrubber.py plus Desktop's scratchpad/analysis.
private const val REASONING_TAGS = "think|thinking|reasoning|thought|reasoning_scratchpad|scratchpad|analysis"

// A run of adjacent closed blocks is one match, so the seam check sees the prose around the whole run.
private val reasoningBlockRe =
    Regex("(?:<($REASONING_TAGS)>[\\s\\S]*?</\\1>\\s*)+", RegexOption.IGNORE_CASE)

// An open tag that starts its own block with no close tag yet. Prose that only
// mentions `<thinking>` mid-sentence is not at a block boundary and survives.
private val openReasoningBlockRe =
    Regex("(^|\\n)[ \\t]*<($REASONING_TAGS)>[\\s\\S]*$", RegexOption.IGNORE_CASE)

// A half-arrived open tag (`<thin`) at a block boundary is held back for the one
// flush before it completes, instead of painting and then vanishing.
private val partialOpenReasoningTagRe = run {
    val prefixes = REASONING_TAGS.split('|')
        .flatMap { tag -> (1..tag.length).map { tag.substring(0, it) } }
        .distinct()
        .joinToString("|")
    Regex("(^|\\n)[ \\t]*<(?:$prefixes)?$", RegexOption.IGNORE_CASE)
}

internal fun stripReasoningBlocks(text: String): String {
    val closed = reasoningBlockRe.replace(text) { match ->
        val prev = text.getOrNull(match.range.first - 1)
        val next = text.getOrNull(match.range.last + 1)
        // Removing a block between two words keeps one space so they do not fuse.
        if (prev != null && next != null && !prev.isWhitespace() && !next.isWhitespace()) " " else ""
    }
    return closed.replace(openReasoningBlockRe, "$1").replace(partialOpenReasoningTagRe, "$1")
}

// ── Backtick noise ───────────────────────────────────────────────────────

private val balancedFenceRe =
    Regex("(^|\\n)([ \\t]*)(`{3,}|~{3,})([^\\n]*)\\n([\\s\\S]*?)\\n[ \\t]*\\3[ \\t]*(?=\\n|\\z)")
private val danglingCodeFenceRe =
    Regex("(^|\\n)[ \\t]*(`{3,}|~{3,})([a-z0-9][a-z0-9+#-]{0,15})[ \\t]*\\n([\\s\\S]*)\\z", RegexOption.IGNORE_CASE)
private val fenceNoiseRe = Regex("`{3,}")
private val doubleBacktickPairRe = Regex("(?<!`)``(?!`)\\s*(?<!`)``(?!`)")
private val loneDoubleBacktickRe = Regex("(^|[^`])``(?=\\s|[.,;:!?)\\]'\"\\u2014\\u2013-]|\\z)")

/** A line of [body] after its first that is exactly [marker], spaces aside. */
private fun hasCloseFenceLine(body: String, marker: String): Boolean =
    body.split('\n').drop(1).any { it.trim(' ', '\t') == marker }

/**
 * Removes backtick runs that open or close nothing: ``` left over outside any
 * balanced fence, and `` pairs. Balanced fences, and a still-streaming fence
 * that names a language and does not read as prose, are left alone.
 */
internal fun scrubBacktickNoise(text: String): String {
    val protectedRanges = ArrayList<IntRange>()
    for (match in balancedFenceRe.findAll(text)) {
        val start = match.range.first + match.groupValues[1].length
        protectedRanges.add(start..match.range.last)
    }
    danglingCodeFenceRe.find(text)?.let { match ->
        val start = match.range.first + match.groupValues[1].length
        val marker = match.groupValues[2]
        val info = match.groupValues[3]
        val body = match.groupValues[4]
        if (!hasCloseFenceLine(body, marker) && sanitizeLanguageTag(info).isNotEmpty() &&
            !isLikelyProseFence(info, body)
        ) {
            protectedRanges.add(start until text.length)
        }
    }
    protectedRanges.sortBy { it.first }

    val out = StringBuilder()
    var cursor = 0
    for (range in protectedRanges) {
        if (range.first < cursor) continue
        out.append(fenceNoiseRe.replace(text.substring(cursor, range.first), ""))
        out.append(text, range.first, range.last + 1)
        cursor = range.last + 1
    }
    out.append(fenceNoiseRe.replace(text.substring(cursor.coerceAtMost(text.length)), ""))

    var result = out.toString()
    repeat(2) {
        // Exactly two backticks on each side, so two adjacent ``` fences are never spliced into one.
        result = doubleBacktickPairRe.replace(result, "")
        result = loneDoubleBacktickRe.replace(result, "$1")
    }
    return result
}

// ── Fences ───────────────────────────────────────────────────────────────

private val validLanguageRe = Regex("^[a-z0-9][a-z0-9+#-]*$", RegexOption.IGNORE_CASE)
private val fenceLineRe = Regex("^([ \\t]*)(`{3,}|~{3,})([^\\n]*)$")
private val fenceOpenLineRe =
    Regex("^([ \\t]*(?:>[ \\t]*)*(?:(?:[-+*]|\\d+[.)])[ \\t]+)?[ \\t]*)(`{3,}|~{3,})([^\\n]*)$")
private val fenceCloseLineRe = Regex("^[ \\t]*(?:>[ \\t]*)*(`{3,}|~{3,})[ \\t]*\\r?$")
private val urlOnlyLineRe = Regex("^\\s*https?://\\S+\\s*$", RegexOption.IGNORE_CASE)

/** The info string's first word when it is a plausible language name, lowercased; otherwise "". */
internal fun sanitizeLanguageTag(tag: String): String {
    val first = tag.trim().split(Regex("\\s"), limit = 2).firstOrNull().orEmpty()
    return if (validLanguageRe.matches(first) && first.length <= 16) first.lowercase() else ""
}

private fun isUrlOnlyBlock(lines: List<String>): Boolean {
    val nonEmpty = lines.filter { it.isNotBlank() }
    return nonEmpty.isNotEmpty() && nonEmpty.all { urlOnlyLineRe.matches(it) }
}

private fun findClosingFence(lines: List<String>, start: Int, marker: String): Int {
    for (cursor in start + 1 until lines.size) {
        val close = fenceLineRe.matchEntire(lines[cursor]) ?: continue
        val closeMarker = close.groupValues[2]
        if (close.groupValues[3].isBlank() && closeMarker[0] == marker[0] && closeMarker.length >= marker.length) {
            return cursor
        }
    }
    return -1
}

/**
 * Rewrites each fence: an opener whose info is not a language becomes that text,
 * an empty closed fence goes, a fence of URLs only or of prose is unwrapped, and
 * a code fence keeps just its language (`python title="x"` → `python`).
 */
internal fun normalizeFenceBlocks(text: String): String {
    val lines = text.split('\n')
    val out = ArrayList<String>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val match = fenceLineRe.matchEntire(line)
        if (match == null) {
            out.add(line)
            index++
            continue
        }
        val indent = match.groupValues[1]
        val marker = match.groupValues[2]
        val infoRaw = match.groupValues[3].trim()
        val language = sanitizeLanguageTag(infoRaw.split(Regex("\\s+"), limit = 2).first())
        if (infoRaw.isNotEmpty() && language.isEmpty()) {
            out.add("$indent$infoRaw".trimEnd())
            index++
            continue
        }

        val closeIndex = findClosingFence(lines, index, marker)
        val bodyLines = lines.subList(index + 1, if (closeIndex == -1) lines.size else closeIndex)
        val body = bodyLines.joinToString("\n")

        if (closeIndex == -1) {
            // Still streaming: what is there so far, with no closing fence yet.
            if (body.isBlank()) {
                index++
                continue
            }
            if (isLikelyProseFence(infoRaw, body)) {
                pushProseFence(out, indent, infoRaw, bodyLines)
            } else {
                out.add("$indent$marker$language")
                out.addAll(bodyLines)
            }
            break
        }
        when {
            body.isBlank() -> Unit
            isUrlOnlyBlock(bodyLines) -> out.addAll(bodyLines)
            isLikelyProseFence(infoRaw, body) -> pushProseFence(out, indent, infoRaw, bodyLines)
            else -> {
                out.add("$indent$marker$language")
                out.addAll(bodyLines)
                out.add("$indent$marker")
            }
        }
        index = closeIndex + 1
    }
    return out.joinToString("\n")
}

private fun pushProseFence(out: MutableList<String>, indent: String, info: String, lines: List<String>) {
    // The info line is prose the model put after the fence (```Here are the steps:).
    // Desktop also keeps a bare `text` / `md` tag as a line of prose; that is dropped here.
    if (info.isNotEmpty() && info.lowercase() !in nonCodeFenceLanguages) out.add("$indent$info".trimEnd())
    out.addAll(lines)
}

internal class MarkdownSegment(val code: Boolean, val empty: Boolean, val text: String)

/**
 * Prose and fenced-code segments in order; joined, they are [text] exactly. A
 * fence opens only on its own line (after any quote or list-item prefix) and
 * closes only on a line of the same character at least as long, so a ``` inside
 * a code line or the inner fences of a ````-fenced example stay code.
 */
internal fun splitFencedCode(text: String): List<MarkdownSegment> {
    val segments = ArrayList<MarkdownSegment>()
    var cursor = 0
    var fenceMarker: String? = null
    var fenceStart = 0
    var openerEnd = 0
    var lineStart = 0
    while (true) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = if (newline == -1) text.length else newline
        val line = text.substring(lineStart, lineEnd)
        val marker = fenceMarker
        if (marker == null) {
            val open = fenceOpenLineRe.matchEntire(line)
            // A backtick run with a backtick in its info string is inline code.
            if (open != null && !(open.groupValues[2][0] == '`' && '`' in open.groupValues[3])) {
                fenceMarker = open.groupValues[2]
                fenceStart = lineStart + open.groupValues[1].length
                openerEnd = lineEnd
            }
        } else {
            val close = fenceCloseLineRe.matchEntire(line)
            if (close != null && close.groupValues[1][0] == marker[0] && close.groupValues[1].length >= marker.length) {
                segments.add(MarkdownSegment(false, false, text.substring(cursor, fenceStart)))
                segments.add(MarkdownSegment(true, lineStart == openerEnd + 1, text.substring(fenceStart, lineEnd)))
                cursor = lineEnd
                fenceMarker = null
            }
        }
        if (newline == -1) break
        lineStart = newline + 1
    }
    if (fenceMarker != null) {
        segments.add(MarkdownSegment(false, false, text.substring(cursor, fenceStart)))
        segments.add(MarkdownSegment(true, false, text.substring(fenceStart)))
    } else {
        segments.add(MarkdownSegment(false, false, text.substring(cursor)))
    }
    return segments
}

/** Drops fences whose closer is the very next line, including ones inside lists and quotes. */
private fun stripEmptyFenceBlocks(text: String): String =
    splitFencedCode(text).filterNot { it.empty }.joinToString("") { it.text }

// ── Prose or code? (markdown-code.ts) ────────────────────────────────────

private val nonCodeFenceLanguages = setOf("", "text", "plain", "plaintext", "md", "markdown")

private val codeSignalRes = listOf(
    Regex(
        "(^|\\s)(const|let|var|function|class|import|export|return|if|for|while|switch)\\b",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    ),
    Regex("=>|==|===|!=|!==|\\{|\\}|;|</?[a-z][^>]*>", RegexOption.IGNORE_CASE),
    Regex(
        "^\\s*(#include|SELECT|INSERT|UPDATE|DELETE|CREATE|DROP)\\b",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    ),
)
private val boldRe = Regex("\\*\\*[^*]+\\*\\*")
private val inlineCodeRe = Regex("`[^`\\n]+`")
private val bulletLineRe = Regex("^\\s*[-*]\\s+\\S+", RegexOption.MULTILINE)
private val urlLineRe = Regex("^\\s*https?://\\S+\\s*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

// Sentence-ending punctuation; `؟` is the Persian question mark.
private val sentencePunctuationRe = Regex("[.!?؟](?:\\s|$)")
private val configSeparatorLineRe = Regex("^[A-Za-z0-9_][\\w.-]*\\s*[:=]\\s*\\S")
private val configKeyRe = Regex("^[A-Za-z0-9_][\\w.-]*$")

/**
 * Lines that read as prose. Desktop counts lines opening with an ASCII letter or
 * digit; any letter counts here, so a Persian paragraph is prose too.
 */
private fun proseLineCount(body: String): Int = body.split('\n').count { line ->
    val first = line.trim().firstOrNull() ?: return@count false
    first.isLetterOrDigit() || first in "\"'`*-"
}

private class CodeSignals(
    val bulletLines: Int,
    val codeSignals: Int,
    val hasMarkdown: Boolean,
    val proseLines: Int,
    val trimmed: String,
    val urlLines: Int,
)

private fun codeSignals(body: String): CodeSignals {
    val trimmed = body.trim()
    return CodeSignals(
        bulletLines = bulletLineRe.findAll(trimmed).count(),
        codeSignals = codeSignalRes.sumOf { it.findAll(trimmed).count() },
        hasMarkdown = boldRe.containsMatchIn(trimmed) || inlineCodeRe.containsMatchIn(trimmed),
        proseLines = proseLineCount(trimmed),
        trimmed = trimmed,
        urlLines = urlLineRe.findAll(trimmed).count(),
    )
}

private fun isConfigDirectiveLine(line: String): Boolean {
    val trimmed = line.trim()
    if (configSeparatorLineRe.containsMatchIn(trimmed)) return true
    val tokens = trimmed.split(Regex("\\s+"))
    return tokens.size in 2..3 && configKeyRe.matches(tokens[0])
}

/**
 * Structured text (SSH config, `.env`, `key: value` listings) rather than
 * wrapped prose: it keeps its fence even though it has no code tokens.
 */
internal fun isLikelyStructuredText(body: String): Boolean {
    val lines = body.split('\n').filter { it.isNotBlank() }
    if (lines.size < 2) return false
    if (lines.any { Regex("^\\s+\\S").containsMatchIn(it) }) return true
    if (lines.any { sentencePunctuationRe.containsMatchIn(it.trim()) }) return false
    val configLines = lines.count(::isConfigDirectiveLine)
    return configLines >= maxOf(2, (lines.size * 0.6).let { kotlin.math.ceil(it).toInt() })
}

/** A fence the model wrapped around prose (a paragraph, a bullet list, links) rather than code. */
internal fun isLikelyProseFence(info: String, body: String): Boolean {
    val trimmedInfo = info.trim()
    val rawInfo = trimmedInfo.lowercase()
    val language = sanitizeLanguageTag(info)
    val infoToken = trimmedInfo.split(Regex("\\s+"), limit = 2).firstOrNull().orEmpty()
    val hasInfoTail = trimmedInfo.isNotEmpty() && trimmedInfo != infoToken

    if (Regex("^[-*+]\\s").containsMatchIn(rawInfo) || rawInfo.startsWith("http://") || rawInfo.startsWith("https://")) {
        return true
    }
    val signals = codeSignals(body)
    if (signals.trimmed.isEmpty()) return false
    if (hasInfoTail && signals.codeSignals <= 2 &&
        (signals.proseLines >= 2 || signals.bulletLines >= 1 || signals.urlLines >= 1)
    ) {
        return true
    }
    if (language !in nonCodeFenceLanguages) return false
    if (isLikelyStructuredText(body)) return false
    return (signals.bulletLines >= 2 && signals.hasMarkdown && signals.codeSignals <= 2) ||
        (signals.proseLines >= 3 && signals.codeSignals == 0)
}

// ── Citations ────────────────────────────────────────────────────────────

// `word[1]` / `word.[1, 2]`: a numeric marker glued to the text it cites. `[1](url)` is a link.
private val citationMarkerRe = Regex("(?<=[\\p{L}\\p{N})\\].,!?:;\"'”’])\\[(?:\\d+(?:\\s*,\\s*\\d+)*)](?!\\()")

// Gemini-style grounding markers: U+E200 … U+E201 around `turn<n>search<m>` ids, or the bare `citeturn…` form.
private val citationTransportMarkerRe =
    Regex("\\uE200(?:cite)?(?:\\uE202?turn\\d+search\\d+)+\\uE201?|citeturn\\d+search\\d+(?:turn\\d+search\\d+)*")
private val sourcesHeaderRe =
    Regex("^[ \\t]*(?:#{1,6}[ \\t]*)?(?:\\*\\*)?sources:?(?:\\*\\*)?[ \\t]*\\r?$", RegexOption.IGNORE_CASE)
private val sourceListEntryRe =
    Regex("^[ \\t]*(?:[-*+][ \\t]+)?\\[(\\d+(?:\\s*,\\s*\\d+)*)][ \\t]*(?:[-–:][ \\t]*)?https?://\\S")
private val fenceToggleRe = Regex("^[ \\t]*(?:```|~~~)")

/** Ids listed as `[n] url` under the reply's last `Sources` header (outside code). */
internal fun collectSourceListIds(text: String): Set<String> {
    val lines = text.split('\n')
    var inFence = false
    var header = -1
    lines.forEachIndexed { index, line ->
        if (fenceToggleRe.containsMatchIn(line)) inFence = !inFence
        else if (!inFence && sourcesHeaderRe.matches(line)) header = index
    }
    if (header == -1) return emptySet()
    val ids = HashSet<String>()
    inFence = false
    for (line in lines.drop(header + 1)) {
        if (fenceToggleRe.containsMatchIn(line)) {
            inFence = !inFence
            continue
        }
        if (inFence) continue
        sourceListEntryRe.find(line)?.let { match ->
            match.groupValues[1].split(',').forEach { ids.add(it.trim()) }
        }
    }
    return ids
}

// Inline code, then `$$…$$` / `$…$` math, are shielded from the prose rewrites:
// `\sqrt[3]{8}` would otherwise lose its index as a citation marker.
private val inlineCodeSplitRe = Regex("`[^`\\n]+`")
private val mathSpanRe =
    Regex("(?<!\\\\)\\$\\$[\\s\\S]*?(?<!\\\\)\\$\\$|(?<!\\\\)\\$(?:[^\\n$\\\\]|\\\\[^\\n])+?\\$")

private fun rewriteVisibleProse(text: String, sourceIds: Set<String>): String =
    splitKeeping(text, inlineCodeSplitRe) { prose ->
        splitKeeping(prose, mathSpanRe) { stripCitations(it, sourceIds) }
    }

/** Applies [rewrite] to the parts of [text] between [re]'s matches; the matches stay as they are. */
private fun splitKeeping(text: String, re: Regex, rewrite: (String) -> String): String {
    val out = StringBuilder()
    var cursor = 0
    for (match in re.findAll(text)) {
        out.append(rewrite(text.substring(cursor, match.range.first)))
        out.append(match.value)
        cursor = match.range.last + 1
    }
    out.append(rewrite(text.substring(cursor)))
    return out.toString()
}

private fun stripCitations(prose: String, sourceIds: Set<String>): String =
    citationMarkerRe.replace(citationTransportMarkerRe.replace(prose, "")) { marker ->
        val ids = marker.value.removeSurrounding("[", "]").split(',').map { it.trim() }
        if (ids.all { it in sourceIds }) marker.value else ""
    }

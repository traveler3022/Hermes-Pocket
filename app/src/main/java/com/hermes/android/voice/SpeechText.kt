package com.hermes.android.voice

/*
 * Reply text → what the voice says, sentence by sentence. Ported from Hermes Desktop
 * (apps/desktop/src/lib/speech-text.ts, MIT, Nous Research): the same silence for code,
 * links, file names and hashes, a table read as its header, and the same sentence cutter.
 * Persian "؟" ends a sentence too.
 */

private val EMOJI_RE = Regex("(?:[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}]|[\\x{FE0F}\\x{200D}]|[\\x{E0020}-\\x{E007F}])+")
private val FENCED_CODE_RE = Regex("```[\\s\\S]*?(?:```|$)")
private val INLINE_CODE_RE = Regex("`([^`]+)`")
private val MARKDOWN_LINK_RE = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")
private val PARAGRAPH_BREAK_RE = Regex("[ \\t]*\\n{2,}[ \\t]*")
private val PUNCTUATED_PARAGRAPH_BREAK_RE = Regex("([.!?؟])([*_~`>\"'’”)}\\]]*)[ \\t]*\\n{2,}[ \\t]*")
private val SOFT_BREAK_RE = Regex("[ \\t]*\\n[ \\t]*")
private val MEDIA_PATH_RE = Regex("[ \\t]*MEDIA:\\S+?(?=[.,;:!?)\\]]*(?:\\s|$))")
private val LINE_FINAL_COLON_RE = Regex(":\\s*$", RegexOption.MULTILINE)
private val THINKING_PREFIX_RE = Regex(
    "^\\s*(?:\\([^)\\n]{1,48}\\)\\s*)?(?:processing|thinking|reasoning|analyzing|pondering|contemplating|musing|" +
        "cogitating|ruminating|deliberating|mulling|reflecting|computing|synthesizing|formulating|brainstorming)\\.\\.\\.\\s*",
    RegexOption.IGNORE_CASE,
)
private val URL_RE = Regex("\\bhttps?://\\S+", RegexOption.IGNORE_CASE)

private val FILENAME_EXT_RE = Regex(
    "[\\w.-]{0,60}\\.(?:wav|ogg|mp3|flac|m4a|aac|py|pyc|ts|tsx|js|jsx|mjs|cjs|json|yaml|yml|toml|md|mdx|txt|csv|xlsx|xls|" +
        "pdf|png|jpg|jpeg|gif|webp|svg|log|sql|sh|bash|zsh|rs|go|java|rb|php|html|css|lock|tar|gz|zip|db|sqlite|sqlite3|" +
        "onnx|pt|bin|env|ini|conf|cfg|xml)\\b",
    RegexOption.IGNORE_CASE,
)
private val UUID_RE = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
private val HASH_PREFIX_HEX_RE = Regex("\\b(?:sha(?:-?256|-?512|-?1|3)?|blake2[ab]?|md5|crc32?)[:\\s]+[0-9a-fA-F]{7,64}", RegexOption.IGNORE_CASE)
private val HEX_RUN_RE = Regex("[0-9a-fA-F]{7,64}")
private val IDENTIFIER_TOKEN_RE = Regex("[A-Za-z0-9_./~@-]+")
private val DATE_TOKEN_RE = Regex("^\\d{4}[-/]\\d{1,2}(?:[-/]\\d{1,2})?$")
private val PATH_START_RE = Regex("^(?:~/|\\.\\.?/|/)")
private val DIGIT_RE = Regex("\\d")

private fun isDenseIdentifier(token: String): Boolean {
    if ('@' in token || DATE_TOKEN_RE.matches(token)) return false
    if (PATH_START_RE.containsMatchIn(token)) return true
    if ('/' in token && (FILENAME_EXT_RE.containsMatchIn(token) || DIGIT_RE.containsMatchIn(token))) return true
    if (FILENAME_EXT_RE.containsMatchIn(token) || UUID_RE.containsMatchIn(token)) return true
    if (HEX_RUN_RE.containsMatchIn(token) && DIGIT_RE.containsMatchIn(token)) return true
    if (!DIGIT_RE.containsMatchIn(token)) return false
    val seps = listOf('_', '.', '/').count { it in token }
    val hyphens = token.count { it == '-' }
    if (seps >= 2 || hyphens >= 2) return true
    return '/' in token || (hyphens >= 1 && seps >= 1)
}

private fun pruneIdentifierTokens(text: String): String =
    IDENTIFIER_TOKEN_RE.replace(HASH_PREFIX_HEX_RE.replace(text, " ")) { m ->
        if (isDenseIdentifier(m.value)) " " else m.value
    }

private val TABLE_DELIMITER_CELL_RE = Regex("^:?-{3,}:?$")

private class TableRow(val depth: Int, val cells: List<String>)

private fun isUnescapedPipe(row: String, index: Int): Boolean {
    var backslashes = 0
    var cursor = index - 1
    while (cursor >= 0 && row[cursor] == '\\') {
        backslashes++
        cursor--
    }
    return backslashes % 2 == 0
}

private fun splitTableCells(row: String): List<String> {
    val cells = mutableListOf<String>()
    var start = 0
    for (i in row.indices) {
        if (row[i] == '|' && isUnescapedPipe(row, i)) {
            cells += row.substring(start, i).trim()
            start = i + 1
        }
    }
    cells += row.substring(start).trim()
    return cells
}

private fun parseTableRow(line: String): TableRow? {
    var row = line
    var depth = 0
    while (true) {
        val indent = row.takeWhile { it == ' ' || it == '\t' }
        if ('\t' in indent || indent.length > 3) return null
        row = row.substring(indent.length)
        if (!row.startsWith(">")) break
        depth++
        row = row.substring(1).removePrefix(" ")
    }
    row = row.trimEnd()
    val pipes = row.indices.filter { row[it] == '|' && isUnescapedPipe(row, it) }
    if (pipes.isEmpty()) return null
    val leading = pipes.first() == 0
    val trailing = pipes.last() == row.length - 1
    if (leading) row = row.substring(1)
    if (trailing && row.isNotEmpty()) row = row.substring(0, row.length - 1)
    val cells = splitTableCells(row)
    if (cells.size < 2 && !(leading && trailing && cells.size == 1)) return null
    return TableRow(depth, cells)
}

private fun speakableTableHeader(cells: List<String>): String {
    val header = cells.map { it.replace("\\|", " ").trim() }.filter { it.isNotEmpty() }.joinToString(", ")
    return if (header.isNotEmpty() && header.last() !in ".!?:") "$header." else header
}

private fun summarizeTables(text: String): String {
    val lines = text.replace(Regex("\\r\\n?"), "\n").split('\n')
    val tableLines = HashSet<Int>()
    val headers = HashMap<Int, String>()
    var index = 1
    while (index < lines.size) {
        val delimiter = parseTableRow(lines[index])
        val header = parseTableRow(lines[index - 1])
        if (delimiter == null || header == null ||
            !delimiter.cells.all { TABLE_DELIMITER_CELL_RE.matches(it) } ||
            header.cells.size != delimiter.cells.size || header.depth != delimiter.depth
        ) {
            index++
            continue
        }
        tableLines += index - 1
        tableLines += index
        headers[index - 1] = speakableTableHeader(header.cells)
        var rowIndex = index + 1
        while (rowIndex < lines.size) {
            val body = parseTableRow(lines[rowIndex])
            if (body == null || body.depth != delimiter.depth) break
            tableLines += rowIndex
            rowIndex++
        }
        index = rowIndex
    }
    return lines.flatMapIndexed { i, line ->
        when {
            i !in tableLines -> listOf(line)
            else -> listOfNotNull(headers[i]?.takeIf { it.isNotEmpty() })
        }
    }.joinToString("\n")
}

private fun normalizeLineBreaks(text: String): String = text
    .replace(Regex("\\r\\n?"), "\n")
    .replace(Regex("(\\p{L})-\\n(\\p{L})"), "$1$2")
    .replace(PUNCTUATED_PARAGRAPH_BREAK_RE, "$1$2 ")
    .replace(PARAGRAPH_BREAK_RE, ". ")
    .replace(SOFT_BREAK_RE, " ")

/** What a voice should say for [text]: markdown, code, links and identifiers become silence. */
fun sanitizeTextForSpeech(text: String): String {
    val pre = LINE_FINAL_COLON_RE.replace(summarizeTables(text), ".")
    var t = normalizeLineBreaks(pre)
    t = FENCED_CODE_RE.replace(t, "")
    t = THINKING_PREFIX_RE.replace(t, " ")
    t = MARKDOWN_LINK_RE.replace(t, "$1")
    t = INLINE_CODE_RE.replace(t, "$1")
    t = URL_RE.replace(t, "")
    t = MEDIA_PATH_RE.replace(t, "")
    t = EMOJI_RE.replace(t, " ")
    t = pruneIdentifierTokens(t)
    return t
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("[*_~>#]"), "")
        .replace(Regex("^\\s*[-+*]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex(":\\s*$"), ".")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private val CLOSED_FENCE_RE = Regex("```[\\s\\S]*?```")
private val SENTENCE_CUT_RE = Regex("[.!?…。！？؟]+[\"'”’)\\]]*\\s+")
private const val MIN_SENTENCE_CHARS = 24

/** Complete sentences of [buffer] and the unfinished rest; [flush] also emits the rest. */
fun cutSentences(buffer: String, flush: Boolean, minChars: Int = MIN_SENTENCE_CHARS): Pair<List<String>, String> {
    val sentences = mutableListOf<String>()
    var start = 0
    for (match in SENTENCE_CUT_RE.findAll(buffer)) {
        val end = match.range.last + 1
        val candidate = buffer.substring(start, end).trim()
        // "e.g. ", "1. " stay buffered until a later boundary extends them.
        if (candidate.length >= minChars) {
            sentences += candidate
            start = end
        }
    }
    var rest = buffer.substring(start)
    if (flush) {
        rest.trim().takeIf { it.isNotEmpty() }?.let { sentences += it }
        rest = ""
    }
    return sentences to rest
}

/**
 * Sentence buffer fed with raw reply text as it streams. A code block still open is held
 * back whole, so its contents are never cut into sentences and read out.
 */
class IncrementalSpeechSentenceBuffer {
    private var buffer = ""

    fun append(delta: String): List<String> {
        // A finished code block is silence as a whole: cutting through it would split it at
        // a "." inside the code and leave half a fence for the sanitizer.
        buffer = CLOSED_FENCE_RE.replace(buffer + delta, "\n")
        val fence = openFenceStart(buffer)
        val ready = if (fence >= 0) buffer.substring(0, fence) else buffer
        val held = if (fence >= 0) buffer.substring(fence) else ""
        val (sentences, rest) = cutSentences(ready, flush = false)
        buffer = rest + held
        return sentences.map(::sanitizeTextForSpeech).filter { it.isNotBlank() }
    }

    fun flush(): List<String> {
        val text = CLOSED_FENCE_RE.replace(buffer, "\n")
        val fence = openFenceStart(text)
        val (sentences, _) = cutSentences(if (fence >= 0) text.substring(0, fence) else text, flush = true)
        buffer = ""
        return sentences.map(::sanitizeTextForSpeech).filter { it.isNotBlank() }
    }

    /** Where an unclosed ``` begins, or -1. */
    private fun openFenceStart(text: String): Int {
        var open = -1
        var i = text.indexOf("```")
        while (i >= 0) {
            open = if (open < 0) i else -1
            i = text.indexOf("```", i + 3)
        }
        return open
    }
}

// Reasoning blocks some models write into the reply itself (the chat hides them in
// ui/component/MarkdownPreprocess; Hermes' agent/think_scrubber.py has the same tag set).
private const val REASONING_TAGS = "think|thinking|reasoning|thought|reasoning_scratchpad|scratchpad|analysis"
private val CLOSED_REASONING_RE = Regex("<($REASONING_TAGS)>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
private val OPEN_REASONING_RE = Regex("(^|\\n)[ \\t]*<($REASONING_TAGS)>[\\s\\S]*$", RegexOption.IGNORE_CASE)
private val PARTIAL_TAG_AT_END_RE = Regex("(^|\\n)[ \\t]*<[a-z_]{0,21}$", RegexOption.IGNORE_CASE)

/**
 * The reply without its reasoning blocks, for the voice: a finished block is dropped, and one
 * still streaming (or a tag still being typed) hides everything after it until it closes.
 */
fun stripReasoningForSpeech(text: String): String =
    CLOSED_REASONING_RE.replace(text, " ")
        .replace(OPEN_REASONING_RE, "$1")
        .replace(PARTIAL_TAG_AT_END_RE, "$1")

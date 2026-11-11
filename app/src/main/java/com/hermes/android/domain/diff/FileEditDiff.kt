package com.hermes.android.domain.diff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A `[start, end)` range of UTF-16 units inside a line. */
data class DiffRange(val start: Int, val end: Int)

enum class DiffLineType { Add, Del, Ctx }

/** One row of a file's diff. */
sealed interface DiffRow {
    /** A line, without its +/- marker or line break; [emphasis] marks the words that changed. */
    data class Line(val type: DiffLineType, val text: String, val emphasis: List<DiffRange> = emptyList()) : DiffRow

    /** A hunk break: V4A's `@@ hint @@`, or unchanged lines left out. [hint] is V4A's context hint. */
    data class Gap(val hint: String?) : DiffRow
}

enum class FileEditKind { Modified, Added, Deleted, Moved }

/** One file a tool call edits. [newPath] is set only for a move. */
data class FileEdit(
    val path: String,
    val kind: FileEditKind,
    val rows: List<DiffRow>,
    val newPath: String? = null,
) {
    val additions: Int get() = rows.count { it is DiffRow.Line && it.type == DiffLineType.Add }
    val deletions: Int get() = rows.count { it is DiffRow.Line && it.type == DiffLineType.Del }
}

/**
 * The edits a Hermes file tool makes, read from its call's arguments
 * (tools/file_tools.py schemas, read-only reference on the server):
 * - `patch`, mode `replace` (the default): `path`, `old_string`, `new_string`;
 * - `patch`, mode `patch`: `patch`, a V4A multi-file patch (tools/patch_parser.py);
 * - `write_file`: `path`, `content`, the whole file, shown as added.
 * Anything else, or arguments in another shape, is null: the card shows them as they are.
 */
object FileEditDiff {
    /** Unchanged lines kept around a change; a longer unchanged run is cut to a [DiffRow.Gap]. */
    const val CONTEXT_LINES = 3

    /** Past this many edits a line diff gives up and shows the old text removed, the new added. */
    private const val MAX_EDIT_LENGTH = 1000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun fromToolArgs(toolName: String, argsText: String?): List<FileEdit>? {
        if (argsText.isNullOrBlank()) return null
        val args = runCatching { json.parseToJsonElement(argsText) as? JsonObject }.getOrNull() ?: return null
        fun str(key: String) = (args[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        return when (toolName) {
            "write_file" -> {
                val path = str("path")?.takeIf { it.isNotBlank() } ?: return null
                val content = str("content") ?: return null
                listOf(written(path, content))
            }
            "patch" -> when (str("mode") ?: "replace") {
                "replace" -> {
                    val path = str("path")?.takeIf { it.isNotBlank() } ?: return null
                    val old = str("old_string") ?: return null
                    val new = str("new_string") ?: return null
                    listOf(replaced(path, old, new))
                }
                "patch" -> str("patch")?.let(::parseV4a)?.takeIf { it.isNotEmpty() }
                else -> null
            }
            else -> null
        }
    }

    /** A whole file written: every line added. */
    fun written(path: String, content: String): FileEdit =
        FileEdit(path, FileEditKind.Added, splitLines(content).map { DiffRow.Line(DiffLineType.Add, it) })

    /** `old_string` replaced by `new_string`: a line diff, with words emphasised. */
    fun replaced(path: String, old: String, new: String): FileEdit {
        val lines = lineDiff(old, new)
        return FileEdit(path, FileEditKind.Modified, trimContext(withEmphasis(lines)))
    }

    private fun lineDiff(old: String, new: String): List<Pair<DiffLineType, String>> {
        // Both sides end in a line break, so "a" -> "a\nb" is one added line, not a changed one.
        fun terminated(s: String) = if (s.isEmpty() || s.endsWith("\n")) s else s + "\n"
        val changes = MyersDiff.diffLines(terminated(old), terminated(new), MyersDiff.Budget(maxEditLength = MAX_EDIT_LENGTH))
            ?: return splitLines(old).map { DiffLineType.Del to it } + splitLines(new).map { DiffLineType.Add to it }
        return changes.flatMap { change ->
            val type = when {
                change.added -> DiffLineType.Add
                change.removed -> DiffLineType.Del
                else -> DiffLineType.Ctx
            }
            splitLines(change.value).map { type to it }
        }
    }

    /**
     * A V4A patch as tools/patch_parser.py reads it: optional Begin/End markers,
     * `*** Update|Add|Delete File: p`, `*** Move File: a -> b`, hunks opened by
     * `@@ hint @@`, lines marked ` `, `-` or `+`; any other non-empty line is
     * context and `\ No newline` is skipped.
     */
    fun parseV4a(patch: String): List<FileEdit> {
        val lines = patch.split('\n').map { it.removeSuffix("\r") }
        var start = -1
        var end = lines.size
        for ((i, line) in lines.withIndex()) {
            if (BEGIN.matches(line)) {
                start = i
            } else if (END.matches(line)) {
                end = i
                break
            }
        }
        val edits = ArrayList<FileEdit>()
        var path: String? = null
        var kind = FileEditKind.Modified
        var rows = ArrayList<Pair<DiffLineType, String>?>() // null = a hunk break
        val hints = ArrayList<String?>()

        fun flush() {
            val p = path ?: return
            edits.add(FileEdit(p, kind, rowsWithGaps(rows, hints)))
            path = null
        }

        for (line in lines.subList(start + 1, end)) {
            val op = opOf(line)
            if (op != null) {
                flush()
                val (opKind, a, b) = op
                if (a.isEmpty()) continue
                when (opKind) {
                    FileEditKind.Deleted -> edits.add(FileEdit(a, opKind, emptyList()))
                    FileEditKind.Moved -> if (!b.isNullOrEmpty()) edits.add(FileEdit(a, opKind, emptyList(), newPath = b))
                    else -> {
                        path = a
                        kind = opKind
                        rows = ArrayList()
                        hints.clear()
                    }
                }
            } else if (line.startsWith("@@")) {
                if (path != null) {
                    rows.add(null)
                    hints.add(HINT.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() })
                }
            } else if (path != null && line.isNotEmpty()) {
                when (line[0]) {
                    '+' -> rows.add(DiffLineType.Add to line.substring(1))
                    '-' -> rows.add(DiffLineType.Del to line.substring(1))
                    ' ' -> rows.add(DiffLineType.Ctx to line.substring(1))
                    '\\' -> Unit
                    else -> rows.add(DiffLineType.Ctx to line)
                }
            }
        }
        flush()
        return edits
    }

    /** V4A rows, hunk by hunk: emphasis within each hunk, a [DiffRow.Gap] at each break. */
    private fun rowsWithGaps(rows: List<Pair<DiffLineType, String>?>, hints: List<String?>): List<DiffRow> {
        val out = ArrayList<DiffRow>()
        var hunk = ArrayList<Pair<DiffLineType, String>>()
        var gap = 0
        fun flushHunk() {
            out.addAll(withEmphasis(hunk))
            hunk = ArrayList()
        }
        for (row in rows) {
            if (row == null) {
                flushHunk()
                val hint = hints.getOrNull(gap++)
                // A hunk break with nothing to say before the first line is noise.
                if (out.isNotEmpty() || hint != null) out.add(DiffRow.Gap(hint))
            } else {
                hunk.add(row)
            }
        }
        flushHunk()
        // Breaks with no lines after them (an empty hunk) say nothing either.
        while (out.lastOrNull() is DiffRow.Gap && (out.last() as DiffRow.Gap).hint == null) out.removeAt(out.lastIndex)
        return out
    }

    private fun withEmphasis(lines: List<Pair<DiffLineType, String>>): List<DiffRow.Line> {
        val emphasis = Intraline.computeEmphasis(lines)
        return lines.mapIndexed { i, (type, text) -> DiffRow.Line(type, text, emphasis[i].orEmpty()) }
    }

    /** Unchanged runs longer than twice [CONTEXT_LINES] keep [CONTEXT_LINES] on each side of a change. */
    internal fun trimContext(lines: List<DiffRow.Line>, context: Int = CONTEXT_LINES): List<DiffRow> {
        if (lines.none { it.type != DiffLineType.Ctx }) return lines
        val out = ArrayList<DiffRow>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].type != DiffLineType.Ctx) {
                out.add(lines[i++])
                continue
            }
            var j = i
            while (j < lines.size && lines[j].type == DiffLineType.Ctx) j++
            val keepBefore = if (i == 0) 0 else context
            val keepAfter = if (j == lines.size) 0 else context
            if (j - i > keepBefore + keepAfter) {
                out.addAll(lines.subList(i, i + keepBefore))
                out.add(DiffRow.Gap(null))
                out.addAll(lines.subList(j - keepAfter, j))
            } else {
                out.addAll(lines.subList(i, j))
            }
            i = j
        }
        // A cut at the very start or end shows nothing on that side: leave no break there.
        if (out.firstOrNull() is DiffRow.Gap) out.removeAt(0)
        if (out.lastOrNull() is DiffRow.Gap) out.removeAt(out.lastIndex)
        return out
    }

    /** The lines of [text], without breaks; a final break ends the last line rather than starting an empty one. */
    internal fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val parts = text.split('\n').map { it.removeSuffix("\r") }
        return if (text.endsWith("\n")) parts.dropLast(1) else parts
    }

    private data class Op(val kind: FileEditKind, val path: String, val newPath: String?)

    private fun opOf(line: String): Op? {
        UPDATE.find(line)?.let { return Op(FileEditKind.Modified, it.groupValues[1].trim(), null) }
        ADD.find(line)?.let { return Op(FileEditKind.Added, it.groupValues[1].trim(), null) }
        DELETE.find(line)?.let { return Op(FileEditKind.Deleted, it.groupValues[1].trim(), null) }
        MOVE.find(line)?.let { return Op(FileEditKind.Moved, it.groupValues[1].trim(), it.groupValues[2].trim()) }
        return null
    }

    private val BEGIN = Regex("""^\*\*\*\s*Begin\s+Patch\s*$""")
    private val END = Regex("""^\*\*\*\s*End\s+Patch\s*$""")
    private val UPDATE = Regex("""^\*\*\*\s*Update\s+File:\s*(.+)""")
    private val ADD = Regex("""^\*\*\*\s*Add\s+File:\s*(.+)""")
    private val DELETE = Regex("""^\*\*\*\s*Delete\s+File:\s*(.+)""")
    private val MOVE = Regex("""^\*\*\*\s*Move\s+File:\s*(.+?)\s*->\s*(.+)""")
    private val HINT = Regex("""^@@\s*(.+?)\s*@@""")
}

/** The first [cap] rows of a diff and how many lines were left out; a cut never ends on a hunk break. */
fun cappedRows(rows: List<DiffRow>, cap: Int): Pair<List<DiffRow>, Int> {
    if (rows.size <= cap) return rows to 0
    var shown = rows.take(cap)
    while (shown.lastOrNull() is DiffRow.Gap) shown = shown.dropLast(1)
    val hiddenLines = rows.drop(shown.size).count { it is DiffRow.Line }
    return shown to hiddenLines
}

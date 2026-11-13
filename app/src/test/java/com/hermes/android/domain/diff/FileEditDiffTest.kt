package com.hermes.android.domain.diff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A file tool's arguments read as the diff they make (tools/file_tools.py, tools/patch_parser.py). */
class FileEditDiffTest {

    private fun edits(tool: String, args: String) = requireNotNull(FileEditDiff.fromToolArgs(tool, args))

    private fun lines(edit: FileEdit) = edit.rows.map { row ->
        when (row) {
            is DiffRow.Line -> when (row.type) {
                DiffLineType.Add -> "+${row.text}"
                DiffLineType.Del -> "-${row.text}"
                DiffLineType.Ctx -> " ${row.text}"
            }
            is DiffRow.Gap -> "@@${row.hint.orEmpty()}"
        }
    }

    @Test
    fun `patch replace mode is a line diff with the renamed word emphasised`() {
        val args = """{"path":"a.kt","old_string":"val x = 1\nfun foo() = x\n","new_string":"val x = 1\nfun bar() = x\n"}"""
        val edit = edits("patch", args).single()
        assertEquals("a.kt", edit.path)
        assertEquals(FileEditKind.Modified, edit.kind)
        assertEquals(listOf(" val x = 1", "-fun foo() = x", "+fun bar() = x"), lines(edit))
        assertEquals(1, edit.additions)
        assertEquals(1, edit.deletions)
        val del = edit.rows[1] as DiffRow.Line
        val add = edit.rows[2] as DiffRow.Line
        assertEquals(listOf(DiffRange(4, 7)), del.emphasis)
        assertEquals(listOf(DiffRange(4, 7)), add.emphasis)
    }

    @Test
    fun `explicit replace mode and a missing final newline`() {
        val args = """{"mode":"replace","path":"b.txt","old_string":"a","new_string":"a\nb"}"""
        assertEquals(listOf(" a", "+b"), lines(edits("patch", args).single()))
    }

    @Test
    fun `deleting text is all removed lines`() {
        val args = """{"path":"c.txt","old_string":"one\ntwo","new_string":""}"""
        assertEquals(listOf("-one", "-two"), lines(edits("patch", args).single()))
    }

    @Test
    fun `write_file is a new file with every line added`() {
        val args = """{"path":"/r/new.md","content":"# Title\n\nbody\n"}"""
        val edit = edits("write_file", args).single()
        assertEquals(FileEditKind.Added, edit.kind)
        assertEquals(listOf("+# Title", "+", "+body"), lines(edit))
    }

    @Test
    fun `v4a patch mode covers update, add, delete and move`() {
        val patch = """
            *** Begin Patch
            *** Update File: src/a.py
            @@ def main @@
             keep
            -old()
            +new()
            @@
             tail
            +more
            \ No newline at end of file
            *** Add File: docs/n.md
            +hello
            *** Delete File: old.txt
            *** Move File: x.py -> y.py
            *** End Patch
            ignored after end
        """.trimIndent()
        val args = kotlinx.serialization.json.buildJsonObject {
            put("mode", kotlinx.serialization.json.JsonPrimitive("patch"))
            put("patch", kotlinx.serialization.json.JsonPrimitive(patch))
        }.toString()
        val all = edits("patch", args)
        assertEquals(4, all.size)
        assertEquals(FileEditKind.Modified, all[0].kind)
        assertEquals("src/a.py", all[0].path)
        assertEquals(listOf("@@def main", " keep", "-old()", "+new()", "@@", " tail", "+more"), lines(all[0]))
        assertEquals(FileEditKind.Added, all[1].kind)
        assertEquals(listOf("+hello"), lines(all[1]))
        assertEquals(FileEdit("old.txt", FileEditKind.Deleted, emptyList()), all[2])
        assertEquals(FileEdit("x.py", FileEditKind.Moved, emptyList(), newPath = "y.py"), all[3])
    }

    @Test
    fun `v4a without markers and with implicit context lines`() {
        val edits = FileEditDiff.parseV4a("*** Update File: a\r\nbare context\r\n-x\r\n+y\r\n")
        assertEquals(listOf(" bare context", "-x", "+y"), lines(edits.single()))
    }

    @Test
    fun `unknown shapes fall back to the raw arguments`() {
        assertNull(FileEditDiff.fromToolArgs("terminal", """{"command":"ls"}"""))
        assertNull(FileEditDiff.fromToolArgs("patch", null))
        assertNull(FileEditDiff.fromToolArgs("patch", ""))
        // History from an older gateway sends only the one-line context, not JSON.
        assertNull(FileEditDiff.fromToolArgs("patch", "a.kt"))
        assertNull(FileEditDiff.fromToolArgs("patch", "{not json"))
        assertNull(FileEditDiff.fromToolArgs("patch", """{"path":"a.kt"}"""))
        assertNull(FileEditDiff.fromToolArgs("patch", """{"path":"a.kt","old_string":1,"new_string":"x"}"""))
        assertNull(FileEditDiff.fromToolArgs("patch", """{"mode":"other","path":"a","old_string":"a","new_string":"b"}"""))
        assertNull(FileEditDiff.fromToolArgs("patch", """{"mode":"patch","patch":"no ops here"}"""))
        assertNull(FileEditDiff.fromToolArgs("write_file", """{"path":"","content":"x"}"""))
        assertNull(FileEditDiff.fromToolArgs("write_file", """[1,2]"""))
    }

    @Test
    fun `long unchanged runs are cut to three lines around a change`() {
        val old = (1..20).joinToString("\n") { "line $it" } + "\n"
        val new = old.replace("line 10\n", "line ten\n")
        val rows = lines(FileEditDiff.replaced("f", old, new))
        assertEquals(
            listOf(" line 7", " line 8", " line 9", "-line 10", "+line ten", " line 11", " line 12", " line 13"),
            rows,
        )
        val twoChanges = old.replace("line 2\n", "line two\n").replace("line 18\n", "line eighteen\n")
        val cut = lines(FileEditDiff.replaced("f", old, twoChanges))
        assertTrue(cut.contains("@@"))
        assertEquals(" line 1", cut.first())
        assertEquals(" line 20", cut.last())
    }

    @Test
    fun `cap keeps the first rows and counts the hidden lines`() {
        val rows = (1..50).map { DiffRow.Line(DiffLineType.Add, "l$it") }
        val (shown, hidden) = cappedRows(rows, 40)
        assertEquals(40, shown.size)
        assertEquals(10, hidden)
        assertEquals(rows to 0, cappedRows(rows, 50))
        // A cut never ends on a hunk break.
        val withGap = rows.take(39) + DiffRow.Gap(null) + rows.drop(39)
        val (shownGap, hiddenGap) = cappedRows(withGap, 40)
        assertEquals(39, shownGap.size)
        assertEquals(11, hiddenGap)
    }

    @Test
    fun `split lines drops only the final break`() {
        assertEquals(emptyList<String>(), FileEditDiff.splitLines(""))
        assertEquals(listOf("a", ""), FileEditDiff.splitLines("a\n\n"))
        assertEquals(listOf("a", "b"), FileEditDiff.splitLines("a\r\nb"))
    }
}

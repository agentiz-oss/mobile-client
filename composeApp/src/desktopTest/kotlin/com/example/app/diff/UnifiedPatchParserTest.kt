package com.example.app.diff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parser's contract is "render the server's patch the way the web viewer does", so these
 * cases mirror what `parse-diff` handles on the dashboard — plus the ways a real patch is hostile:
 * a size-capped patch cut mid-hunk, CRLF content, quoted non-ASCII paths, and the `-- ` mail
 * signature of `git format-patch` output, which is one dash away from a deleted line.
 */
class UnifiedPatchParserTest {

    private fun patchOf(vararg lines: String) = lines.joinToString("\n")

    @Test
    fun `single file, single hunk - line numbers and counters`() {
        val files = UnifiedPatchParser.parse(
            patchOf(
                "diff --git a/src/main.kt b/src/main.kt",
                "index 1111111..2222222 100644",
                "--- a/src/main.kt",
                "+++ b/src/main.kt",
                "@@ -10,4 +10,5 @@ fun main() {",
                " context1",
                "-old line",
                "+new line one",
                "+new line two",
                " context2",
                " context3",
                "",
            ),
        )

        assertEquals(1, files.size)
        val file = files.single()
        assertEquals("src/main.kt", file.newPath)
        assertEquals("src/main.kt", file.oldPath)
        assertEquals(ChangeType.Modified, file.changeType)
        assertEquals(2, file.additions)
        assertEquals(1, file.deletions)

        val hunk = file.hunks.single()
        assertEquals("@@ -10,4 +10,5 @@ fun main() {", hunk.header)
        assertEquals(10, hunk.oldStart)
        assertEquals(4, hunk.oldCount)
        assertEquals(10, hunk.newStart)
        assertEquals(5, hunk.newCount)

        val lines = hunk.lines
        assertEquals(6, lines.size)
        assertEquals(DiffLineType.Context, lines[0].lineType)
        assertEquals(10, lines[0].oldLineNumber)
        assertEquals(10, lines[0].newLineNumber)
        assertEquals(DiffLineType.Deleted, lines[1].lineType)
        assertEquals("old line", lines[1].content)
        assertEquals(11, lines[1].oldLineNumber)
        assertNull(lines[1].newLineNumber)
        assertEquals(DiffLineType.Added, lines[2].lineType)
        assertEquals(11, lines[2].newLineNumber)
        assertNull(lines[2].oldLineNumber)
        assertEquals(12, lines[3].newLineNumber)
        assertEquals(DiffLineType.Context, lines[4].lineType)
        assertEquals(12, lines[4].oldLineNumber)
        assertEquals(13, lines[4].newLineNumber)
        assertEquals(13, lines[5].oldLineNumber)
        assertEquals(14, lines[5].newLineNumber)
    }

    @Test
    fun `unbalanced delete-add runs get no inline diff`() {
        val files = UnifiedPatchParser.parse(
            patchOf(
                "diff --git a/a.kt b/a.kt",
                "--- a/a.kt",
                "+++ b/a.kt",
                "@@ -1,2 +1,3 @@",
                "-old line",
                "+new line one",
                "+new line two",
                " tail",
                "",
            ),
        )
        val lines = files.single().hunks.single().lines
        assertNull(lines[0].inlineDiff)
        assertNull(lines[1].inlineDiff)
        assertNull(lines[2].inlineDiff)
    }

    @Test
    fun `paired edit is split into prefix, changed middle and suffix`() {
        val files = UnifiedPatchParser.parse(
            patchOf(
                "diff --git a/a.kt b/a.kt",
                "--- a/a.kt",
                "+++ b/a.kt",
                "@@ -1,2 +1,2 @@",
                "-val enabled = false",
                "+val enabled = true",
                " tail",
                "",
            ),
        )
        val lines = files.single().hunks.single().lines

        val deleted = lines[0].inlineDiff!!
        assertEquals(listOf("val enabled = ", "fals", "e"), deleted.segments.map { it.content })
        assertEquals(listOf(false, true, false), deleted.segments.map { it.isAdded })
        assertEquals(listOf(0, 14, 18), deleted.segments.map { it.startIndex })

        val added = lines[1].inlineDiff!!
        assertEquals(listOf("val enabled = ", "tru", "e"), added.segments.map { it.content })
        assertEquals(listOf(false, true, false), added.segments.map { it.isAdded })
        assertTrue(added.hasInlineDiff)

        // A rewritten line with nothing in common is left without inline emphasis.
        val rewritten = UnifiedPatchParser.parse(
            patchOf(
                "diff --git a/b.kt b/b.kt",
                "--- a/b.kt",
                "+++ b/b.kt",
                "@@ -1 +1 @@",
                "-alpha",
                "+zzz",
                "",
            ),
        ).single().hunks.single().lines
        assertNull(rewritten[0].inlineDiff)
        assertNull(rewritten[1].inlineDiff)
    }

    @Test
    fun `new, deleted, renamed and binary files`() {
        val files = UnifiedPatchParser.parse(
            patchOf(
                "diff --git a/added.txt b/added.txt",
                "new file mode 100644",
                "index 0000000..e69de29",
                "--- /dev/null",
                "+++ b/added.txt",
                "@@ -0,0 +1,2 @@",
                "+hello",
                "+world",
                "diff --git a/gone.txt b/gone.txt",
                "deleted file mode 100644",
                "index e69de29..0000000",
                "--- a/gone.txt",
                "+++ /dev/null",
                "@@ -1,2 +0,0 @@",
                "-goodbye",
                "-world",
                "diff --git a/old-name.txt b/new-name.txt",
                "similarity index 90%",
                "rename from old-name.txt",
                "rename to new-name.txt",
                "index 1111111..2222222 100644",
                "--- a/old-name.txt",
                "+++ b/new-name.txt",
                "@@ -1,2 +1,2 @@",
                "-x",
                "+y",
                " z",
                "diff --git a/logo.png b/logo.png",
                "index 3333333..4444444 100644",
                "Binary files a/logo.png and b/logo.png differ",
                "",
            ),
        )

        assertEquals(4, files.size)

        val added = files[0]
        assertEquals(ChangeType.Added, added.changeType)
        assertEquals("added.txt", added.path)
        assertEquals("added.txt", added.oldPath)
        assertEquals(2, added.additions)
        assertEquals(1, added.hunks.single().lines.first().newLineNumber)

        val deleted = files[1]
        assertEquals(ChangeType.Deleted, deleted.changeType)
        assertEquals("gone.txt", deleted.path)
        assertEquals(2, deleted.deletions)

        val renamed = files[2]
        assertEquals(ChangeType.Renamed, renamed.changeType)
        assertEquals("old-name.txt", renamed.oldPath)
        assertEquals("new-name.txt", renamed.newPath)
        assertEquals("old-name.txt → new-name.txt", renamed.path)

        val binary = files[3]
        assertTrue(binary.isBinary)
        assertTrue(binary.hunks.isEmpty())
        assertEquals("logo.png", binary.path)
    }

    @Test
    fun `hunk header without a count defaults to 1`() {
        val files = UnifiedPatchParser.parse(
            patchOf(
                "diff --git a/a.txt b/a.txt",
                "--- a/a.txt",
                "+++ b/a.txt",
                "@@ -1 +1,2 @@",
                " first",
                "+second",
                "",
            ),
        )
        val hunk = files.single().hunks.single()
        assertEquals(1, hunk.oldCount)
        assertEquals(2, hunk.newCount)
        assertEquals(2, hunk.lines.size)
    }

    @Test
    fun `no newline at end of file marker is not a line`() {
        val files = UnifiedPatchParser.parse(
            patchOf(
                "diff --git a/a.txt b/a.txt",
                "--- a/a.txt",
                "+++ b/a.txt",
                "@@ -1 +1 @@",
                "-old",
                "\\ No newline at end of file",
                "+new",
                "\\ No newline at end of file",
                "",
            ),
        )
        val lines = files.single().hunks.single().lines
        assertEquals(2, lines.size)
        assertEquals("old", lines[0].content)
        assertEquals("new", lines[1].content)
    }

    @Test
    fun `CRLF stays part of the line content`() {
        val files = UnifiedPatchParser.parse(
            patchOf(
                "diff --git a/win.txt b/win.txt",
                "--- a/win.txt",
                "+++ b/win.txt",
                "@@ -1,2 +1,2 @@",
                " context\r",
                "-old\r",
                "+new\r",
                "",
            ),
        )
        val lines = files.single().hunks.single().lines
        assertEquals("context\r", lines[0].content)
        assertEquals("old\r", lines[1].content)
        assertEquals("new\r", lines[2].content)
    }

    @Test
    fun `patch truncated mid-hunk returns what parsed and does not throw`() {
        val withoutTrailingNewline = patchOf(
            "diff --git a/t.txt b/t.txt",
            "index 1111111..2222222 100644",
            "--- a/t.txt",
            "+++ b/t.txt",
            "@@ -1,10 +1,10 @@",
            " ctx",
            "-o",
            "+n",
        )
        for (patch in listOf(withoutTrailingNewline, withoutTrailingNewline + "\n")) {
            val files = UnifiedPatchParser.parse(patch)
            val hunk = files.single().hunks.single()
            assertEquals(3, hunk.lines.size)
            assertEquals(1, files.single().additions)
            assertEquals(1, files.single().deletions)
        }
    }

    @Test
    fun `empty context line - as a single space and trimmed to nothing`() {
        val files = UnifiedPatchParser.parse(
            patchOf(
                "diff --git a/a.txt b/a.txt",
                "--- a/a.txt",
                "+++ b/a.txt",
                "@@ -1,5 +1,5 @@",
                " a",
                " ",
                "",
                "-b",
                "+c",
                " d",
                "",
            ),
        )
        val lines = files.single().hunks.single().lines
        assertEquals(6, lines.size)
        assertEquals(DiffLineType.Context, lines[1].lineType)
        assertEquals("", lines[1].content)
        assertEquals(DiffLineType.Context, lines[2].lineType)
        assertEquals("", lines[2].content)
        assertEquals(2, lines[1].oldLineNumber)
        assertEquals(3, lines[2].oldLineNumber)
    }

    @Test
    fun `quoted non-ASCII paths are unescaped from octal UTF-8`() {
        val files = UnifiedPatchParser.parse(
            patchOf(
                "diff --git \"a/\\320\\264\\320\\276\\320\\272.txt\" \"b/\\320\\264\\320\\276\\320\\272.txt\"",
                "index 1111111..2222222 100644",
                "--- \"a/\\320\\264\\320\\276\\320\\272.txt\"",
                "+++ \"b/\\320\\264\\320\\276\\320\\272.txt\"",
                "@@ -1 +1 @@",
                "-до",
                "+после",
                "",
            ),
        )
        val file = files.single()
        assertEquals("док.txt", file.newPath)
        assertEquals("док.txt", file.oldPath)
    }

    @Test
    fun `real git format-patch output - mail header and signature are not diff content`() {
        val patch = javaClass.classLoader!!
            .getResourceAsStream("fixture-format-patch.txt")!!
            .readBytes()
            .decodeToString()

        val files = UnifiedPatchParser.parse(patch)

        assertEquals(6, files.size)
        assertEquals(
            "composeApp/src/androidMain/kotlin/com/example/app/push/Push.android.kt",
            files[0].path,
        )
        // git show --numstat of the fixture commit: 3/5/13/3/15/3 additions, zero deletions —
        // in particular the "-- " signature after the last hunk must not count as a deletion.
        assertEquals(listOf(3, 5, 13, 3, 15, 3), files.map { it.additions })
        assertEquals(listOf(0, 0, 0, 0, 0, 0), files.map { it.deletions })
        assertTrue(files.all { it.changeType == ChangeType.Modified })
        assertFalse(files.any { it.isBinary })

        // Line numbering stays consistent deep into a multi-hunk file.
        val appKt = files[1]
        assertEquals(2, appKt.hunks.size)
        val lastHunk = appKt.hunks.last()
        assertEquals(203, lastHunk.newStart)
        assertEquals(lastHunk.newStart + lastHunk.newCount - 1, lastHunk.lines.last().newLineNumber)
    }
}

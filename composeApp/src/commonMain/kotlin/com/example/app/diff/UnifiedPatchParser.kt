package com.example.app.diff

/**
 * Parses a unified git patch (`git diff` / the server's `AgentRunDiff.patch`) into the [FileDiff]
 * model the viewer renders. The dashboard feeds the same patch through `parse-diff`, so this
 * parser's contract is "show that patch the way the web does".
 *
 * Deliberately forgiving: the server cuts a patch at its size cap, so the last file or hunk can
 * end mid-line — everything that did parse is returned and nothing throws. Anything between
 * recognised markers that the parser does not understand is skipped, not fatal.
 */
object UnifiedPatchParser {

    private val hunkHeaderRegex = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*""")

    fun parse(patch: String): List<FileDiff> {
        if (patch.isBlank()) return emptyList()
        val files = mutableListOf<FileDiff>()
        var builder: FileBuilder? = null

        // Split on \n only: content of CRLF files keeps its \r, which is part of the line.
        val lines = patch.split("\n")
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            when {
                line.startsWith("diff --git ") -> {
                    builder?.build()?.let(files::add)
                    builder = FileBuilder(line.removePrefix("diff --git "))
                    index++
                }
                builder == null -> index++ // preamble before the first file (format-patch mail header etc.)
                line.startsWith("@@") -> {
                    val match = hunkHeaderRegex.matchEntire(line)
                    if (match == null) {
                        index++ // an @@ line that is not a hunk header — skip like any other noise
                    } else {
                        index = parseHunk(lines, index, match, builder)
                    }
                }
                else -> {
                    builder.headerLine(line)
                    index++
                }
            }
        }
        builder?.build()?.let(files::add)
        return files
    }

    /** Consumes one hunk starting at [headerIndex]; returns the index of the first line after it. */
    private fun parseHunk(lines: List<String>, headerIndex: Int, match: MatchResult, builder: FileBuilder): Int {
        val oldStart = match.groupValues[1].toInt()
        val oldCount = match.groupValues[2].ifEmpty { "1" }.toInt()
        val newStart = match.groupValues[3].toInt()
        val newCount = match.groupValues[4].ifEmpty { "1" }.toInt()

        val hunkLines = mutableListOf<DiffLine>()
        var oldLine = oldStart
        var newLine = newStart
        var oldRemaining = oldCount
        var newRemaining = newCount

        var index = headerIndex + 1
        // The counts decide where the hunk ends — a `-- ` mail signature after the last hunk must
        // not read as a deleted line. A truncated patch simply runs out of lines first.
        while (index < lines.size && (oldRemaining > 0 || newRemaining > 0)) {
            val line = lines[index]
            when {
                line.startsWith("\\") -> {
                    // "\ No newline at end of file" — an annotation of the previous line, not a line.
                }
                line.startsWith("+") -> {
                    hunkLines += DiffLine(line.substring(1), DiffLineType.Added, newLineNumber = newLine++)
                    newRemaining--
                }
                line.startsWith("-") -> {
                    hunkLines += DiffLine(line.substring(1), DiffLineType.Deleted, oldLineNumber = oldLine++)
                    oldRemaining--
                }
                // The final "" of a patch that ends with \n is split()'s artifact, not a line;
                // it is only ever reached mid-hunk when the patch was truncated there.
                line.isEmpty() && index == lines.lastIndex -> break
                line.startsWith(" ") || line.isEmpty() -> {
                    // A fully blank line is an empty context line whose single space was trimmed
                    // somewhere in transit; git itself emits " ".
                    hunkLines += DiffLine(
                        content = if (line.isEmpty()) "" else line.substring(1),
                        lineType = DiffLineType.Context,
                        oldLineNumber = oldLine++,
                        newLineNumber = newLine++,
                    )
                    oldRemaining--
                    newRemaining--
                }
                else -> break // malformed or truncated mid-hunk: keep what parsed
            }
            index++
        }

        builder.hunks += DiffHunk(
            header = lines[headerIndex],
            lines = withInlineDiffs(hunkLines),
            oldStart = oldStart,
            oldCount = oldCount,
            newStart = newStart,
            newCount = newCount,
        )
        return index
    }

    /**
     * Word-level second pass: a run of exactly N deleted lines followed by N added ones is the
     * shape an edit leaves, so the i-th pair is compared and its common prefix/suffix split off.
     * Everything else — pure insertions, pure deletions, unbalanced runs — is left untouched.
     */
    private fun withInlineDiffs(lines: List<DiffLine>): List<DiffLine> {
        val result = lines.toMutableList()
        var index = 0
        while (index < result.size) {
            if (result[index].lineType != DiffLineType.Deleted) {
                index++
                continue
            }
            var deletedEnd = index
            while (deletedEnd < result.size && result[deletedEnd].lineType == DiffLineType.Deleted) deletedEnd++
            var addedEnd = deletedEnd
            while (addedEnd < result.size && result[addedEnd].lineType == DiffLineType.Added) addedEnd++

            val deletedCount = deletedEnd - index
            val addedCount = addedEnd - deletedEnd
            if (deletedCount == addedCount) {
                for (pair in 0 until deletedCount) {
                    val deleted = result[index + pair]
                    val added = result[deletedEnd + pair]
                    val (deletedInline, addedInline) = pairInlineDiff(deleted.content, added.content)
                    result[index + pair] = deleted.copy(inlineDiff = deletedInline)
                    result[deletedEnd + pair] = added.copy(inlineDiff = addedInline)
                }
            }
            index = addedEnd
        }
        return result
    }

    /** Splits both sides of one edited line into unchanged-prefix / changed-middle / unchanged-suffix. */
    private fun pairInlineDiff(deleted: String, added: String): Pair<InlineDiff?, InlineDiff?> {
        val shorter = minOf(deleted.length, added.length)
        var prefix = 0
        while (prefix < shorter && deleted[prefix] == added[prefix]) prefix++
        var suffix = 0
        while (suffix < shorter - prefix &&
            deleted[deleted.length - 1 - suffix] == added[added.length - 1 - suffix]
        ) suffix++

        // Nothing in common — a rewritten line gains nothing from whole-line "emphasis".
        if (prefix == 0 && suffix == 0) return null to null

        return segmentsFor(deleted, prefix, suffix) to segmentsFor(added, prefix, suffix)
    }

    private fun segmentsFor(content: String, prefix: Int, suffix: Int): InlineDiff? {
        val middleEnd = content.length - suffix
        val segments = mutableListOf<InlineDiffSegment>()
        if (prefix > 0) segments += InlineDiffSegment(content.substring(0, prefix), isAdded = false, startIndex = 0)
        if (middleEnd > prefix) {
            segments += InlineDiffSegment(content.substring(prefix, middleEnd), isAdded = true, startIndex = prefix)
        }
        if (suffix > 0) segments += InlineDiffSegment(content.substring(middleEnd), isAdded = false, startIndex = middleEnd)
        return if (segments.size > 1) InlineDiff(segments) else null
    }

    /** Per-file parse state; headers arrive one line at a time, [build] settles precedence. */
    private class FileBuilder(private val gitHeaderRest: String) {
        val hunks = mutableListOf<DiffHunk>()
        var isBinary = false
        private var newFile = false
        private var deletedFile = false
        private var renamed = false
        private var copied = false
        private var oldPathFromMarker: String? = null // "--- a/…" or "rename from …"
        private var newPathFromMarker: String? = null // "+++ b/…" or "rename to …"

        fun headerLine(line: String) {
            when {
                line.startsWith("new file mode") -> newFile = true
                line.startsWith("deleted file mode") -> deletedFile = true
                line.startsWith("rename from ") -> {
                    renamed = true
                    oldPathFromMarker = unquoteGitPath(line.removePrefix("rename from "))
                }
                line.startsWith("rename to ") -> {
                    renamed = true
                    newPathFromMarker = unquoteGitPath(line.removePrefix("rename to "))
                }
                line.startsWith("copy from ") -> {
                    copied = true
                    oldPathFromMarker = unquoteGitPath(line.removePrefix("copy from "))
                }
                line.startsWith("copy to ") -> {
                    copied = true
                    newPathFromMarker = unquoteGitPath(line.removePrefix("copy to "))
                }
                line.startsWith("Binary files ") || line == "GIT binary patch" -> isBinary = true
                line.startsWith("--- ") -> {
                    stripMarkerPath(line.removePrefix("--- "))?.let { oldPathFromMarker = it }
                }
                line.startsWith("+++ ") -> {
                    stripMarkerPath(line.removePrefix("+++ "))?.let { newPathFromMarker = it }
                }
                // index/mode/similarity lines carry nothing the viewer shows.
            }
        }

        /** `a/path`, `"a/путь"` or `/dev/null` after a `---`/`+++` marker; null for /dev/null. */
        private fun stripMarkerPath(raw: String): String? {
            val unquoted = unquoteGitPath(raw.substringBefore('\t'))
            if (unquoted == "/dev/null") return null
            return unquoted.removePrefix("a/").removePrefix("b/")
        }

        fun build(): FileDiff? {
            val fromGitHeader = splitGitHeaderPaths(gitHeaderRest)
            var oldPath = oldPathFromMarker ?: fromGitHeader?.first ?: ""
            var newPath = newPathFromMarker ?: fromGitHeader?.second ?: ""
            // One side of an add/delete is /dev/null; the model wants the real path on both so
            // the viewer never titles a card with nothing.
            if (oldPath.isEmpty()) oldPath = newPath
            if (newPath.isEmpty()) newPath = oldPath
            if (oldPath.isEmpty() && newPath.isEmpty()) return null

            return FileDiff(
                oldPath = oldPath,
                newPath = newPath,
                changeType = when {
                    newFile -> ChangeType.Added
                    deletedFile -> ChangeType.Deleted
                    renamed -> ChangeType.Renamed
                    copied -> ChangeType.Copied
                    else -> ChangeType.Modified
                },
                hunks = hunks.toList(),
                additions = hunks.sumOf { it.addedLines },
                deletions = hunks.sumOf { it.deletedLines },
                isBinary = isBinary,
            )
        }
    }

    /**
     * The two paths of `diff --git a/… b/…`. Only a fallback — `---`/`+++`/`rename` lines override
     * — so the unquoted-with-spaces case settles for the common heuristic of splitting at the last
     * ` b/`.
     */
    private fun splitGitHeaderPaths(rest: String): Pair<String, String>? {
        var remainder = rest
        val oldRaw: String
        if (remainder.startsWith("\"")) {
            val closing = closingQuote(remainder) ?: return null
            oldRaw = unquoteGitPath(remainder.substring(0, closing + 1))
            remainder = remainder.substring(closing + 1).trimStart(' ')
        } else {
            val split = remainder.lastIndexOf(" b/").takeIf { it >= 0 }
                ?: remainder.lastIndexOf(" \"b/").takeIf { it >= 0 }
                ?: return null
            oldRaw = remainder.substring(0, split)
            remainder = remainder.substring(split + 1)
        }
        val newRaw = unquoteGitPath(remainder)
        return oldRaw.removePrefix("a/") to newRaw.removePrefix("b/")
    }

    private fun closingQuote(quoted: String): Int? {
        var index = 1
        while (index < quoted.length) {
            when (quoted[index]) {
                '\\' -> index += 2
                '"' -> return index
                else -> index++
            }
        }
        return null
    }

    /**
     * Undoes git's C-style path quoting (`core.quotePath`): `"файл"` arrives as
     * `"\320\266..."` — octal escapes are raw UTF-8 bytes, so the unescaped bytes are decoded as
     * one UTF-8 string rather than char by char.
     */
    private fun unquoteGitPath(raw: String): String {
        if (!raw.startsWith("\"") || !raw.endsWith("\"") || raw.length < 2) return raw
        val inner = raw.substring(1, raw.length - 1)
        val bytes = ArrayList<Byte>(inner.length)
        var index = 0
        while (index < inner.length) {
            val char = inner[index]
            if (char != '\\' || index == inner.length - 1) {
                // Anything git left unescaped inside quotes is printable ASCII.
                bytes += char.code.toByte()
                index++
                continue
            }
            val escaped = inner[index + 1]
            if (escaped in '0'..'7') {
                var value = 0
                var digits = 0
                while (digits < 3 && index + 1 + digits < inner.length && inner[index + 1 + digits] in '0'..'7') {
                    value = value * 8 + (inner[index + 1 + digits] - '0')
                    digits++
                }
                bytes += value.toByte()
                index += 1 + digits
            } else {
                bytes += when (escaped) {
                    'n' -> '\n'.code.toByte()
                    't' -> '\t'.code.toByte()
                    'r' -> '\r'.code.toByte()
                    'b' -> '\b'.code.toByte()
                    else -> escaped.code.toByte() // covers \\ and \" and anything exotic
                }
                index += 2
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}

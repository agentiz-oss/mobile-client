// Adapted from FuwaGit (https://github.com/JamGmilk/FuwaGit), MIT License.
//
// MIT License
//
// Copyright (c) 2026 JamGmilk
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
package com.example.app.diff

/**
 * Inline diff segment (word-level diff).
 * Used to highlight which part of a line actually changed.
 *
 * @param content Segment content
 * @param isAdded true if these characters are the changed part, false for the unchanged rest
 * @param startIndex Starting position in the line content
 */
data class InlineDiffSegment(
    val content: String,
    val isAdded: Boolean,
    val startIndex: Int,
)

/**
 * Inline diff information.
 *
 * @param segments List of diff segments within a line
 */
data class InlineDiff(
    val segments: List<InlineDiffSegment>,
) {
    val hasInlineDiff: Boolean get() = segments.size > 1
}

/** Diff line type. */
enum class DiffLineType {
    /** Added line */
    Added,

    /** Deleted line */
    Deleted,

    /** Unchanged context line */
    Context,

    /** File header information */
    Header,
}

/**
 * Single line in a diff.
 *
 * @param content Line content (without the leading diff symbol)
 * @param lineType Line type
 * @param oldLineNumber Line number in old file (starting from 1, null for added lines)
 * @param newLineNumber Line number in new file (starting from 1, null for deleted lines)
 * @param inlineDiff Inline diff info (for highlighting word-level changes)
 */
data class DiffLine(
    val content: String,
    val lineType: DiffLineType,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null,
    val inlineDiff: InlineDiff? = null,
)

/**
 * Diff hunk — a changed block in a file diff.
 *
 * @param header Hunk header (e.g., @@ -1,5 +1,6 @@)
 * @param lines All lines in this hunk
 * @param oldStart Starting line number in old file
 * @param oldCount Number of lines in old file
 * @param newStart Starting line number in new file
 * @param newCount Number of lines in new file
 */
data class DiffHunk(
    val header: String,
    val lines: List<DiffLine>,
    val oldStart: Int = 0,
    val oldCount: Int = 0,
    val newStart: Int = 0,
    val newCount: Int = 0,
) {
    /** Number of added lines. */
    val addedLines: Int get() = lines.count { it.lineType == DiffLineType.Added }

    /** Number of deleted lines. */
    val deletedLines: Int get() = lines.count { it.lineType == DiffLineType.Deleted }
}

/**
 * How a file changed. FuwaGit keeps a wider `GitChangeType` for its status screens; a patch
 * viewer only ever sees these five.
 */
enum class ChangeType {
    Added,
    Deleted,
    Modified,
    Renamed,
    Copied,
}

/**
 * File diff result.
 *
 * @param oldPath Old file path (original path when renamed)
 * @param newPath New file path (new path when renamed)
 * @param changeType Change type
 * @param hunks List of diff hunks
 * @param additions Total number of added lines
 * @param deletions Total number of deleted lines
 * @param isBinary Whether this is a binary file
 * @param oldContent Old file content (for side-by-side display; a parsed patch never carries it)
 * @param newContent New file content (for side-by-side display; a parsed patch never carries it)
 */
data class FileDiff(
    val oldPath: String,
    val newPath: String,
    val changeType: ChangeType,
    val hunks: List<DiffHunk> = emptyList(),
    val additions: Int = 0,
    val deletions: Int = 0,
    val isBinary: Boolean = false,
    val oldContent: String? = null,
    val newContent: String? = null,
) {
    /** File path (prefers new path). */
    val path: String get() = if (changeType == ChangeType.Renamed) "$oldPath → $newPath" else newPath
}

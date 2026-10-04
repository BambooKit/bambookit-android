package com.bambookit.android.data

/** One display line of a unified diff. */
sealed interface DiffLine
/** [type] is '+', '-' or ' '; old/new line numbers are null on the side the line does not exist. */
data class DiffCode(val type: Char, val oldNo: Int?, val newNo: Int?, val text: String) : DiffLine
data class DiffHunkHeader(val text: String) : DiffLine
data class DiffSection(val text: String) : DiffLine
/** "\ No newline at end of file" and similar markers. */
data class DiffNote(val text: String) : DiffLine

private val hunkRe = Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$")

/**
 * Parses a unified diff into display lines with old/new line numbers. Handles CRLF, several hunks,
 * several files in one patch, "\ No newline at end of file" and hunks whose blank context lines lost
 * their leading space. File headers (diff --git, index, ---, +++) are not shown; lines are counted by the
 * hunk header so a "--- x" or "+++ x" line inside a hunk is still code.
 */
fun parseUnifiedDiff(patch: String): List<DiffLine> {
    val out = mutableListOf<DiffLine>()
    var inHunk = false
    var oldNo = 0
    var newNo = 0
    var oldLeft = 0
    var newLeft = 0
    val lines = patch.split("\n").let { if (it.isNotEmpty() && it.last().isEmpty()) it.dropLast(1) else it }
    for (raw in lines) {
        val line = raw.removeSuffix("\r")
        if (line.startsWith("\\")) {
            if (inHunk || out.isNotEmpty()) out += DiffNote(line.removePrefix("\\").trim())
            continue
        }
        if (inHunk && (oldLeft > 0 || newLeft > 0)) {
            val type = line.firstOrNull() ?: ' '
            val text = if (line.isEmpty()) "" else line.substring(1)
            when (type) {
                '+' -> { out += DiffCode('+', null, newNo++, text); newLeft-- ; continue }
                '-' -> { out += DiffCode('-', oldNo++, null, text); oldLeft--; continue }
                ' ' -> { out += DiffCode(' ', oldNo++, newNo++, text); oldLeft--; newLeft--; continue }
                else -> inHunk = false
            }
        }
        val h = hunkRe.find(line)
        if (h != null) {
            oldNo = h.groupValues[1].toInt()
            oldLeft = h.groupValues[2].ifEmpty { "1" }.toInt()
            newNo = h.groupValues[3].toInt()
            newLeft = h.groupValues[4].ifEmpty { "1" }.toInt()
            inHunk = true
            out += DiffHunkHeader(line)
            continue
        }
        inHunk = false
    }
    return out
}

/** Added and removed line counts of a patch (what +N −M should say). */
data class DiffStats(val additions: Int, val deletions: Int)

fun diffStats(patch: String): DiffStats {
    var a = 0
    var d = 0
    for (l in parseUnifiedDiff(patch)) if (l is DiffCode) when (l.type) { '+' -> a++; '-' -> d++ }
    return DiffStats(a, d)
}

/** How a changed file should be shown when there is no ordinary text diff. */
enum class FileState { Modified, New, Deleted, Renamed, Binary, TooLarge }

private val binaryRe = Regex("(?m)^(Binary files .* differ|GIT binary patch)")

/**
 * The state shown for a changed file: explicit NEW / DELETED / RENAMED / BINARY / TOO LARGE instead of an
 * error or an empty diff. [truncated] comes from the PC when it cut a large file.
 */
fun fileState(status: String?, patches: List<String>, truncated: Boolean = false): FileState = when {
    patches.any { binaryRe.containsMatchIn(it) } -> FileState.Binary
    truncated -> FileState.TooLarge
    status == "added" -> FileState.New
    status == "deleted" -> FileState.Deleted
    status == "renamed" -> FileState.Renamed
    else -> FileState.Modified
}

package com.bambookit.android.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.DiffCode
import com.bambookit.android.data.DiffHunkHeader
import com.bambookit.android.data.DiffNote
import com.bambookit.android.data.DiffSection
import com.bambookit.android.data.Part
import com.bambookit.android.data.command
import com.bambookit.android.data.diffStats
import com.bambookit.android.data.endedAt
import com.bambookit.android.data.errorText
import com.bambookit.android.data.exit
import com.bambookit.android.data.filePath
import com.bambookit.android.data.jsonText
import com.bambookit.android.data.outputText
import com.bambookit.android.data.parseUnifiedDiff
import com.bambookit.android.data.patch
import com.bambookit.android.data.startedAt
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.DiffAddedBg
import com.bambookit.android.presentation.theme.DiffAddedText
import com.bambookit.android.presentation.theme.DiffContextText
import com.bambookit.android.presentation.theme.DiffHunk
import com.bambookit.android.presentation.theme.DiffHunkBg
import com.bambookit.android.presentation.theme.DiffRemovedBg
import com.bambookit.android.presentation.theme.DiffRemovedText
import com.bambookit.android.presentation.theme.LineNumber
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusFailedTint
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import com.bambookit.android.presentation.theme.Transparent
import kotlinx.coroutines.delay
import java.time.Duration
import kotlinx.serialization.json.JsonObject

/** Lines shown before a long block collapses behind "Show all". */
private const val COLLAPSED_LINES = 14

/** A copy button that confirms with a check mark. 48 dp touch target. */
@Composable
internal fun CopyButton(text: String, label: String = "Copy") {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    IconButton(onClick = { clipboard.setText(AnnotatedString(text)); copied = true }) {
        Icon(
            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, if (copied) "Copied" else label,
            tint = if (copied) StatusSuccess else TextSecondary, modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Complete text in a monospace block: never cut, scrolls sideways, selectable, with a copy button. Blocks longer
 * than [collapsedLines] start collapsed with "Show all N lines".
 */
@Composable
internal fun CollapsibleBlock(
    text: String,
    label: String? = null,
    color: Color = TextPrimary,
    background: Color = CodeBlockBackground,
    collapsedLines: Int = COLLAPSED_LINES,
    startExpanded: Boolean = false,
) {
    val lines = remember(text) { text.count { it == '\n' } + 1 }
    val long = lines > collapsedLines
    var expanded by rememberSaveable(text.hashCode(), lines) { mutableStateOf(startExpanded || !long) }
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(shape).background(background).border(1.dp, BambooBorder, shape)) {
        Row(Modifier.fillMaxWidth().background(BambooSurfaceElevated).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label ?: "text", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            if (long) Text(plural(lines, "line"), color = TextMuted, fontSize = 11.sp)
            CopyButton(text, "Copy ${label ?: "text"}")
        }
        val shown = if (expanded) text else text.lineSequence().take(collapsedLines).joinToString("\n")
        SelectionContainer {
            Text(
                shown, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, softWrap = false,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),
            )
        }
        if (long) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (expanded) "Show less" else "Show all $lines lines", fontSize = 12.sp)
        }
    }
}

/**
 * A unified diff drawn GitHub-style without a lazy list (for chat and requests): line numbers, +/− rows,
 * hunk headers, "\ No newline" notes. Long diffs collapse behind "Show all".
 */
@Composable
internal fun InlineDiff(patch: String, label: String? = null, collapsedLines: Int = 40) {
    val lines = remember(patch) { parseUnifiedDiff(patch) }
    if (lines.isEmpty()) {
        CollapsibleBlock(patch, label ?: "diff")
        return
    }
    val stats = remember(patch) { diffStats(patch) }
    val long = lines.size > collapsedLines
    var expanded by rememberSaveable(patch.hashCode()) { mutableStateOf(!long) }
    val digits = remember(lines) { lines.maxOfOrNull { l -> if (l is DiffCode) maxOf(l.oldNo ?: 0, l.newNo ?: 0) else 0 }.toString().length.coerceAtLeast(2) }
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(shape).background(CodeBlockBackground).border(1.dp, BambooBorder, shape)) {
        Row(Modifier.fillMaxWidth().background(BambooSurfaceElevated).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label ?: "diff", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text("+${stats.additions}", color = DiffAddedText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            Text("−${stats.deletions}", color = DiffRemovedText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            CopyButton(patch, "Copy diff")
        }
        SelectionContainer {
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                (if (expanded) lines else lines.take(collapsedLines)).forEach { l ->
                    when (l) {
                        is DiffHunkHeader -> Text(l.text, color = DiffHunk, fontFamily = FontFamily.Monospace, fontSize = 11.sp, softWrap = false, modifier = Modifier.background(DiffHunkBg).padding(horizontal = 8.dp, vertical = 2.dp))
                        is DiffNote -> Text("  ${l.text}", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, softWrap = false, modifier = Modifier.padding(horizontal = 8.dp))
                        is DiffSection -> Text(l.text, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        is DiffCode -> {
                            val (bg, fg) = when (l.type) {
                                '+' -> DiffAddedBg to DiffAddedText
                                '-' -> DiffRemovedBg to DiffRemovedText
                                else -> Transparent to DiffContextText
                            }
                            Row(Modifier.background(bg)) {
                                Text(
                                    (l.oldNo?.toString() ?: "").padStart(digits) + " " + (l.newNo?.toString() ?: "").padStart(digits),
                                    color = LineNumber, fontFamily = FontFamily.Monospace, fontSize = 11.sp, softWrap = false, modifier = Modifier.padding(horizontal = 6.dp),
                                )
                                Text(
                                    "${if (l.type == ' ') ' ' else l.type} ${l.text}", color = fg, fontFamily = FontFamily.Monospace, fontSize = 11.sp, softWrap = false,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (long) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (expanded) "Show less" else "Show all ${lines.size} lines", fontSize = 12.sp)
        }
    }
}

private val shownInputKeys = setOf("command", "filePath", "filepath", "path", "description")

private fun durationLabel(p: Part): String? {
    val s = p.startedAt() ?: return null
    val e = p.endedAt() ?: return null
    val ms = Duration.between(s, e).toMillis().coerceAtLeast(0)
    return if (ms < 1000) "${ms} ms" else if (ms < 60_000) "%.1f s".format(ms / 1000.0) else "${ms / 60_000} min ${(ms / 1000) % 60} s"
}

/**
 * A tool call in the chat: status, tool, title, duration and exit code; tap to see everything the PC sent —
 * the command, the file, the input, the full output, the error and the diff. Errors are shown open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolCard(p: Part) {
    val error = p.errorText()
    var open by rememberSaveable(p.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val command = p.command()
    val path = p.filePath()
    val output = p.outputText()
    val patch = p.patch()
    // Input fields not already shown as the command or the file (e.g. edit's oldString/newString, a search pattern).
    val input = when (val i = p.input) {
        is JsonObject -> i.filterKeys { it !in shownInputKeys }.takeIf { it.isNotEmpty() }?.let { jsonText(JsonObject(it)) }
        else -> jsonText(i)
    }
    val hasMore = command != null || path != null || output != null || patch != null || input != null || error != null
    Column(
        Modifier.fillMaxWidth().clip(shape).background(BambooSurface).border(1.dp, if (error != null) StatusFailed.copy(alpha = 0.45f) else BambooBorder, shape)
            .clickable(enabled = hasMore, onClickLabel = if (open) "Hide details" else "Show details") { open = !open }
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            when (p.toolStatus) {
                "running", "pending" -> CircularProgressIndicator(Modifier.size(13.dp), color = StatusRunning, strokeWidth = 1.5.dp)
                "completed" -> Icon(Icons.Filled.CheckCircle, "Done", tint = StatusSuccess, modifier = Modifier.size(14.dp))
                "error" -> Icon(Icons.Filled.ErrorOutline, "Failed", tint = StatusFailed, modifier = Modifier.size(14.dp))
                else -> Icon(Icons.Filled.Build, null, tint = TextMuted, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.tool ?: "tool", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    p.toolTitle?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.width(8.dp))
                        Mono(it, color = TextSecondary, size = 12, maxLines = if (open) 6 else 1, modifier = Modifier.weight(1f))
                    }
                }
                val chips = listOfNotNull(durationLabel(p), p.exit()?.let { "exit $it" }, patch?.let { val st = diffStats(it); "+${st.additions} −${st.deletions}" })
                if (chips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { c ->
                        Text(c, color = if (c.startsWith("exit ") && c != "exit 0") StatusWarning else TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (hasMore) Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Hide details" else "Show details", tint = TextMuted, modifier = Modifier.padding(horizontal = 12.dp).size(18.dp))
        }
        if (!open && error != null) {
            Text(error.lineSequence().take(3).joinToString("\n"), color = StatusFailed, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 3, modifier = Modifier.padding(start = 22.dp, bottom = 6.dp, end = 8.dp))
        }
        if (open) Column(Modifier.padding(end = 6.dp, bottom = 6.dp)) {
            command?.let { CollapsibleBlock(it, "command") }
            path?.let { Mono(it, color = TextSecondary, size = 11, maxLines = 3, modifier = Modifier.padding(top = 6.dp)) }
            input?.let { CollapsibleBlock(it, "input") }
            output?.let { CollapsibleBlock(it, "output") }
            error?.let { CollapsibleBlock(it, "error", color = StatusFailed, background = StatusFailedTint, startExpanded = true) }
            patch?.let { InlineDiff(it) }
            if (p.truncated) Text("Your PC sent a shortened version of this tool call.", color = StatusWarning, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            if (output == null && error == null && patch == null && p.toolStatus == "completed") {
                Text("No output was recorded for this tool call. Older BambooKit Desktop versions only send the title.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

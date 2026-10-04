package com.bambookit.android.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import kotlinx.coroutines.delay

/** Model ids from the engine's built-in free provider are shown under the BambooKit name. */
fun brandModel(model: String?): String? = model?.replace(Regex("(?i)opencode"), "BambooKit")

private val inlineRe = Regex("`([^`]+)`|\\*\\*([^*]+)\\*\\*|(?<![*\\w])\\*([^*\\s][^*]*)\\*(?!\\*)")
private val bulletRe = Regex("^\\s*([-*+]|\\d+\\.)\\s+")

private fun inline(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in inlineRe.findAll(text)) {
        append(text.substring(last, m.range.first))
        when {
            m.groups[1] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = BambooSurfaceElevated, color = StatusRunning, fontSize = 13.sp)) { append(" ${m.groupValues[1]} ") }
            m.groups[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = TextPrimary)) { append(m.groupValues[2]) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color)) { append(m.groupValues[3]) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
private fun CodeBlock(language: String, body: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(shape).background(CodeBlockBackground).border(1.dp, BambooBorder, shape)) {
        Row(Modifier.fillMaxWidth().background(BambooSurfaceElevated).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(language.ifBlank { "code" }, color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(body)); copied = true }, modifier = Modifier.size(34.dp)) {
                Icon(if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, if (copied) "Copied" else "Copy code", tint = if (copied) StatusSuccess else TextSecondary, modifier = Modifier.size(15.dp))
            }
        }
        HorizontalDivider(color = BambooBorder)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Text(body, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, softWrap = false)
        }
    }
}

/** Small markdown renderer for agent messages: code fences, headings, bullets, quotes, rules, bold, italic and inline code. */
@Composable
fun MarkdownText(text: String, color: Color = TextPrimary) {
    val chunks = text.split("```")
    Column(Modifier.fillMaxWidth()) {
        chunks.forEachIndexed { i, chunk ->
            if (i % 2 == 1) {
                val language = chunk.substringBefore('\n', "").trim().takeIf { !it.contains(' ') }.orEmpty()
                val body = (if (chunk.contains('\n')) chunk.substringAfter('\n') else chunk).trimEnd('\n', ' ')
                CollapsibleBlock(body, language.ifBlank { "code" }, collapsedLines = 30)
            } else {
                chunk.trim('\n').split("\n").forEach { raw ->
                    val line = raw.trimEnd()
                    when {
                        line.isBlank() -> Spacer(Modifier.height(6.dp))
                        line.matches(Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")) -> HorizontalDivider(color = BambooBorderStrong, modifier = Modifier.padding(vertical = 8.dp))
                        line.startsWith("#") -> {
                            val level = line.takeWhile { it == '#' }.length
                            Text(
                                inline(line.trimStart('#', ' '), color), color = TextPrimary,
                                fontSize = when (level) { 1 -> 18.sp; 2 -> 16.sp; else -> 15.sp }, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        line.startsWith(">") -> Row(Modifier.padding(vertical = 2.dp)) {
                            Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(BambooBorderStrong))
                            Spacer(Modifier.width(10.dp))
                            Text(inline(line.trimStart('>', ' '), TextSecondary), color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        bulletRe.containsMatchIn(line) -> {
                            val indent = line.takeWhile { it == ' ' }.length / 2
                            val bullet = Regex("^\\s*(\\d+\\.)").find(line)?.groupValues?.get(1) ?: "•"
                            Row(Modifier.padding(start = (4 + indent * 14).dp, top = 1.dp, bottom = 1.dp)) {
                                Text("$bullet ", color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                                Text(inline(line.replace(bulletRe, ""), color), color = color, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                        else -> Text(inline(line, color), color = color, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

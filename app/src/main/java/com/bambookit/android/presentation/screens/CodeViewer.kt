package com.bambookit.android.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bambookit.android.data.FileView
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.LineNumber
import com.bambookit.android.presentation.theme.SearchMatch
import com.bambookit.android.presentation.theme.SearchMatchCurrent
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.SyntaxComment
import com.bambookit.android.presentation.theme.SyntaxKeyword
import com.bambookit.android.presentation.theme.SyntaxNumber
import com.bambookit.android.presentation.theme.SyntaxPlain
import com.bambookit.android.presentation.theme.SyntaxString
import com.bambookit.android.presentation.theme.SyntaxType
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** "812 B", "4.2 KB", "1.1 MB". */
internal fun formatSize(bytes: Long?): String = when {
    bytes == null -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

// ================================================================== syntax highlighting

/** Lines longer than this are cut for display (minified files). */
private const val MAX_LINE = 2000

/** Widest the scrollable code area may get, in pixels. */
internal const val MAX_CONTENT_PX = 60_000f

private class Syntax(
    val lineComments: List<String> = emptyList(),
    val blockStart: String? = null,
    val blockEnd: String? = null,
    val quotes: String = "\"'",
    val keywords: Set<String> = emptySet(),
    val caseInsensitive: Boolean = false,
    val typesByCase: Boolean = true,
    val jsonKeys: Boolean = false,
    val markupTags: Boolean = false,
)

private val C_KEYWORDS = setOf(
    "abstract", "as", "async", "await", "break", "case", "catch", "class", "const", "continue", "data", "debugger", "def", "default",
    "defer", "delete", "do", "else", "enum", "export", "extends", "false", "final", "finally", "fn", "for", "from", "fun", "func", "function",
    "go", "if", "impl", "implements", "import", "in", "instanceof", "interface", "internal", "is", "let", "match", "mod", "module", "mut",
    "namespace", "new", "null", "nil", "object", "open", "override", "package", "private", "protected", "pub", "public", "readonly", "return",
    "sealed", "select", "self", "static", "struct", "super", "suspend", "switch", "this", "throw", "throws", "trait", "true", "try", "type",
    "typeof", "undefined", "use", "using", "val", "var", "void", "when", "where", "while", "with", "yield", "int", "long", "float", "double",
    "boolean", "bool", "char", "byte", "short", "string", "unsigned", "signed", "constexpr", "include", "define", "lateinit", "companion",
    "inline", "reified", "crossinline", "operator", "infix", "typealias", "get", "set", "declare", "keyof", "never", "unknown", "any",
)
private val HASH_KEYWORDS = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else", "except", "False", "finally", "for",
    "from", "global", "if", "import", "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while",
    "with", "yield", "self", "end", "begin", "module", "require", "then", "fi", "do", "done", "case", "esac", "function", "local", "export",
    "echo", "true", "false", "null", "yes", "no", "param", "foreach",
)
private val SQL_KEYWORDS = setOf(
    "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create", "table", "index", "view", "drop", "alter",
    "add", "column", "primary", "key", "foreign", "references", "not", "null", "and", "or", "join", "left", "right", "inner", "outer", "on",
    "group", "by", "order", "having", "limit", "offset", "as", "distinct", "union", "all", "case", "when", "then", "else", "end", "default",
    "unique", "constraint", "if", "exists", "begin", "commit", "returning", "integer", "text", "varchar", "boolean", "timestamp", "true", "false",
)

private val CLIKE = Syntax(listOf("//"), "/*", "*/", "\"'`", C_KEYWORDS)
private val CSS = Syntax(emptyList(), "/*", "*/", "\"'", setOf("important", "media", "import", "keyframes", "from", "to"), typesByCase = false)
private val HASH = Syntax(listOf("#"), null, null, "\"'", HASH_KEYWORDS)
private val SQL = Syntax(listOf("--"), "/*", "*/", "'\"", SQL_KEYWORDS, caseInsensitive = true, typesByCase = false)
private val JSON = Syntax(emptyList(), null, null, "\"", setOf("true", "false", "null"), typesByCase = false, jsonKeys = true)
private val MARKUP = Syntax(emptyList(), "<!--", "-->", "\"'", emptySet(), typesByCase = false, markupTags = true)

private enum class Lang { Code, Markdown, Plain }

private fun syntaxFor(path: String): Pair<Lang, Syntax?> {
    val name = path.substringAfterLast('/').substringAfterLast('\\').lowercase()
    val ext = name.substringAfterLast('.', "")
    return when {
        ext in setOf("kt", "kts", "java", "js", "jsx", "mjs", "cjs", "ts", "tsx", "mts", "cts", "c", "h", "cc", "cpp", "hpp", "cs", "go", "rs",
            "swift", "dart", "scala", "php", "groovy", "gradle", "m", "zig", "proto") -> Lang.Code to CLIKE
        ext in setOf("css", "scss", "less", "sass") -> Lang.Code to CSS
        ext in setOf("py", "rb", "sh", "bash", "zsh", "ps1", "psm1", "yml", "yaml", "toml", "ini", "cfg", "conf", "r", "pl", "env") ||
            name in setOf("dockerfile", "makefile", ".gitignore", ".env", ".dockerignore") -> Lang.Code to HASH
        ext == "sql" -> Lang.Code to SQL
        ext in setOf("json", "jsonc", "json5", "lock") || name.endsWith(".webmanifest") -> Lang.Code to JSON
        ext in setOf("html", "htm", "xml", "svg", "vue", "svelte", "xaml", "plist", "csproj") -> Lang.Code to MARKUP
        ext in setOf("md", "mdx", "markdown") -> Lang.Markdown to null
        else -> Lang.Plain to null
    }
}

private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

/** Highlights one line; returns whether a block comment is still open at its end. */
private fun highlightLine(line: String, syntax: Syntax, startInBlock: Boolean, out: AnnotatedString.Builder): Boolean {
    var i = 0
    val n = line.length
    var inBlock = startInBlock
    fun span(color: androidx.compose.ui.graphics.Color, from: Int, to: Int) = out.withStyle(SpanStyle(color = color)) { append(line, from, to) }
    while (i < n) {
        if (inBlock) {
            val end = line.indexOf(syntax.blockEnd!!, i)
            val stop = if (end < 0) n else end + syntax.blockEnd.length
            span(SyntaxComment, i, stop)
            i = stop
            if (end >= 0) inBlock = false
            continue
        }
        val c = line[i]
        if (syntax.lineComments.any { line.startsWith(it, i) } && !(c == '#' && i > 0 && line[i - 1] == '$')) {
            span(SyntaxComment, i, n)
            break
        }
        if (syntax.blockStart != null && line.startsWith(syntax.blockStart, i)) {
            inBlock = true
            span(SyntaxComment, i, i + syntax.blockStart.length)
            i += syntax.blockStart.length
            continue
        }
        if (c in syntax.quotes) {
            var j = i + 1
            while (j < n && line[j] != c) j += if (line[j] == '\\') 2 else 1
            val end = min(n, j + 1)
            val isKey = syntax.jsonKeys && line.substring(end).trimStart().startsWith(":")
            span(if (isKey) SyntaxType else SyntaxString, i, end)
            i = end
            continue
        }
        if (syntax.markupTags && c == '<' && i + 1 < n && (line[i + 1] == '/' || line[i + 1].isLetter() || line[i + 1] == '!')) {
            var j = i + 1
            if (j < n && (line[j] == '/' || line[j] == '!')) j++
            while (j < n && (line[j].isLetterOrDigit() || line[j] in "-_:.")) j++
            span(SyntaxKeyword, i, j)
            i = j
            continue
        }
        if (c.isDigit() && (i == 0 || !isIdentPart(line[i - 1]))) {
            var j = i + 1
            while (j < n && (line[j].isLetterOrDigit() || line[j] == '.' || line[j] == '_')) j++
            span(SyntaxNumber, i, j)
            i = j
            continue
        }
        if (isIdentStart(c) || (c == '@' && i + 1 < n && isIdentStart(line[i + 1]))) {
            var j = i + 1
            while (j < n && isIdentPart(line[j])) j++
            val word = line.substring(i, j)
            val key = if (syntax.caseInsensitive) word.lowercase() else word
            when {
                key in syntax.keywords -> span(SyntaxKeyword, i, j)
                c == '@' -> span(SyntaxType, i, j)
                syntax.markupTags && j < n && line[j] == '=' -> span(SyntaxType, i, j)
                syntax.typesByCase && c.isUpperCase() -> span(SyntaxType, i, j)
                else -> span(SyntaxPlain, i, j)
            }
            i = j
            continue
        }
        span(SyntaxPlain, i, i + 1)
        i++
    }
    return inBlock
}

private fun highlightMarkdown(line: String): AnnotatedString = buildAnnotatedString {
    val t = line.trimStart()
    when {
        t.startsWith("#") -> withStyle(SpanStyle(color = SyntaxKeyword)) { append(line) }
        t.startsWith(">") || t.startsWith("```") -> withStyle(SpanStyle(color = SyntaxComment)) { append(line) }
        else -> {
            var last = 0
            for (m in Regex("`[^`]+`|\\[[^]]+]\\([^)]+\\)").findAll(line)) {
                withStyle(SpanStyle(color = SyntaxPlain)) { append(line, last, m.range.first) }
                withStyle(SpanStyle(color = if (m.value.startsWith("`")) SyntaxString else SyntaxType)) { append(m.value) }
                last = m.range.last + 1
            }
            withStyle(SpanStyle(color = SyntaxPlain)) { append(line, last, line.length) }
        }
    }
}

private fun displayLine(line: String) = if (line.length > MAX_LINE) line.take(MAX_LINE) else line

/** Highlights all lines (runs off the main thread). Large files are shown without colors. */
private fun highlightAll(path: String, lines: List<String>, totalChars: Int): List<AnnotatedString> {
    val (lang, syntax) = syntaxFor(path)
    if (lang == Lang.Plain || lines.size > 25_000 || totalChars > 700_000) return lines.map { AnnotatedString(displayLine(it)) }
    if (lang == Lang.Markdown) return lines.map { highlightMarkdown(displayLine(it)) }
    var inBlock = false
    return lines.map { raw ->
        val b = AnnotatedString.Builder()
        inBlock = highlightLine(displayLine(raw), syntax!!, inBlock, b)
        b.toAnnotatedString()
    }
}

// ================================================================== viewer

private data class Match(val line: Int, val start: Int)

/**
 * Full-screen, read-only code viewer for project files read live from the PC: line numbers,
 * horizontal scrolling, light syntax highlighting, find in file, copy path / copy all.
 */
@Composable
fun CodeViewer(view: FileView, pcTitle: String, onClose: () -> Unit, onRetry: () -> Unit) {
    // While App lock is showing, dialogs (separate windows) are not drawn over it.
    if (LocalAppLocked.current) return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(BambooObsidian).imePadding()) {
            val content = view.content
            val lineCount = remember(content) { content?.let { lineCountOf(it) } }
            var searching by rememberSaveable(view.path) { mutableStateOf(false) }

            ScreenTopBar(
                title = view.path.substringAfterLast('/').substringAfterLast('\\'),
                subtitle = listOfNotNull(
                    "Read only",
                    view.size?.let { formatSize(it) },
                    lineCount?.let { plural(it, "line") },
                    view.path,
                ).joinToString(" · "),
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") } },
                actions = {
                    if (content != null) IconButton(onClick = { searching = !searching }) { Icon(Icons.Filled.Search, "Find in file") }
                    CodeMenu(view.path, content)
                },
            )
            HorizontalDivider(color = BambooBorder)

            when {
                view.loading -> LoadingState("Opening the file on $pcTitle…")
                view.error != null -> ErrorState(
                    title = errorTitle(view.error, pcTitle, "Can't show this file"),
                    message = errorMessage(view.error, pcTitle),
                    icon = Icons.Filled.ErrorOutline,
                    onRetry = onRetry,
                    color = if (view.error.desktopUnavailable || view.error.desktopOutdated || view.error.timedOut) StatusWarning else StatusFailed,
                    diagnosis = view.error.diagnosis,
                )
                content == null -> Unit
                else -> CodeContent(view.path, content, searching, onCloseSearch = { searching = false })
            }
        }
    }
}

/** NUL characters in the first part of the text: a binary file (images, archives...) sent as text. */
internal fun looksBinary(content: String): Boolean = content.indexOf('\u0000').let { it in 0 until 8000 }

private fun lineCountOf(content: String) = if (content.isEmpty()) 0 else content.count { it == '\n' } + 1

/** "More" menu of a code view: copy path, copy all. */
@Composable
internal fun CodeMenu(path: String, content: String?) {
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = BambooSurfaceElevated) {
            DropdownMenuItem(
                text = { Text("Copy path") }, leadingIcon = { Icon(Icons.Filled.Link, null) },
                onClick = { menu = false; clipboard.setText(AnnotatedString(path)); toast("Path copied") },
            )
            DropdownMenuItem(
                text = { Text("Copy all") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, enabled = content != null,
                onClick = {
                    menu = false
                    if (content != null) {
                        if (content.length > 500_000) toast("This file is too large to copy on the phone")
                        else runCatching { clipboard.setText(AnnotatedString(content)) }
                            .onSuccess { toast("Copied ${plural(lineCountOf(content), "line")}") }
                            .onFailure { toast("Could not copy this file") }
                    }
                },
            )
        }
    }
}

/**
 * Read-only text of one file: line numbers, syntax highlighting, horizontal scrolling and (when
 * [searching]) a find bar. Shared by the code viewer and the Before/After views of a changed file.
 */
@Composable
internal fun CodeContent(path: String, content: String, searching: Boolean, onCloseSearch: () -> Unit) {
    val lines = remember(content) { content.split("\r\n", "\n") }
    var query by remember { mutableStateOf("") }
    var current by remember { mutableIntStateOf(0) }
    LaunchedEffect(searching) { if (!searching) query = "" }
    val matches = remember(lines, query) {
        if (query.isEmpty()) emptyList()
        else buildList {
            lines.forEachIndexed { li, line ->
                var from = line.indexOf(query, 0, ignoreCase = true)
                while (from >= 0 && size < 5000) {
                    add(Match(li, from))
                    from = line.indexOf(query, from + max(1, query.length), ignoreCase = true)
                }
            }
        }
    }
    LaunchedEffect(matches) { current = 0 }

    Column(Modifier.fillMaxSize()) {
        if (searching) {
            SearchBar(
                query = query, onQuery = { query = it },
                counter = when { query.isEmpty() -> ""; matches.isEmpty() -> "No matches"; else -> "${current + 1}/${matches.size}${if (matches.size >= 5000) "+" else ""}" },
                onPrev = { if (matches.isNotEmpty()) current = (current - 1 + matches.size) % matches.size },
                onNext = { if (matches.isNotEmpty()) current = (current + 1) % matches.size },
                onClose = onCloseSearch,
            )
        }
        when {
            content.isEmpty() -> EmptyState("Empty file", "This file has no content.", Icons.Filled.Description)
            looksBinary(content) -> EmptyState("Binary file", "This file isn't text, so it can't be shown on the phone. Open it on your PC.", Icons.Filled.Description)
            else -> CodeLines(path, lines, content.length, matches, current, query.length)
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, counter: String, onPrev: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().background(BambooObsidian).padding(start = Space.m, end = Space.xs, top = Space.s, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            placeholder = { Text("Find in file", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextMuted) },
            suffix = { if (counter.isNotEmpty()) Text(counter, color = TextSecondary, fontSize = 12.sp) },
            textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BambooBorderStrong, unfocusedBorderColor = BambooBorder),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowUp, "Previous match", tint = TextPrimary) }
        IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowDown, "Next match", tint = TextPrimary) }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close search", tint = TextSecondary) }
    }
    HorizontalDivider(color = BambooBorder)
}

@Composable
private fun CodeLines(path: String, lines: List<String>, totalChars: Int, matches: List<Match>, current: Int, queryLength: Int) {
    // Keep the highlighted lines together with the lines they were made from: when another file is shown,
    // the previous result must not be drawn over the new file's lines.
    val highlightedFor by produceState<Pair<List<String>, List<AnnotatedString>>?>(null, path, lines) {
        value = lines to withContext(Dispatchers.Default) { highlightAll(path, lines, totalChars) }
    }
    val highlighted = highlightedFor?.takeIf { it.first === lines }?.second
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charPx = remember(measurer) { measurer.measure(AnnotatedString("0".repeat(10)), style).size.width / 10f }
    val gutterChars = lines.size.toString().length
    val gutterPx = (gutterChars + 2) * charPx
    val longest = remember(lines) { min(MAX_LINE, lines.maxOfOrNull { it.length } ?: 0) }
    val anyCut = remember(lines) { lines.any { it.length > MAX_LINE } }
    val matchesByLine = remember(matches) { matches.withIndex().groupBy({ it.value.line }, { it.index to it.value.start }) }
    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        if (anyCut) Text(
            "Lines longer than $MAX_LINE characters are cut on the phone.", color = TextMuted, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = Space.m, vertical = 4.dp),
        )
        BoxWithConstraints(Modifier.fillMaxSize().background(CodeBlockBackground)) {
            val viewportPx = with(density) { maxWidth.toPx() }
            // Capped: Compose layouts can't be arbitrarily wide (very long lines are cut at MAX_LINE anyway).
            val contentPx = min(MAX_CONTENT_PX, max(viewportPx, gutterPx + longest * charPx + with(density) { 32.dp.toPx() }))
            LaunchedEffect(current, matches) {
                val m = matches.getOrNull(current) ?: return@LaunchedEffect
                scope.launch { listState.animateScrollToItem(max(0, m.line - 6)) }
                scope.launch { hScroll.animateScrollTo(max(0, (gutterPx + m.start * charPx - viewportPx / 3f).toInt())) }
            }
            Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.width(with(density) { contentPx.toDp() }).fillMaxHeight(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(lines.size) { i ->
                            val base = highlighted?.getOrNull(i) ?: AnnotatedString(displayLine(lines[i]))
                            val lineMatches = matchesByLine[i]
                            val text = if (lineMatches == null || queryLength == 0) base else {
                                val b = AnnotatedString.Builder(base)
                                for ((index, start) in lineMatches) {
                                    val end = min(base.length, start + queryLength)
                                    if (start < end) b.addStyle(SpanStyle(background = if (index == current) SearchMatchCurrent else SearchMatch), start, end)
                                }
                                b.toAnnotatedString()
                            }
                            Row(Modifier.padding(end = 16.dp)) {
                                Text(
                                    (i + 1).toString().padStart(gutterChars), style = style, color = LineNumber,
                                    modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                                )
                                Text(text.ifEmptyLine(), style = style, color = SyntaxPlain, softWrap = false, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun AnnotatedString.ifEmptyLine(): AnnotatedString = if (isEmpty()) AnnotatedString(" ") else this

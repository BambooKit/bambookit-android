package com.bambookit.android.presentation.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.ResearchRepository
import com.bambookit.android.data.ResearchResult
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusFailedTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import kotlinx.coroutines.launch

private fun openTab(context: Context, url: String) {
    val uri = Uri.parse(url)
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri) }
        .recoverCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun ResearchScreen(store: BambooStore, onBack: () -> Unit) {
    val repo = remember { ResearchRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ResearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<ResearchResult?>(null) }
    var keyEditor by remember { mutableStateOf(false) }
    var hasKey by remember { mutableStateOf(store.geminiKey() != null) }
    BackHandler(onBack = onBack)

    fun doSearch() {
        val q = query.trim()
        if (q.isBlank() || searching) return
        searching = true
        searched = true
        scope.launch {
            results = runCatching { repo.search(q) }.getOrDefault(emptyList())
            searching = false
        }
    }

    reading?.let { r ->
        ReaderView(repo, r, onBack = { reading = null }, onOpen = { openTab(context, it) })
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Research", "Wikipedia & DuckDuckGo", navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, placeholder = { Text("Search the web") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.s))
            Button(onClick = { doSearch() }, enabled = query.isNotBlank() && !searching) {
                if (searching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Go")
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
            item { AskAiCard(store, repo, hasKey, onSetupKey = { keyEditor = true }) }
            item {
                Row(Modifier.fillMaxWidth().padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    OutlinedButton(onClick = { openTab(context, "https://www.google.com/search?q=${Uri.encode(query.ifBlank { "" })}") }, modifier = Modifier.weight(1f)) { Text("Open in Google", fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton(onClick = { openTab(context, "https://notebooklm.google.com/") }, modifier = Modifier.weight(1f)) { Text("Open NotebookLM", fontSize = 12.sp, maxLines = 1) }
                }
            }
            if (searching && results.isEmpty()) item { LoadingState("Searching…") }
            else if (searched && results.isEmpty()) item { EmptyState("No results", "Try different words.", Icons.Filled.Search) }
            else if (!searched) item { EmptyState("Search the web", "Free Wikipedia and DuckDuckGo results, with a reader. Add a Gemini key for Ask AI.", Icons.Filled.Science) }
            items(results, key = { it.source + ":" + it.url }) { r ->
                Spacer(Modifier.height(Space.s))
                ResultCard(r, onClick = { if (r.wikiTitle != null) reading = r else openTab(context, r.url) })
            }
            item { BottomSpacer() }
        }
    }

    if (keyEditor) GeminiKeyDialog(store, onDone = { hasKey = store.geminiKey() != null; keyEditor = false })
}

@Composable
private fun AskAiCard(store: BambooStore, repo: ResearchRepository, hasKey: Boolean, onSetupKey: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    BkCard(modifier = Modifier.padding(top = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.AutoAwesome)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("Ask AI", color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(if (hasKey) "Powered by your Gemini key (gemini-2.5-flash)" else "Add a Google AI Studio key to enable", color = TextSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = onSetupKey) { Text(if (hasKey) "Change key" else "Add key", fontSize = 12.sp) }
        }
        if (!hasKey) {
            Spacer(Modifier.height(Space.s))
            Text(
                "Your key stays only on this phone (encrypted). It is never sent to BambooKit.",
                color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp,
            )
            Spacer(Modifier.height(Space.xs))
            TextButton(onClick = { openTab(context, "https://aistudio.google.com/apikey") }, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Filled.Key, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Get a free key at aistudio.google.com/apikey", fontSize = 12.sp)
            }
            return@BkCard
        }
        Spacer(Modifier.height(Space.s))
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it; error = null }, placeholder = { Text("Ask anything") },
            modifier = Modifier.fillMaxWidth(), maxLines = 4,
        )
        Spacer(Modifier.height(Space.s))
        Button(
            onClick = {
                val key = store.geminiKey() ?: return@Button
                busy = true; answer = null; error = null
                scope.launch {
                    runCatching { repo.askAi(key, prompt.trim()) }
                        .onSuccess { answer = it }.onFailure { error = it.message }
                    busy = false
                }
            },
            enabled = prompt.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Ask")
        }
        error?.let { Text(it, color = StatusFailed, fontSize = 12.sp, modifier = Modifier.padding(top = Space.s)) }
        answer?.let {
            Spacer(Modifier.height(Space.s))
            SelectionContainer {
                Text(it, color = TextPrimary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(CodeBlockBackground).padding(Space.m).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ResultCard(r: ResearchResult, onClick: () -> Unit) {
    BkCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(r.source, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(ChipShape).background(BambooGreenSubtle).padding(horizontal = 7.dp, vertical = 2.dp))
        }
        if (r.snippet.isNotBlank()) Text(r.snippet, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun ReaderView(repo: ResearchRepository, r: ResearchResult, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember(r.url) { mutableStateOf<String?>(null) }
    var loading by remember(r.url) { mutableStateOf(true) }
    BackHandler(onBack = onBack)
    androidx.compose.runtime.LaunchedEffect(r.wikiTitle) {
        val wt = r.wikiTitle
        text = if (wt != null) runCatching { repo.wikipediaSummary(wt) }.getOrNull()?.ifBlank { r.snippet } ?: r.snippet else r.snippet
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(r.title, r.source, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
            if (loading) LoadingState("Loading…")
            else SelectionContainer { Text(text.orEmpty(), color = TextPrimary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = Space.m)) }
            Spacer(Modifier.height(Space.m))
            OutlinedButton(onClick = { onOpen(r.url) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Space.s))
                Text("Open full article")
            }
            BottomSpacer()
        }
    }
}

@Composable
private fun GeminiKeyDialog(store: BambooStore, onDone: () -> Unit) {
    var value by remember { mutableStateOf(store.geminiKey().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = BambooSurfaceElevated,
        icon = { Icon(Icons.Filled.Key, null) },
        title = { Text("Google AI Studio key") },
        text = {
            Column {
                Text("Your Gemini API key is stored only on this phone, encrypted. It is never sent to BambooKit and never logged.", color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(Space.m))
                OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, placeholder = { Text("AIza…") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { store.setGeminiKey(value.trim().ifBlank { null }); onDone() }) { Text("Save") } },
        dismissButton = {
            if (store.geminiKey() != null) TextButton(onClick = { store.setGeminiKey(null); onDone() }) { Text("Clear", color = StatusFailed) }
            else TextButton(onClick = onDone) { Text("Cancel") }
        },
    )
}

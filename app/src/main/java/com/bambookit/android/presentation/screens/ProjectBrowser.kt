package com.bambookit.android.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.SessionDetail
import com.bambookit.android.data.TreeEntry
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.DividerSubtle
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import com.bambookit.android.presentation.theme.Transparent

private fun parentOf(path: String) = path.trim('/').substringBeforeLast('/', "")

/**
 * Read-only folder browser for the session's project, listed live from the PC one folder at a
 * time. System back goes up a folder before leaving the tab.
 */
@Composable
fun ProjectPane(d: SessionDetail, pcTitle: String, projectName: String, onLoad: (String) -> Unit, onOpenFile: (String) -> Unit) {
    LaunchedEffect(d.sessionId) { if (d.tree == null) onLoad("") }
    val t = d.tree
    val path = t?.path ?: ""
    BackHandler(enabled = path.isNotEmpty()) { onLoad(parentOf(path)) }
    // Files this session read or changed, from the file map when it has been loaded.
    val touched = remember(d.fileMap?.entries) { d.fileMap?.entries.orEmpty().associateBy { normPath(it.path) } }

    Column(Modifier.fillMaxSize()) {
        Breadcrumbs(projectName, path, onNavigate = onLoad)
        HorizontalDivider(color = BambooBorder)
        val listing = t?.listing
        when {
            t == null || (listing == null && t.loading) -> LoadingState("Listing ${if (path.isEmpty()) "the project" else path} on $pcTitle…")
            listing == null && t.error != null -> ContentUnavailable(t.error, pcTitle, t.loading) { onLoad(path) }
            listing == null -> Unit
            else -> SessionRefresh(t.loading, { onLoad(path) }) {
                val folders = listing.entries.count { it.isDirectory }
                val files = listing.entries.size - folders
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xl)) {
                    if (t.error != null) item { StaleBanner(t.error, t.loading) { onLoad(path) } }
                    if (listing.truncated) item {
                        Banner(
                            "Large folder: showing the first ${listing.entries.size} entries.", Icons.Filled.Info,
                            color = StatusWarning, tint = StatusWarningTint, modifier = Modifier.padding(horizontal = Space.m).padding(top = Space.s),
                        )
                    }
                    if (listing.entries.isNotEmpty()) item {
                        Text(
                            listOfNotNull(folders.takeIf { it > 0 }?.let { plural(it, "folder") }, files.takeIf { it > 0 }?.let { plural(it, "file") }).joinToString(" · ") + " · read only",
                            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.s),
                        )
                    }
                    if (path.isNotEmpty()) item(key = "..") {
                        Row(
                            Modifier.fillMaxWidth().clickable { onLoad(parentOf(path)) }.padding(horizontal = Space.screen, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Up to ${parentOf(path).substringAfterLast('/').ifEmpty { projectName }}", color = TextSecondary, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = DividerSubtle)
                    }
                    if (listing.entries.isEmpty()) item { EmptyState("Empty folder", "There is nothing in this folder.", Icons.Filled.FolderOpen) }
                    items(listing.entries, key = { it.path }) { e ->
                        EntryRow(e, touched[normPath(e.path)]?.let { it.actions.firstOrNull { a -> a != "read" } ?: "read" }) {
                            if (e.isDirectory) onLoad(e.path) else onOpenFile(e.path)
                        }
                        HorizontalDivider(color = DividerSubtle)
                    }
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(projectName: String, path: String, onNavigate: (String) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(path) { scroll.animateScrollTo(scroll.maxValue) }
    val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb(projectName, isLast = segments.isEmpty(), icon = true) { onNavigate("") }
        segments.forEachIndexed { i, seg ->
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            Crumb(seg, isLast = i == segments.lastIndex) { onNavigate(segments.take(i + 1).joinToString("/")) }
        }
    }
}

@Composable
private fun Crumb(label: String, isLast: Boolean, icon: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (isLast) BambooGreenSubtle else Transparent)
            .clickable(enabled = !isLast, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(Icons.Filled.FolderOpen, null, tint = if (isLast) TextPrimary else TextSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = if (isLast) TextPrimary else TextSecondary, fontSize = 13.sp, fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun EntryRow(e: TreeEntry, action: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.screen, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (e.isDirectory) Icons.Filled.Folder else Icons.Filled.Description, null,
            tint = when {
                e.isDirectory -> TextSecondary
                action != null -> actionColor(action).first
                else -> TextMuted
            },
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(e.name, color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (action != null) {
            val (fg, bg) = actionColor(action)
            Spacer(Modifier.width(8.dp))
            Text(action, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(ChipShape).background(bg).padding(horizontal = 7.dp, vertical = 1.dp))
        }
        Spacer(Modifier.width(8.dp))
        if (e.isDirectory) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        else Text(formatSize(e.size), color = TextMuted, fontSize = 12.sp)
    }
}

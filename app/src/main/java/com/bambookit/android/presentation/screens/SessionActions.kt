package com.bambookit.android.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.NicknameCheck
import com.bambookit.android.data.SESSION_TITLE_MAX
import com.bambookit.android.data.Session
import com.bambookit.android.data.validateSessionTitle
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.LikedHeart
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextSecondary

/** Heart toggle: like / unlike a session. */
@Composable
fun StarButton(starred: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            if (starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            if (starred) "Unlike session" else "Like session",
            tint = if (starred) LikedHeart else TextMuted,
        )
    }
}

/**
 * A session card wired to the store: tap opens it, the heart likes it, long-press renames it.
 * Used by every session list so they all behave the same.
 */
@Composable
fun SessionRow(store: BambooStore, s: Session, onOpen: (String) -> Unit) {
    val renames by store.renames.collectAsState()
    var renaming by remember { mutableStateOf(false) }
    SessionCard(
        s, onClick = { onOpen(s.id) }, onToggleStar = { store.toggleStar(s) }, onLongClick = { renaming = true },
        renamingTo = renames[s.id]?.newTitle,
    )
    if (renaming) RenameSessionDialog(store, s, onDismiss = { renaming = false })
}

/**
 * Rename dialog: prefilled with the current title, 1-200 characters. The PC renames the session, so this
 * is disabled (with an explanation) while that PC is offline.
 */
@Composable
fun RenameSessionDialog(store: BambooStore, s: Session, onDismiss: () -> Unit) {
    val devices by store.devices.collectAsState()
    val pc = devices.firstOrNull { it.id == s.deviceId }
    val online = pc?.online == true
    var value by rememberSaveable(s.id) { mutableStateOf(s.title) }
    val check = validateSessionTitle(value)
    val error = (check as? NicknameCheck.Invalid)?.reason
    val changed = value.trim() != s.title
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BambooSurfaceElevated,
        icon = { Icon(Icons.Filled.Edit, null) },
        title = { Text("Rename session") },
        text = {
            Column {
                OutlinedTextField(
                    value = value, onValueChange = { value = it.replace("\n", " ").take(SESSION_TITLE_MAX + 20) },
                    label = { Text("Title") }, enabled = online, maxLines = 3, isError = error != null,
                    supportingText = { Text(error ?: "${value.trim().length}/$SESSION_TITLE_MAX", color = if (error != null) StatusFailed else TextMuted) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BambooBorderStrong, unfocusedBorderColor = BambooBorder),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (online) "${pc?.name ?: "Your PC"} renames the session; the new title shows here when it's done."
                    else "${pc?.name ?: "The session's PC"} is offline. Sessions are renamed on the PC, so open BambooKit Desktop there first.",
                    color = if (online) TextSecondary else StatusWarning, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { (check as? NicknameCheck.Ok)?.let { store.renameSession(s, it.value) }; onDismiss() },
                enabled = online && changed && check is NicknameCheck.Ok,
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Session screen top-bar actions: like, and an overflow menu with Rename and Reload. */
@Composable
fun SessionMenu(store: BambooStore, s: Session?, onReload: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    if (s != null) StarButton(s.starred, onToggle = { store.toggleStar(s) })
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More actions") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = BambooSurfaceElevated) {
            DropdownMenuItem(
                text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, enabled = s != null,
                onClick = { menu = false; renaming = true },
            )
            DropdownMenuItem(text = { Text("Reload") }, leadingIcon = { Icon(Icons.Filled.Refresh, null) }, onClick = { menu = false; onReload() })
        }
    }
    if (renaming && s != null) RenameSessionDialog(store, s, onDismiss = { renaming = false })
}

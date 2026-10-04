package com.bambookit.android.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.ContinueRequest
import com.bambookit.android.data.Device
import com.bambookit.android.data.PendingCommand
import com.bambookit.android.data.Session
import com.bambookit.android.data.SessionDetail
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.BambooSurfaceHigh
import com.bambookit.android.presentation.theme.ComposerBackground
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary

/**
 * The bottom of the session screen for a session that is not continued on the PC: it is view only
 * until the user taps "Continue on PC" (with a confirmation). Then "Opening on your PC…" is shown until the
 * PC confirms (remote = true) or 30 seconds pass.
 */
@Composable
internal fun ContinueOnPcBar(pc: Device?, pcTitle: String, request: ContinueRequest?, onContinue: () -> Unit, onDismissError: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val online = pc?.online == true
    Column(Modifier.fillMaxWidth().background(ComposerBackground).padding(horizontal = Space.m, vertical = Space.s + 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Visibility, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s))
            Text(
                "View only. Continue this session on your PC to chat from your phone.",
                color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Space.s))
        when {
            request != null && request.error == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = StatusRunning, strokeWidth = 2.dp)
                Spacer(Modifier.width(Space.s))
                Text("Opening on your PC…", color = StatusRunning, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            request?.error != null -> Column {
                Text(request.error, color = StatusFailed, fontSize = 12.sp, lineHeight = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.padding(top = Space.xs)) {
                    TextButton(onClick = onDismissError) { Text("Close") }
                    Button(onClick = onContinue, enabled = online) { Text("Try again") }
                }
            }
            else -> {
                Button(onClick = { confirm = true }, enabled = online, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DesktopWindows, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s))
                    Text("Continue on PC")
                }
                if (!online) Text(
                    "$pcTitle is offline. Open BambooKit Desktop on it, then continue the session from here.",
                    color = StatusWarning, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = BambooSurfaceElevated,
            icon = { Icon(Icons.Filled.DesktopWindows, null, tint = TextPrimary) },
            title = { Text("Continue on your PC?") },
            text = {
                Text(
                    "BambooKit Desktop on $pcTitle opens this session. You can then send messages to the agent from this phone; " +
                        "the agent keeps running on the PC.",
                    color = TextSecondary,
                )
            },
            confirmButton = { TextButton(onClick = { confirm = false; onContinue() }) { Text("Continue on PC") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}


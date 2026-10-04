package com.bambookit.android.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.ModelRef
import com.bambookit.android.data.NewSessionState
import com.bambookit.android.data.Project
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary

/**
 * "New session" in a project: the first prompt and the model, started on the project's PC. The sheet shows
 * sending, then "Starting on your PC…" until the PC reports the new session, which then opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewSessionSheet(store: BambooStore, project: Project, onOpenSession: (String) -> Unit, onDismiss: () -> Unit) {
    val state by store.newSession.collectAsState()
    val devices by store.devices.collectAsState()
    val providers by store.providers.collectAsState()
    val pc = devices.firstOrNull { it.id == project.deviceId }
    val pcName = pc?.name ?: project.deviceName ?: "your PC"
    var text by rememberSaveable(project.id) { mutableStateOf("") }
    var model by remember { mutableStateOf<ModelRef?>(null) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(project.deviceId) { store.loadProviders(project.deviceId) }
    LaunchedEffect(state) {
        (state as? NewSessionState.Created)?.let {
            store.clearNewSession()
            onOpenSession(it.sessionId)
        }
    }
    val busy = state == NewSessionState.Sending || state is NewSessionState.Waiting
    ModalBottomSheet(
        onDismissRequest = { if (!busy) { store.clearNewSession(); onDismiss() } },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BambooSurfaceElevated,
    ) {
        Column(Modifier.padding(horizontal = Space.screen).navigationBarsPadding().padding(bottom = Space.m)) {
            Text("New session", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("${project.name} · $pcName", color = TextSecondary, fontSize = 12.sp)
            if (pc?.online == false) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.s)) {
                Icon(Icons.Filled.CloudOff, null, tint = StatusWarning, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("$pcName is offline. The session starts if it reconnects within 5 minutes.", color = StatusWarning, fontSize = 12.sp)
            }
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(20_000); if (state is NewSessionState.Failed) store.clearNewSession() },
                enabled = !busy, minLines = 3, maxLines = 8,
                placeholder = { Text("What should the agent do?") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BambooBorderStrong, unfocusedBorderColor = BambooBorder),
                modifier = Modifier.fillMaxWidth().padding(top = Space.m),
            )
            ModelChip(
                "Model: " + (model?.let { modelLabel(it, providers[project.deviceId]?.info) }
                    ?: providers[project.deviceId]?.info?.default?.let { "PC default (${modelLabel(it, providers[project.deviceId]?.info)})" } ?: "PC default"),
                enabled = !busy, onClick = { picking = true },
            )
            when (val st = state) {
                is NewSessionState.Waiting -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.s)) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = StatusRunning, strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.s))
                    Text(if (st.deviceOnline) "Starting on $pcName…" else "Waiting for $pcName to come online…", color = StatusRunning, fontSize = 13.sp)
                }
                is NewSessionState.Failed -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.s)) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = StatusFailed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(st.message, color = StatusFailed, fontSize = 12.sp, lineHeight = 16.sp)
                }
                else -> Unit
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Text("Runs in ${project.directory}", color = TextMuted, fontSize = 11.sp, maxLines = 2, modifier = Modifier.weight(1f))
                TextButton(onClick = { store.clearNewSession(); onDismiss() }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
                Button(
                    onClick = { store.startSession(project, text, model) },
                    enabled = text.isNotBlank() && !busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state is NewSessionState.Failed) "Try again" else "Start")
                }
            }
        }
    }
    if (picking) ModelPickerSheet(
        store, project.deviceId, pcName, selected = model, currentLabel = null,
        onPick = { model = it; picking = false }, onDismiss = { picking = false },
    )
}

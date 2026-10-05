package com.bambookit.android.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.PendingCommand
import com.bambookit.android.data.Plan
import com.bambookit.android.data.Quota
import com.bambookit.android.data.SendState
import com.bambookit.android.data.Session
import com.bambookit.android.data.SessionDetail
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurfaceHigh
import com.bambookit.android.presentation.theme.ComposerBackground
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextSecondary

/**
 * Chat controls for a session continued on the PC: the model in use (tap to change), a message box
 * (SEND_MESSAGE) with a send button that shows sending / sent / failed-with-Retry, and Continue / Retry when
 * the agent is idle. Stop is in the activity strip while the agent works ([showActivity]).
 */
@Composable
internal fun ChatComposer(
    d: SessionDetail,
    s: Session,
    showActivity: Boolean,
    modelLabel: String,
    onPickModel: () -> Unit,
    onSend: (String, (Boolean) -> Unit) -> Unit,
    onDismissSendError: () -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    /** The account's plan: on the Free plan the messages left today are shown, and the box locks at 0. */
    plan: Plan? = null,
    /** Shown instead of the send error when today's free messages are used up (Watch ad / Upgrade). */
    limitPanel: @Composable (Plan) -> Unit = {},
) {
    var text by rememberSaveable(d.sessionId) { mutableStateOf("") }
    val last: PendingCommand? = d.commands.lastOrNull()
    val send = d.send
    val sending = send is SendState.Sending || d.sending
    val left = plan?.messagesLeft()
    val limit = plan?.limits?.phoneMessagesPerDay
    val locked = plan != null && left == 0
    fun submit(body: String) = onSend(body) { ok -> if (ok && text.trim() == body.trim()) text = "" }
    Column(Modifier.fillMaxWidth().background(ComposerBackground)) {
        if (showActivity && s.isActive) LiveActivity(s, last, onStop = onStop, compact = true)
        Column(Modifier.padding(horizontal = Space.m, vertical = Space.s)) {
            last?.takeIf { it.status != "SUCCEEDED" && it.type != "CONTINUE_ON_PC" && it.type != "SEND_MESSAGE" }?.let { CommandStatus(it) }
            if (!s.isActive) {
                val busy = last?.status == "PENDING" && last.type in setOf("CONTINUE", "RETRY")
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onContinue, enabled = !busy, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                        if (busy && last?.type == "CONTINUE") CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Continue", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onRetry, enabled = !busy, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                        if (busy && last?.type == "RETRY") CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp), tint = if (s.status == "error") StatusWarning else TextSecondary)
                        Spacer(Modifier.width(4.dp))
                        Text("Retry", fontSize = 13.sp)
                    }
                    Text(
                        if (s.status == "error") "Retry the step that failed" else "Ask the agent to keep going",
                        color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f),
                    )
                }
            }
            ModelChip(modelLabel, enabled = !sending, onClick = onPickModel)
            if (locked && plan != null) limitPanel(plan)
            else if (left != null && limit != null) QuotaLine(Quota.messagesText(left, limit), Quota.warn(left), Modifier.padding(bottom = 4.dp))
            (send as? SendState.Failed)?.takeIf { !locked }?.let { f ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = StatusFailed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(f.message, color = StatusFailed, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissSendError) { Text("Close") }
                    TextButton(onClick = { submit(f.text) }) { Text("Retry") }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.take(20_000)
                        if (send is SendState.Failed) onDismissSendError()
                    },
                    placeholder = {
                        Text(
                            when {
                                locked -> "Daily free limit reached"
                                s.isActive -> "Message the agent (sent after its current step)"
                                else -> "Message the agent"
                            },
                            fontSize = 14.sp,
                        )
                    },
                    leadingIcon = if (locked) ({ Icon(Icons.Filled.Lock, "Locked", tint = StatusWarning, modifier = Modifier.size(18.dp)) }) else null,
                    enabled = !sending && !locked,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BambooBorderStrong, unfocusedBorderColor = BambooBorder,
                        focusedContainerColor = BambooObsidian, unfocusedContainerColor = BambooObsidian,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.s))
                SendButton(send, sending, enabled = text.isNotBlank() && !sending && !locked, locked = locked) { submit(text) }
            }
        }
    }
}

/** The send button: send icon (idle), progress (sending, disabled), check (sent), retry (failed). */
@Composable
internal fun SendButton(send: SendState, sending: Boolean, enabled: Boolean, locked: Boolean = false, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (send is SendState.Failed) StatusFailed else BambooGreen, contentColor = BambooObsidian,
            disabledContainerColor = BambooSurfaceHigh, disabledContentColor = TextMuted,
        ),
        modifier = Modifier.padding(bottom = 4.dp).size(48.dp),
    ) {
        when {
            locked -> Icon(Icons.Filled.Lock, "Daily free limit reached")
            sending -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TextSecondary)
            send is SendState.Sent -> Icon(Icons.Filled.Check, if (send.deviceOnline) "Sent" else "Sent, waiting for your PC")
            send is SendState.Failed -> Icon(Icons.Filled.Refresh, "Not sent. Send again")
            else -> Icon(Icons.AutoMirrored.Filled.Send, "Send message")
        }
    }
}

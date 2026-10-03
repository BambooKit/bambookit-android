package com.bambookit.android.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.Approval
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.QuestionDraft
import com.bambookit.android.data.QuestionSpec
import com.bambookit.android.data.buildAnswers
import com.bambookit.android.data.toggle
import com.bambookit.android.data.typed
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.QuestionAccent
import com.bambookit.android.presentation.theme.QuestionAccentTint
import com.bambookit.android.presentation.theme.QuestionBorder
import com.bambookit.android.presentation.theme.RequestBorder
import com.bambookit.android.presentation.theme.SelectedOption
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import com.bambookit.android.presentation.theme.Transparent

/** What a permission request asks for, in plain words. */
internal fun permissionSentence(permission: String): String = when (permission.lowercase()) {
    "bash" -> "run a command on your PC"
    "edit" -> "edit files in the project"
    "write" -> "create or overwrite files in the project"
    "read" -> "read files"
    "list", "glob", "grep" -> "look through project files"
    "webfetch" -> "fetch a web page"
    "websearch" -> "search the web"
    "external_directory" -> "use files outside the project folder"
    "task" -> "start a helper agent"
    "doom_loop" -> "keep going after repeating the same step several times"
    "" -> "do something that needs your permission"
    else -> "use \"$permission\""
}

internal fun replyLabel(reply: String?) = when (reply) {
    "once" -> "Approve once"
    "always" -> "Always allow"
    "reject" -> "Reject"
    "answer" -> "Answer"
    else -> reply ?: "reply"
}

/**
 * A request from the agent: a permission approval (Reject / Always allow / Approve once) or a question
 * (options, optional typed answer, Submit / Dismiss). Used in the Approvals tab and the session Summary.
 */
@Composable
fun ApprovalCard(a: Approval, store: BambooStore, pcName: String? = null, onOpen: (() -> Unit)? = null) {
    val question = a.isQuestion
    BkCard(onClick = onOpen, border = if (question) QuestionBorder else RequestBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (question) IconTile(Icons.AutoMirrored.Filled.HelpOutline, tint = QuestionAccent, background = QuestionAccentTint, size = 34.dp)
            else IconTile(Icons.Filled.Shield, tint = StatusWarning, background = StatusWarningTint, size = 34.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    if (question) "Question from the agent" else "Permission request",
                    color = if (question) QuestionAccent else StatusWarning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    listOfNotNull(a.projectName, a.sessionTitle, pcName).joinToString(" · ").ifBlank { "Session" },
                    color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(relative(a.createdAt), color = TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(Space.m))
        RequestBody(a)
        Spacer(Modifier.height(Space.m))
        RequestActions(a, store)
    }
}

/** What is being asked: the permission and its patterns, or nothing extra for questions (the form shows them). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestBody(a: Approval) {
    if (a.isQuestion) return
    Text("The agent wants to ${permissionSentence(a.permission)}.", color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
    if (a.permission.isNotBlank()) {
        Spacer(Modifier.height(Space.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Chip("Permission: ${a.permission}", TextSecondary, icon = Icons.Filled.Bolt) }
    }
    a.title?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(Space.s))
        Mono(it, color = TextPrimary, size = 12, maxLines = 8, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CodeBlockBackground).padding(10.dp))
    }
    val patterns = a.patterns.filter { it.isNotBlank() }.filter { it != a.title }
    if (patterns.isNotEmpty()) {
        Spacer(Modifier.height(Space.s))
        Text(if (patterns.size == 1) "Applies to" else "Applies to these patterns", color = TextMuted, fontSize = 11.sp)
        Mono(patterns.joinToString("\n"), color = TextSecondary, size = 12, maxLines = 6, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
    }
}

/** The answer controls of a pending request (also used inline in the session timeline). */
@Composable
internal fun RequestActions(a: Approval, store: BambooStore) {
    val answering by store.answering.collectAsState()
    val busy = a.id in answering
    when {
        a.status == "RESPONDING" -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), color = StatusRunning, strokeWidth = 2.dp)
            Spacer(Modifier.width(Space.s))
            Text(
                if (a.isQuestion) "Answer sent. Waiting for the agent on your PC…" else "Sent \"${replyLabel(a.reply)}\". Waiting for the agent on your PC…",
                color = TextSecondary, fontSize = 12.sp,
            )
        }
        a.status != "PENDING" -> Unit
        a.isQuestion -> QuestionForm(a, busy, onSubmit = { store.answer(a, it) }, onDismiss = { store.respond(a, "reject") })
        else -> PermissionButtons(busy) { store.respond(a, it) }
    }
}

@Composable
private fun PermissionButtons(busy: Boolean, onReply: (String) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onReply("reject") }, enabled = !busy, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusFailed), contentPadding = PaddingValues(horizontal = 6.dp),
            ) { Text("Reject", fontSize = 13.sp) }
            OutlinedButton(onClick = { onReply("always") }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("Always allow", fontSize = 13.sp, maxLines = 1)
            }
            Button(onClick = { onReply("once") }, enabled = !busy, modifier = Modifier.weight(1.2f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Text("Approve once", fontSize = 13.sp, maxLines = 1)
            }
        }
        Text("\"Always allow\" also allows matching requests for the rest of this session.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private val DraftsSaver = Saver<List<QuestionDraft>, ArrayList<Any>>(
    save = { drafts -> ArrayList<Any>().apply { drafts.forEach { add(ArrayList(it.selected)); add(it.custom) } } },
    restore = { flat -> flat.chunked(2).map { (sel, custom) -> QuestionDraft((sel as List<*>).map { it.toString() }.toSet(), custom.toString()) } },
)

/** Header, question, options (radio for single choice, checkboxes for multiple), optional typed answer, Submit and Dismiss. */
@Composable
private fun QuestionForm(a: Approval, busy: Boolean, onSubmit: (List<List<String>>) -> Unit, onDismiss: () -> Unit) {
    val questions = a.questions.orEmpty()
    var drafts by rememberSaveable(a.id, stateSaver = DraftsSaver) { mutableStateOf(List(questions.size) { QuestionDraft() }) }
    if (questions.isEmpty()) {
        // An older PC may send a question without its details: it can only be dismissed from here.
        Text(a.title?.takeIf { it.isNotBlank() } ?: "The agent asked a question. Answer it on your PC.", color = TextPrimary, fontSize = 14.sp)
        Spacer(Modifier.height(Space.s))
        OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Dismiss") }
        return
    }
    val answers = buildAnswers(questions, drafts)
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        questions.forEachIndexed { i, q ->
            QuestionBlock(q, drafts.getOrElse(i) { QuestionDraft() }, enabled = !busy) { d -> drafts = drafts.toMutableList().also { it[i] = d } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusFailed),
            ) { Text("Dismiss") }
            Button(onClick = { answers?.let(onSubmit) }, enabled = !busy && answers != null, modifier = Modifier.weight(1.4f)) {
                if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                else Text(if (questions.size > 1) "Submit answers" else "Submit")
            }
        }
        if (answers == null) Text(
            if (questions.size > 1) "Answer every question to submit." else "Choose an answer${if (questions.any { it.allowsCustom }) " or type your own" else ""} to submit.",
            color = TextMuted, fontSize = 11.sp,
        )
    }
}

@Composable
private fun QuestionBlock(q: QuestionSpec, draft: QuestionDraft, enabled: Boolean, onChange: (QuestionDraft) -> Unit) {
    Column {
        q.header?.takeIf { it.isNotBlank() }?.let {
            Text(it.uppercase(), color = QuestionAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(2.dp))
        }
        Text(q.question.ifBlank { "Choose an answer" }, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        if (q.allowsMultiple) Text("Choose one or more", color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(Space.xs))
        q.options.filter { it.label.isNotBlank() }.forEach { o ->
            val selected = o.label in draft.selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) SelectedOption else Transparent)
                    .clickable(enabled = enabled) { onChange(draft.toggle(q, o.label)) }
                    .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (q.allowsMultiple) Checkbox(
                    checked = selected, onCheckedChange = { onChange(draft.toggle(q, o.label)) }, enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = BambooGreen, uncheckedColor = TextSecondary, checkmarkColor = CodeBlockBackground),
                ) else RadioButton(
                    selected = selected, onClick = { onChange(draft.toggle(q, o.label)) }, enabled = enabled,
                    colors = RadioButtonDefaults.colors(selectedColor = BambooGreen, unselectedColor = TextSecondary),
                )
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(o.label, color = TextPrimary, fontSize = 14.sp)
                    o.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp) }
                }
            }
        }
        if (q.allowsCustom) {
            Spacer(Modifier.height(Space.xs))
            OutlinedTextField(
                value = draft.custom, onValueChange = { onChange(draft.typed(q, it.take(2000))) }, enabled = enabled,
                placeholder = { Text(if (q.options.isEmpty()) "Type your answer" else "Or type your own answer", fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth(), maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BambooBorderStrong, unfocusedBorderColor = BambooBorder),
            )
        }
    }
}

/** A finished or pending request as a timeline entry: what was asked and how it was answered. */
@Composable
internal fun RequestSummary(a: Approval) {
    if (a.isQuestion) {
        val qs = a.questions.orEmpty()
        Text("Question", color = QuestionAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        if (qs.isEmpty()) a.title?.takeIf { it.isNotBlank() }?.let { Text(it, color = TextPrimary, fontSize = 13.sp) }
        qs.forEachIndexed { i, q ->
            Text(q.question.ifBlank { q.header ?: "Question" }, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            a.answers?.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { Text("Answer: " + it.joinToString(", "), color = TextSecondary, fontSize = 12.sp) }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("Approval", color = StatusWarning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (a.permission.isNotBlank()) Text("  ·  ${a.permission}", color = TextSecondary, fontSize = 12.sp)
        }
        (a.title ?: a.patterns.joinToString("\n")).takeIf { it.isNotBlank() }?.let {
            Mono(it, color = TextPrimary, size = 11, maxLines = 4, modifier = Modifier.padding(top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(CodeBlockBackground).padding(horizontal = 8.dp, vertical = 5.dp))
        }
    }
}

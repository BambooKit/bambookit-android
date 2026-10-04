package com.bambookit.android.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.Todo
import com.bambookit.android.data.TodoReducer
import com.bambookit.android.data.TodoState
import com.bambookit.android.data.TodosView
import com.bambookit.android.data.todoState
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary

/** Open items of a todo list (shown as the tab count). */
internal fun openTodos(v: TodosView): Int = v.todos.count { val st = todoState(it.status); st == TodoState.Pending || st == TodoState.InProgress }

@Composable
internal fun TodoRow(t: Todo) {
    val st = todoState(t.status)
    val color = when (st) {
        TodoState.Done -> StatusSuccess
        TodoState.InProgress -> StatusRunning
        TodoState.Cancelled -> StatusFailed
        TodoState.Pending -> TextMuted
    }
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(st.mark, color = color, fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 22.dp))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            SelectionContainer {
                Text(
                    t.content, fontSize = 14.sp, lineHeight = 20.sp,
                    color = if (st == TodoState.Done || st == TodoState.Cancelled) TextSecondary else TextPrimary,
                    textDecoration = if (st == TodoState.Cancelled) TextDecoration.LineThrough else null,
                    fontWeight = if (st == TodoState.InProgress) FontWeight.Medium else FontWeight.Normal,
                )
            }
            val meta = listOfNotNull(st.label, t.priority?.takeIf { it.isNotBlank() }?.let { "$it priority" }).joinToString(" · ")
            Text(meta, color = TextMuted, fontSize = 11.sp)
        }
    }
}

/** "3 of 7 done · 1 in progress". */
internal fun todoSummary(todos: List<Todo>): String {
    val c = TodoReducer.counts(todos)
    return listOfNotNull(
        "${c.done} of ${c.total} done",
        c.inProgress.takeIf { it > 0 }?.let { "$it in progress" },
        c.cancelled.takeIf { it > 0 }?.let { "$it cancelled" },
    ).joinToString(" · ")
}

/** The todo list as a card (session Summary): live, with a clear state while it can't be read. */
@Composable
internal fun TodoSection(v: TodosView, pcTitle: String, onRetry: () -> Unit) {
    val err = v.error
    BkCard {
        when {
            v.todos.isNotEmpty() -> {
                Text(todoSummary(v.todos), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                v.todos.forEach { TodoRow(it) }
                if (err != null) Text("Showing the last list read. ${errorTitle(err, pcTitle, "Couldn't refresh it")}.", color = StatusWarning, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            err != null && !v.loaded -> Banner(
                errorMessage(err, pcTitle), if (err.desktopOutdated) Icons.Filled.SystemUpdate else Icons.Filled.CloudOff,
                color = StatusWarning, tint = StatusWarningTint, title = errorTitle(err, pcTitle, "Couldn't read the todo list"),
                actionLabel = "Retry", busy = v.loading, onAction = onRetry, diagnosis = err.diagnosis,
            )
            !v.loaded -> Text("Reading the todo list from $pcTitle…", color = TextMuted, fontSize = 13.sp)
            else -> Text("The agent has no todo list in this session.", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

/** Todo tab: the whole list, updated live. */
@Composable
internal fun TodoPane(v: TodosView, pcTitle: String, onRetry: () -> Unit) {
    val err = v.error
    when {
        v.todos.isEmpty() && err != null && !v.loaded -> ContentUnavailable(err, pcTitle, v.loading, onRetry)
        v.todos.isEmpty() && !v.loaded -> LoadingState("Reading the todo list from $pcTitle…")
        v.todos.isEmpty() -> EmptyState("No todos", "When the agent plans its work, its todo list appears here and updates live.", Icons.AutoMirrored.Filled.FormatListBulleted)
        else -> SessionRefresh(v.loading, onRetry) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.s)) {
                item { Text(todoSummary(v.todos), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = Space.s)) }
                if (err != null) item { StaleBanner(err, v.loading, onRetry) }
                items(v.todos) { TodoRow(it) }
                item { BottomSpacer() }
            }
        }
    }
}

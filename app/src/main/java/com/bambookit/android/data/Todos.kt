package com.bambookit.android.data

/** How a todo is drawn: ✓ done, ● in progress, ○ pending, ✕ cancelled. */
enum class TodoState(val mark: String, val label: String) {
    Done("✓", "Done"), InProgress("●", "In progress"), Pending("○", "Pending"), Cancelled("✕", "Cancelled"),
}

fun todoState(status: String): TodoState = when (status.lowercase().replace('-', '_')) {
    "completed", "complete", "done" -> TodoState.Done
    "in_progress", "inprogress", "active", "running" -> TodoState.InProgress
    "cancelled", "canceled", "skipped" -> TodoState.Cancelled
    else -> TodoState.Pending
}

/**
 * The open session's todo list. [version] counts live updates (session.todos): a /todos read that started
 * before a live update must not overwrite it with an older list.
 */
data class TodosView(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val todos: List<Todo> = emptyList(),
    val error: ContentError? = null,
    val version: Long = 0,
)

/** Pure state transitions of the todo list (unit tested). */
object TodoReducer {
    /** A read from the PC is starting. */
    fun loading(v: TodosView): TodosView = v.copy(loading = true)

    /**
     * A /todos read finished. [startedAt] is the [TodosView.version] when the read began; a live update that
     * arrived meanwhile wins, so the result is dropped.
     */
    fun fetched(v: TodosView, todos: List<Todo>, startedAt: Long): TodosView =
        if (v.version != startedAt) v.copy(loading = false)
        else v.copy(loading = false, loaded = true, todos = todos, error = null)

    fun failed(v: TodosView, error: ContentError): TodosView = v.copy(loading = false, error = error)

    /** A live session.todos event: always the complete current list. Events for other sessions are ignored. */
    fun live(v: TodosView, openSessionId: String, event: TodosEvent): TodosView =
        if (event.sessionId != openSessionId) v
        else v.copy(todos = event.todos, loaded = true, error = null, version = v.version + 1)

    data class Counts(val done: Int, val inProgress: Int, val pending: Int, val cancelled: Int) {
        val total get() = done + inProgress + pending + cancelled
    }

    fun counts(todos: List<Todo>): Counts {
        val by = todos.groupingBy { todoState(it.status) }.eachCount()
        return Counts(by[TodoState.Done] ?: 0, by[TodoState.InProgress] ?: 0, by[TodoState.Pending] ?: 0, by[TodoState.Cancelled] ?: 0)
    }
}

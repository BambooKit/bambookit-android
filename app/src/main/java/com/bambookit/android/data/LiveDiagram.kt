package com.bambookit.android.data

import java.time.Instant

/** Filters of the live diagram. */
enum class LiveFilter(val label: String) { All("All"), Agents("Agents"), Files("Files"), Tools("Tools"), Git("Git"), Tests("Tests"), Connections("Connections") }

/** Rows of the live diagram, top to bottom. */
enum class LiveTier(val label: String, val filters: Set<LiveFilter>) {
    Account("Account", setOf(LiveFilter.Connections)),
    Devices("Devices", setOf(LiveFilter.Connections)),
    Project("Project", setOf(LiveFilter.Connections, LiveFilter.Agents, LiveFilter.Files, LiveFilter.Git)),
    Session("Session", setOf(LiveFilter.Connections, LiveFilter.Agents)),
    Agent("Agent", setOf(LiveFilter.Agents)),
    Model("Model", setOf(LiveFilter.Agents)),
    Tools("Tools used", setOf(LiveFilter.Tools, LiveFilter.Agents)),
    Files("Files changed", setOf(LiveFilter.Files)),
    Git("Git", setOf(LiveFilter.Git)),
    Tests("Tests", setOf(LiveFilter.Tests)),
    Todos("Todos", setOf(LiveFilter.Agents)),
    Approvals("Approvals", setOf(LiveFilter.Agents, LiveFilter.Tools)),
}

enum class LiveStatus { Ok, Active, Warning, Failed, Muted }

/** One box. [id] is built from server ids / paths only, unique per diagram. */
data class LiveNode(
    val id: String,
    val tier: LiveTier,
    val label: String,
    val detail: String? = null,
    val status: LiveStatus = LiveStatus.Ok,
    /** For file nodes: the path to open. */
    val path: String? = null,
)

data class LiveEdge(val from: String, val to: String)

data class LiveGraph(val nodes: List<LiveNode>, val edges: List<LiveEdge>) {
    val tiers: List<Pair<LiveTier, List<LiveNode>>> get() = LiveTier.entries.mapNotNull { t -> nodes.filter { it.tier == t }.takeIf { it.isNotEmpty() }?.let { t to it } }
}

/** Everything the live diagram is built from: real state from the API, the PC and realtime events. */
data class LiveInput(
    val account: Account? = null,
    val devices: List<Device> = emptyList(),
    val myDeviceId: String? = null,
    /** The website is open (realtime presence.changed kind=web). Null = unknown. */
    val webOnline: Boolean? = null,
    val project: Project? = null,
    val session: Session? = null,
    val history: SessionHistory? = null,
    val todos: List<Todo> = emptyList(),
    val approvals: List<Approval> = emptyList(),
)

private fun parse(t: String?): Instant? = t?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** Moments of the session timeline the History slider can stop at (sorted, distinct). */
fun historyMoments(h: SessionHistory?): List<Instant> {
    if (h == null) return emptyList()
    val times = h.timeline.mapNotNull { parse(it.time) } + h.prompts.mapNotNull { parse(it.time) } +
        h.changes.flatMap { c -> c.edits.mapNotNull { parse(it.time) } } + h.tests.mapNotNull { parse(it.time) }
    return times.distinct().sorted()
}

/**
 * Builds the live diagram. With [until] (History mode) only what had happened by that moment is drawn:
 * timeline tools, file edits, tests and approvals after it are left out; live-only facts without a time
 * (devices online now, todos now) are left out as well.
 */
fun buildLiveGraph(input: LiveInput, filter: LiveFilter = LiveFilter.All, until: Instant? = null): LiveGraph {
    val nodes = LinkedHashMap<String, LiveNode>()
    val edges = LinkedHashSet<LiveEdge>()
    fun add(n: LiveNode) { nodes.putIfAbsent(n.id, n) }
    fun link(a: String?, b: String?) { if (a != null && b != null && a != b) edges += LiveEdge(a, b) }
    fun happened(t: String?): Boolean = until == null || (parse(t)?.let { !it.isAfter(until) } ?: false)
    val history = until != null
    val s = input.session
    val h = input.history

    val accountId = input.account?.id?.takeIf { it.isNotBlank() }?.let { "account:$it" }
    input.account?.let { a ->
        add(LiveNode(accountId ?: "account", LiveTier.Account, a.name ?: a.email ?: "Your account", a.email?.takeIf { a.name != null }))
    }
    val acc = accountId ?: input.account?.let { "account" }

    // Devices: this phone, the website, and the PC that runs the session (plus other PCs in Connections).
    val pc = s?.let { ss -> input.devices.firstOrNull { it.id == ss.deviceId } }
    if (!history) {
        input.devices.filter { it.revokedAt == null }.forEach { d ->
            val relevant = d.id == input.myDeviceId || d.id == pc?.id || filter == LiveFilter.Connections
            if (!relevant) return@forEach
            val label = when {
                d.id == input.myDeviceId -> "This phone"
                else -> d.name
            }
            val kind = if (d.kind == "desktop") "PC" else "Phone"
            add(
                LiveNode(
                    "device:${d.id}", LiveTier.Devices, label,
                    listOfNotNull(kind, if (d.online) "online" else "offline", d.appVersion?.let { "v$it" }).joinToString(" · "),
                    if (d.online) LiveStatus.Ok else LiveStatus.Muted,
                ),
            )
            link(acc, "device:${d.id}")
        }
        input.webOnline?.let { on ->
            add(LiveNode("device:web", LiveTier.Devices, "Website", if (on) "open now" else "not open", if (on) LiveStatus.Ok else LiveStatus.Muted))
            link(acc, "device:web")
        }
    } else pc?.let {
        add(LiveNode("device:${it.id}", LiveTier.Devices, it.name, "PC"))
        link(acc, "device:${it.id}")
    }

    val projectId = input.project?.id ?: s?.projectId
    val projectNode = projectId?.let { "project:$it" } ?: s?.let { "project:dir:${it.deviceId}:${it.directory}" }
    if (s != null || input.project != null) {
        add(
            LiveNode(
                projectNode!!, LiveTier.Project,
                input.project?.name ?: s?.projectName ?: h?.projectName ?: s?.directory?.substringAfterLast('/')?.substringAfterLast('\\') ?: "Project",
                input.project?.directory ?: s?.directory,
            ),
        )
        link(pc?.let { "device:${it.id}" }, projectNode)
    }

    val sessionNode = s?.let { "session:${it.id}" }
    if (s != null) {
        val status = when (s.status) {
            "busy", "retry" -> LiveStatus.Active
            "error" -> LiveStatus.Failed
            else -> LiveStatus.Ok
        }
        add(LiveNode(sessionNode!!, LiveTier.Session, s.title, if (history) null else s.currentAction ?: s.status, if (history) LiveStatus.Ok else status))
        link(projectNode, sessionNode)
    }

    val agent = s?.agent ?: h?.agent
    val agentNode = agent?.let { "agent:${s?.id ?: "session"}:$it" }
    if (agent != null) {
        add(LiveNode(agentNode!!, LiveTier.Agent, agent.replaceFirstChar { it.uppercase() }, "agent"))
        link(sessionNode, agentNode)
    }
    val model = s?.model ?: h?.model
    val modelNode = model?.let { "model:$it" }
    if (model != null) {
        add(LiveNode(modelNode!!, LiveTier.Model, model.substringAfter('/'), model.substringBefore('/', "").ifBlank { null }?.let { "provider $it" }))
        link(agentNode ?: sessionNode, modelNode)
    }
    val worker = agentNode ?: sessionNode

    // Tools used: timeline kinds and the tools that edited files, counted.
    if (h != null) {
        val toolCounts = LinkedHashMap<String, Int>()
        val failedTools = HashSet<String>()
        h.timeline.filter { happened(it.time) && it.kind !in setOf("prompt", "response", "completed") }.forEach { e ->
            toolCounts[e.kind] = (toolCounts[e.kind] ?: 0) + 1
            if (e.status == "error" || e.kind == "error") failedTools += e.kind
        }
        toolCounts.forEach { (kind, n) ->
            add(LiveNode("tool:$kind", LiveTier.Tools, kind, "$n×", if (kind in failedTools) LiveStatus.Warning else LiveStatus.Ok))
            link(worker, "tool:$kind")
        }

        h.changes.forEach { c ->
            val edits = c.edits.filter { happened(it.time) }
            if (history && edits.isEmpty()) return@forEach
            // Exact path (only separators normalized): two files differing in case stay two nodes.
            val id = "file:" + c.file.replace('\\', '/')
            val adds = if (history) edits.sumOf { it.additions } else c.additions
            val dels = if (history) edits.sumOf { it.deletions } else c.deletions
            add(
                LiveNode(
                    id, LiveTier.Files, c.file.replace('\\', '/').substringAfterLast('/'), "${c.status} +$adds −$dels",
                    when (c.status) { "deleted" -> LiveStatus.Failed; "added" -> LiveStatus.Ok; else -> LiveStatus.Active }, path = c.file,
                ),
            )
            val tools = edits.mapNotNull { it.tool }.distinct()
            if (tools.isEmpty()) link(worker, id)
            tools.forEach { t ->
                val toolId = if (nodes.containsKey("tool:$t")) "tool:$t" else (toolCounts.keys.firstOrNull { k -> t.contains(k, true) || k.contains(t, true) }?.let { "tool:$it" })
                link(toolId ?: worker, id)
            }
        }

        h.branch?.let { b ->
            add(LiveNode("git:branch:$b", LiveTier.Git, b, "branch"))
            link(projectNode, "git:branch:$b")
            h.baseCommit?.let { c ->
                add(LiveNode("git:commit:$c", LiveTier.Git, c.take(8), "base commit"))
                link("git:branch:$b", "git:commit:$c")
            }
        }

        h.tests.forEachIndexed { i, t ->
            if (!happened(t.time)) return@forEachIndexed
            val id = "test:${t.time ?: ""}:$i"
            add(
                LiveNode(
                    id, LiveTier.Tests, t.command.ifBlank { "test" }, t.status + (t.exitCode?.let { " · exit $it" } ?: ""),
                    when (t.status) { "passed" -> LiveStatus.Ok; "failed" -> LiveStatus.Failed; "running" -> LiveStatus.Active; else -> LiveStatus.Muted },
                ),
            )
            link(nodes.keys.firstOrNull { it == "tool:test" } ?: worker, id)
        }
    }

    if (!history) input.todos.forEach { t ->
        val st = todoState(t.status)
        add(
            LiveNode(
                "todo:${t.id.ifBlank { t.content.hashCode().toString() }}", LiveTier.Todos, "${st.mark} ${t.content}", st.label,
                when (st) { TodoState.Done -> LiveStatus.Ok; TodoState.InProgress -> LiveStatus.Active; TodoState.Cancelled -> LiveStatus.Muted; TodoState.Pending -> LiveStatus.Muted },
            ),
        )
        link(worker, "todo:${t.id.ifBlank { t.content.hashCode().toString() }}")
    }

    input.approvals.filter { happened(it.createdAt) }.forEach { a ->
        val pending = a.isPending && !history
        add(
            LiveNode(
                "approval:${a.id}", LiveTier.Approvals, if (a.isQuestion) "Question" else a.permission.ifBlank { "Permission" },
                if (pending) "waiting for you" else a.reply ?: a.status.lowercase(),
                if (pending) LiveStatus.Warning else if (a.status == "REJECTED") LiveStatus.Failed else LiveStatus.Ok,
            ),
        )
        link(worker, "approval:${a.id}")
    }

    val keep = nodes.values.filter { filter == LiveFilter.All || filter in it.tier.filters }
    val ids = keep.map { it.id }.toSet()
    return LiveGraph(keep, edges.filter { it.from in ids && it.to in ids })
}

package com.bambookit.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class Envelope<T>(val data: T)

/** GET /v1/me. Every field is optional so older or newer API versions still decode. */
@Serializable
data class Account(
    val id: String = "",
    val email: String? = null,
    /** Display name: the BambooKit nickname when set, else the sign-in provider's name. */
    val name: String? = null,
    /** The BambooKit nickname (PATCH /v1/me); null when none was chosen. */
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val avatarStored: Boolean = false,
    /** "email", "google" or "other". */
    val provider: String? = null,
    val emailVerified: Boolean? = null,
    val createdAt: String? = null,
    val lastActiveAt: String? = null,
    val devices: Int? = null,
    val projects: Int? = null,
    val cloudStorage: Boolean = false,
    val accountDeletion: Boolean = false,
)

/** POST /v1/me/avatar-upload: a short-lived signed PUT URL for a new profile photo. */
@Serializable
data class AvatarUpload(
    val key: String,
    val url: String,
    val method: String = "PUT",
    val headers: Map<String, String> = emptyMap(),
    val expiresIn: Int? = null,
)

@Serializable data class AvatarSet(val avatarUrl: String? = null, val profile: Account? = null)

@Serializable data class LinkedDevice(val id: String, val name: String, val kind: String, val platform: String)

@Serializable data class ActiveSessionRef(val id: String, val title: String, val status: String, val projectName: String? = null)

@Serializable
data class Device(
    val id: String,
    val kind: String,
    val name: String,
    val platform: String,
    val appVersion: String? = null,
    val online: Boolean = false,
    val lastSeenAt: String? = null,
    val createdAt: String? = null,
    val revokedAt: String? = null,
    val linkedDevices: List<LinkedDevice> = emptyList(),
    val activeSession: ActiveSessionRef? = null,
    /** Desktops only: the PC's RSA public key (SPKI PEM) that provider API keys are encrypted to. Null on older desktops. */
    val encryptionKey: String? = null,
    /** Desktops only (API 1.1.0+): desktop protocol and the capabilities the PC has. Null from older servers. */
    val protocol: Int? = null,
    val capabilities: List<String>? = null,
    /** Desktops only (API 1.3.0+): the PC's live settings (approval mode, keep-awake). Null from older servers. */
    val settings: DeviceSettings? = null,
)

/**
 * A PC's live settings, reported wherever it reports appVersion/capabilities and kept current with
 * the ephemeral device.updated event (API 1.3.0). [approvalMode]: 'ask', 'edits' (Auto mode) or 'all'
 * (Auto-approve). [keepAwake]: the PC's ☕ keep-awake is on.
 */
@Serializable
data class DeviceSettings(
    val approvalMode: String = "ask",
    val keepAwake: Boolean = false,
)

/** The ephemeral device.updated event payload: a PC's settings changed. */
@Serializable data class DeviceUpdated(val deviceId: String = "", val settings: DeviceSettings? = null)

/** The ephemeral terminal.data event payload (remote terminal, API 1.3.0 / desktop `remote-terminal`). */
@Serializable data class TerminalData(val termId: String = "", val data: String = "")

@Serializable
data class Project(
    val id: String,
    val deviceId: String,
    val deviceName: String? = null,
    val name: String,
    val directory: String,
    val branch: String? = null,
    val activeSessions: Int = 0,
    val totalSessions: Int = 0,
    val updatedAt: String? = null,
    /** active, completed or archived (API 1.1.0+). */
    val status: String? = null,
)

@Serializable data class ChangeSummary(val additions: Int = 0, val deletions: Int = 0, val files: Int = 0)

@Serializable
data class Session(
    val id: String,
    val deviceId: String,
    val projectId: String? = null,
    val projectName: String? = null,
    val directory: String,
    val title: String,
    val status: String,
    val statusMessage: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val currentAction: String? = null,
    val changes: ChangeSummary = ChangeSummary(),
    val pendingApprovals: Int = 0,
    /** True once the session was continued on the PC ("Continue on PC"); only then may the phone chat in it. */
    val remote: Boolean = false,
    /** Liked by the user (kept by BambooKit only, never sent to the PC). */
    val starred: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val isActive get() = status == "busy" || status == "retry"
}

@Serializable
data class Part(
    val id: String,
    val sessionId: String,
    val messageId: String,
    val role: String,
    val type: String,
    val text: String? = null,
    val tool: String? = null,
    val toolStatus: String? = null,
    val toolTitle: String? = null,
    val sortKey: String,
    /**
     * Complete tool details from newer desktops (any of them may be missing on older ones): the tool's input,
     * its output, the error, a unified diff, the exit code, extra metadata and start/end times.
     */
    val input: JsonElement? = null,
    val output: JsonElement? = null,
    val error: JsonElement? = null,
    val diff: String? = null,
    val exitCode: Int? = null,
    val metadata: JsonElement? = null,
    val time: JsonElement? = null,
    val truncated: Boolean = false,
    val updatedAt: String? = null,
)

@Serializable data class ChangedFile(val file: String, val status: String? = null, val additions: Int = 0, val deletions: Int = 0)

@Serializable
data class Approval(
    val id: String,
    val deviceId: String = "",
    val sessionId: String = "",
    val sessionTitle: String? = null,
    val projectName: String? = null,
    val permission: String = "",
    val title: String? = null,
    val patterns: List<String> = emptyList(),
    val status: String = "",
    val reply: String? = null,
    /** "permission" (run a command, edit files…) or "question" (the agent asks you to choose or type an answer). */
    val kind: String = "permission",
    /** Question requests only: what the agent asks. */
    val questions: List<QuestionSpec>? = null,
    /** Question requests only, once answered: one list of chosen labels (or typed text) per question. */
    val answers: List<List<String>>? = null,
    val createdAt: String? = null,
    val resolvedAt: String? = null,
    /** Who resolved a non-pending request: 'phone', 'web', 'pc', 'auto', or null (API 1.3.0). */
    val resolvedBy: String? = null,
) {
    val isPending get() = status == "PENDING" || status == "RESPONDING"
    val isQuestion get() = kind == "question"
    /** A resolved request the PC approved automatically (Auto mode / Auto-approve). */
    val isAuto get() = resolvedBy == "auto"
}

/** One question of a question request. [multiple]: several options may be chosen. [custom]: typed answers allowed unless false. */
@Serializable
data class QuestionSpec(
    val header: String? = null,
    val question: String = "",
    val options: List<QuestionOption> = emptyList(),
    val multiple: Boolean? = null,
    val custom: Boolean? = null,
) {
    val allowsMultiple get() = multiple == true
    val allowsCustom get() = custom != false
}

@Serializable data class QuestionOption(val label: String = "", val description: String? = null)

@Serializable
data class Command(
    val id: String,
    val deviceId: String,
    val sessionId: String? = null,
    val type: String,
    val status: String,
    val result: JsonElement? = null,
    val error: String? = null,
    val createdAt: String? = null,
)

@Serializable data class CommandAccepted(val data: Command, val deviceOnline: Boolean = false)

@Serializable
data class Overview(
    val desktops: List<Device> = emptyList(),
    val mobiles: List<Device> = emptyList(),
    val activeSessions: List<Session> = emptyList(),
    val pendingApprovals: Int = 0,
    val recentChangedFiles: Int = 0,
    val unreadNotifications: Int = 0,
)

@Serializable
data class NotificationItem(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val readAt: String? = null,
    val createdAt: String,
)

/** DELETE /v1/activity and the 'activity.cleared' event: activity up to this event sequence is hidden. */
@Serializable
data class ActivityCleared(val clearedThroughSeq: Long = 0, val notificationsRemoved: Int = 0)

@Serializable
data class RealtimeEvent(
    val seq: Long,
    val id: String,
    val timestamp: String,
    val deviceId: String? = null,
    val projectId: String? = null,
    val sessionId: String? = null,
    val type: String,
    val payload: JsonElement,
)

@Serializable data class DiffFile(val file: String, val status: String? = null, val additions: Int = 0, val deletions: Int = 0, val patch: String = "", val truncated: Boolean = false)
@Serializable data class DiffResult(val files: List<DiffFile> = emptyList())

@Serializable data class PairingResult(val desktop: Device, val mobile: Device)

/**
 * A file the session read, created, edited or deleted (GET /v1/sessions/:id/filemap, live from the PC).
 * [path] is relative to the session's project folder with "/" separators; turn 0 = not from a tool call.
 */
@Serializable
data class FileMapEntry(
    val path: String,
    val actions: List<String> = emptyList(),
    val firstTurn: Int = 0,
    val lastTurn: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val failed: Boolean = false,
) {
    val changed get() = actions.any { it == "created" || it == "edited" || it == "deleted" || it == "renamed" }
}

/** One entry of a project folder (GET /v1/sessions/:id/tree). Folders come first, sorted. */
@Serializable
data class TreeEntry(val name: String, val path: String, val type: String, val size: Long? = null) {
    val isDirectory get() = type == "directory"
}

@Serializable data class TreeListing(val path: String = "", val entries: List<TreeEntry> = emptyList(), val truncated: Boolean = false)

/** A project file's text (GET /v1/sessions/:id/file), read live from the PC. View only. */
@Serializable data class FileContent(val path: String, val content: String = "", val size: Long = 0)

/** A component of the project diagram: a folder (group of files) or a single file. x/y are its top-left. */
@Serializable
data class DiagramNode(
    val id: String,
    val label: String,
    val where: String = "",
    val folder: Boolean = false,
    val files: List<String> = emptyList(),
    val x: Double,
    val y: Double,
)

/** [count] real references (imports, script/link/CSS/Python references) from one component to another. */
@Serializable data class DiagramEdge(val from: String, val to: String, val count: Int = 1)

/** The session's project drawn as components (GET /v1/sessions/:id/diagram); the layout is computed on the PC. */
@Serializable
data class ProjectDiagram(
    val nodes: List<DiagramNode> = emptyList(),
    val edges: List<DiagramEdge> = emptyList(),
    val width: Double = 0.0,
    val height: Double = 0.0,
    val nodeWidth: Double = 190.0,
    val nodeHeight: Double = 58.0,
    val files: Int = 0,
    val truncated: Boolean = false,
)

// ================================================================== session history (GET /v1/sessions/:id/history)

/**
 * The session's history as recorded on the PC: prompts, timeline, changed files with per-edit patches,
 * tests and a summary. [source] is "pc" (read live) or "cloud" (the PC's saved copy, kept 7 days).
 * Everything is optional: missing data stays null/empty and is never invented on the phone.
 */
@Serializable
data class HistoryResponse(
    val source: String? = null,
    val savedAt: String? = null,
    val history: SessionHistory = SessionHistory(),
    val approvals: List<Approval> = emptyList(),
) {
    val live get() = source == "pc"
}

@Serializable
data class SessionHistory(
    val sessionId: String? = null,
    val opencodeSessionId: String? = null,
    val title: String? = null,
    val projectName: String? = null,
    val directory: String? = null,
    val branch: String? = null,
    val baseCommit: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val durationMs: Long? = null,
    val prompts: List<HistoryPrompt> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    val changes: List<FileChange> = emptyList(),
    val tests: List<TestRun> = emptyList(),
    val summary: HistorySummary? = null,
)

@Serializable data class HistoryPrompt(val messageId: String = "", val time: String? = null, val text: String = "")

/** kind: prompt, response, read, edit, write, patch, command, test, search, web, agent, plan, tool, error, completed. */
@Serializable
data class TimelineEntry(
    val id: String = "",
    val time: String? = null,
    val kind: String = "tool",
    val title: String = "",
    val detail: String? = null,
    val file: String? = null,
    val status: String? = null,
    val messageId: String? = null,
)

/** One file the session changed. [status]: added, modified, deleted or renamed (from [oldPath]). */
@Serializable
data class FileChange(
    val file: String = "",
    val status: String = "modified",
    val oldPath: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val edits: List<FileEdit> = emptyList(),
)

/** One tool call's change to a file, as a unified diff. */
@Serializable
data class FileEdit(val time: String? = null, val tool: String? = null, val additions: Int = 0, val deletions: Int = 0, val patch: String = "")

/** status: passed, failed, running or unknown. */
@Serializable data class TestRun(val time: String? = null, val command: String = "", val status: String = "unknown", val exitCode: Int? = null)

@Serializable
data class HistorySummary(
    val prompts: Int? = null,
    val filesChanged: Int? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
    val testsPassed: Int? = null,
    val testsFailed: Int? = null,
    val durationMs: Long? = null,
)

/**
 * GET /v1/sessions/:id/file-versions — one file before and after the session, read live from the PC.
 * [beforeSource]: "session" (reconstructed from the session's own edits), "git" (last commit) or null.
 */
@Serializable
data class FileVersions(
    val path: String = "",
    val status: String = "unchanged",
    val before: String? = null,
    val after: String? = null,
    val beforeSource: String? = null,
    val note: String? = null,
    val truncated: Boolean = false,
)


// ================================================================== todos (GET /v1/sessions/:id/todos, realtime session.todos)

/** One item of the agent's todo list. [status]: completed, in_progress, pending or cancelled. */
@Serializable
data class Todo(val id: String = "", val content: String = "", val status: String = "pending", val priority: String? = null)

@Serializable data class TodoList(val todos: List<Todo> = emptyList())

@Serializable data class TodosEvent(val sessionId: String = "", val todos: List<Todo> = emptyList())

// ================================================================== AI providers (GET /v1/devices/:id/providers)

@Serializable data class ProviderModel(val id: String, val name: String? = null)

/** A provider the PC knows. [configured]: it has a key (or needs none). Keys are never sent to the phone. */
@Serializable
data class AiProvider(
    val id: String,
    val name: String? = null,
    val configured: Boolean = false,
    val source: String? = null,
    val models: List<ProviderModel> = emptyList(),
)

/** A model choice as the engine names it (SEND_MESSAGE / new session payload). */
@Serializable data class ModelRef(val providerID: String, val modelID: String)

@Serializable data class ConnectableProvider(val id: String, val name: String? = null)

@Serializable
data class ProvidersInfo(
    val providers: List<AiProvider> = emptyList(),
    val default: ModelRef? = null,
    val connectable: List<ConnectableProvider> = emptyList(),
)

// ================================================================== request details (GET /v1/approvals/:id/detail)

/**
 * Everything the agent attached to a request, read live from the PC: for permissions the patterns and
 * metadata (full command, file path, proposed diff); for questions the full questions and their context.
 */
@Serializable
data class ApprovalDetail(
    val kind: String? = null,
    val permission: String? = null,
    val title: String? = null,
    val patterns: List<String> = emptyList(),
    val metadata: JsonElement? = null,
    val questions: List<QuestionSpec>? = null,
    val context: JsonElement? = null,
    val truncated: Boolean = false,
)

// ---------------------------------------------------------------- profile statistics (GET /v1/me/stats)

@Serializable
data class ProjectStat(
    val id: String,
    val name: String = "",
    val status: String = "active",
    val branch: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastActivityAt: String? = null,
    val sessions: Int = 0,
    val tasks: Int = 0,
    val filesChanged: Int = 0,
    val codingMs: Long = 0,
)

@Serializable
data class ProjectStats(
    val total: Int = 0,
    val active: Int = 0,
    val completed: Int = 0,
    val archived: Int = 0,
    val list: List<ProjectStat> = emptyList(),
)

@Serializable data class SessionCounts(val total: Int = 0, val withCompletedWork: Int = 0)
@Serializable data class TaskCounts(val total: Int = 0, val completed: Int = 0, val failed: Int = 0, val debugging: Int = 0)

@Serializable
data class CodingTime(
    val totalMs: Long = 0,
    val thisWeekMs: Long = 0,
    val thisMonthMs: Long = 0,
    val nightMs: Long = 0,
    val longestMs: Long = 0,
)

@Serializable
data class CodeStats(
    val filesCreated: Long = 0,
    val filesModified: Long = 0,
    val filesDeleted: Long = 0,
    val filesRenamed: Long = 0,
    val linesAdded: Long = 0,
    val linesDeleted: Long = 0,
    val edits: Long = 0,
    val testsRun: Long = 0,
    val testsPassed: Long = 0,
    val testsFailed: Long = 0,
    val commits: Long = 0,
    val deployments: Long = 0,
)

/** One tier of a tiered achievement: bronze, silver, gold, platinum or diamond. */
@Serializable
data class AchievementTier(
    val name: String = "",
    val threshold: Double = 0.0,
    val unlocked: Boolean = false,
    val unlockedAt: String? = null,
)

/**
 * An achievement. Older servers send only title, description, progress, target, unit ("count" or "ms"), unlocked
 * and unlockedAt; tiered ones (API with 50 achievements) add emoji, value, tiers, tier (current), nextTier,
 * trackable and, for untrackable ones, the reason. Everything has a default so either shape decodes.
 */
@Serializable
data class Achievement(
    val id: String,
    val title: String = "",
    val description: String = "",
    val progress: Double = 0.0,
    val target: Double = 1.0,
    /** "count" or "ms" (older servers); "lines", "hours", "days", "nights", "files", … for tiered achievements. */
    val unit: String = "count",
    val unlocked: Boolean = false,
    val unlockedAt: String? = null,
    val emoji: String? = null,
    val trackable: Boolean = true,
    /** Why an achievement can't be tracked yet (trackable = false). */
    val reason: String? = null,
    val value: Double? = null,
    val tiers: List<AchievementTier> = emptyList(),
    /** The highest tier reached, or null. */
    val tier: String? = null,
    /** The next tier to reach, or null once all are reached. */
    val nextTier: String? = null,
)

/** GET /v1/me/achievements `summary` and /v1/me/stats `achievementSummary`. */
@Serializable
data class AchievementSummary(
    val unlocked: Int = 0,
    val total: Int = 0,
    val tiersUnlocked: Int = 0,
    val tiersTotal: Int = 0,
    val points: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
)

@Serializable
data class ProfileStats(
    val timeZone: String? = null,
    val memberSince: String? = null,
    /** Distinct files changed in sessions with activity in the last 24 h (API 1.3.0, GET /v1/me/stats). */
    val filesChanged24h: Int = 0,
    val projects: ProjectStats = ProjectStats(),
    val sessions: SessionCounts = SessionCounts(),
    val tasks: TaskCounts = TaskCounts(),
    val codingTime: CodingTime = CodingTime(),
    val code: CodeStats = CodeStats(),
    /** Plain-text rules for how coding time, files and night hours are counted. */
    val rules: Map<String, String> = emptyMap(),
    val achievements: List<Achievement> = emptyList(),
    /** Tiered achievements' totals (newer servers only). */
    val achievementSummary: AchievementSummary? = null,
)

@Serializable data class ProjectStatusResult(val id: String, val status: String)

/** The achievement.unlocked event: one tier ([tier]) or, when many unlock at once, a summary (id "summary", [count]). */
@Serializable data class AchievementEvent(
    val id: String = "",
    val title: String = "",
    val unlockedAt: String? = null,
    val tier: String? = null,
    val count: Int? = null,
)

/** GET /v1/meta (public). */
@Serializable data class ApiMeta(val apiVersion: String? = null, val protocol: Int? = null)

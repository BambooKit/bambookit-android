package com.bambookit.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class Envelope<T>(val data: T)

@Serializable
data class Account(val id: String, val email: String? = null, val name: String? = null, val avatarUrl: String? = null)

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
)

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
    /** True once the session was continued on the PC; only then may the phone chat in it. */
    val remote: Boolean = false,
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
)

@Serializable data class ChangedFile(val file: String, val status: String? = null, val additions: Int = 0, val deletions: Int = 0)

@Serializable
data class Approval(
    val id: String,
    val deviceId: String,
    val sessionId: String,
    val sessionTitle: String? = null,
    val projectName: String? = null,
    val permission: String,
    val title: String? = null,
    val patterns: List<String> = emptyList(),
    val status: String,
    val reply: String? = null,
    val createdAt: String? = null,
) {
    val isPending get() = status == "PENDING" || status == "RESPONDING"
}

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
    val changed get() = actions.any { it == "created" || it == "edited" || it == "deleted" }
}

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

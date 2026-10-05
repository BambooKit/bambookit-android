package com.bambookit.android.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bambookit.android.MainActivity
import com.bambookit.android.R

/**
 * Decides whether a realtime 'notification' event should become a phone notification.
 *
 * - Each notification id is shown at most once (the stream can deliver an event again after a reconnect).
 * - Events replayed on (re)connect, i.e. published before this connection started, are not shown: they are
 *   stored events whose sequence number is at or below the server's sequence when the stream became ready.
 *
 * Pure Kotlin so it can be unit tested.
 */
class NotificationGate(private val capacity: Int = 500) {
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > capacity
    }

    /**
     * [eventSeq]: the realtime event's sequence number (-1 for live-only events).
     * [connectionStartSeq]: the server's sequence when the current connection became ready, or null if unknown.
     */
    @Synchronized
    fun shouldNotify(id: String, eventSeq: Long, connectionStartSeq: Long?): Boolean {
        if (id.isBlank() || seen.containsKey(id)) return false
        seen[id] = true
        val replayed = eventSeq >= 0 && connectionStartSeq != null && eventSeq <= connectionStartSeq
        return !replayed
    }

    @Synchronized
    fun clear() = seen.clear()
}

/**
 * Recent activity was cleared through a server event sequence (DELETE /v1/activity, or 'activity.cleared' from
 * another device). Stored events at or below that sequence, e.g. replayed after a reconnect, must not bring
 * entries back; live-only events (seq -1) and newer events are kept.
 *
 * Pure Kotlin so it can be unit tested.
 */
class ActivityClearFilter {
    @Volatile
    var clearedThroughSeq: Long = -1
        private set

    /** Records a clear; the sequence only moves forward. */
    @Synchronized
    fun cleared(throughSeq: Long) {
        if (throughSeq > clearedThroughSeq) clearedThroughSeq = throughSeq
    }

    fun accepts(eventSeq: Long): Boolean = eventSeq < 0 || eventSeq > clearedThroughSeq

    @Synchronized
    fun reset() {
        clearedThroughSeq = -1
    }
}

/**
 * The posted notifications (id, channel) to remove when recent activity is cleared: everything on the BambooKit
 * request and session channels, never the ongoing "connected" notification of the background connection.
 */
fun postedNotificationsToCancel(posted: List<Pair<Int, String?>>): List<Int> = posted
    .filter { (id, channel) -> id != BambooNotifier.CONNECTION_NOTIFICATION_ID && channel in BambooNotifier.ACTIVITY_CHANNELS }
    .map { it.first }

/** The product is BambooKit everywhere, including notification text that comes from the engine. */
fun brandText(text: String): String = text.replace(Regex("(?i)opencode"), "BambooKit")

/** Kind of phone notification, which picks its channel. */
enum class NotifyKind { Request, SessionUpdate }

fun notifyKindOf(type: String): NotifyKind = when (type) {
    "approval.required", "question.asked" -> NotifyKind.Request
    else -> NotifyKind.SessionUpdate
}

/**
 * Notification channels and posting. Channel sounds can't change after a channel is created, so the
 * channels that play the BambooKit sound use new ids ("_v2"); the old "Agent activity" channel is removed.
 */
class BambooNotifier(private val context: Context) {
    val soundUri: Uri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.bambookit_notify}")

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REQUESTS, "Requests", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "The agent asks for approval or asks you a question"
                setSound(soundUri, audio)
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSIONS, "Session updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A session finished or stopped with an error"
                setSound(soundUri, audio)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CONNECTION, "Background connection", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while BambooKit stays connected in the background"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        // Replaced by the channels above (its sound could not be changed).
        runCatching { nm.deleteNotificationChannel(LEGACY_CHANNEL) }
    }

    fun canPost(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Posts a BambooKit notification. Its data carries only ids and short text. */
    fun post(n: NotificationItem) {
        if (!canPost()) return
        val kind = notifyKindOf(n.type)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "com.bambookit.android.OPEN_${n.id}"
            putExtra(EXTRA_NOTIFICATION_ID, n.id)
            n.data["sessionId"]?.let { putExtra(EXTRA_SESSION_ID, it) }
            n.data["approvalId"]?.let { putExtra(EXTRA_APPROVAL_ID, it) }
        }
        val pending = PendingIntent.getActivity(context, n.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, if (kind == NotifyKind.Request) CHANNEL_REQUESTS else CHANNEL_SESSIONS)
            .setSmallIcon(R.drawable.bambookit_mark)
            .setContentTitle(brand(StatsFormat.notificationTitle(n.type, n.title, n.data)))
            .setContentText(brand(n.body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(brand(n.body)))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(if (kind == NotifyKind.Request) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (kind == NotifyKind.Request) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            // Pre-Android 8 (no channels): the sound is set per notification.
            .setSound(soundUri)
        runCatching { NotificationManagerCompat.from(context).notify(n.id.hashCode(), builder.build()) }
    }

    /**
     * Removes the BambooKit request and session notifications this app has posted (recent activity was cleared).
     * The ongoing background-connection notification stays.
     */
    fun cancelActivityNotifications() {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            val active = nm.activeNotifications
            val ids = postedNotificationsToCancel(active.map { it.id to it.notification.channelId }).toSet()
            active.filter { it.id in ids }.forEach { nm.cancel(it.tag, it.id) }
        }
    }

    /** The product is BambooKit everywhere, including text that comes from the engine. */
    private fun brand(text: String) = brandText(text)

    /** The silent ongoing notification of the background connection service. */
    fun connectionNotification(connected: Boolean): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.bambookit_mark)
            .setContentTitle(if (connected) "BambooKit is connected" else "BambooKit is reconnecting…")
            .setContentText("You'll be notified when the agent asks something or a session finishes.")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val CHANNEL_REQUESTS = "bk_requests_v2"
        const val CHANNEL_SESSIONS = "bk_sessions_v2"
        const val CHANNEL_CONNECTION = "bk_connection"
        private const val LEGACY_CHANNEL = "bambookit_agents"
        /** Channels whose notifications belong to recent activity (removed when it is cleared). */
        val ACTIVITY_CHANNELS = setOf(CHANNEL_REQUESTS, CHANNEL_SESSIONS, LEGACY_CHANNEL)
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_APPROVAL_ID = "approvalId"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val CONNECTION_NOTIFICATION_ID = 7001
    }
}

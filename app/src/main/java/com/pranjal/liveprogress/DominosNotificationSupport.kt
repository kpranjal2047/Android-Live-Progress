package com.pranjal.liveprogress

import android.app.Notification
import android.content.Context
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil

sealed interface DominosExtractionResult {
    data class Extracted(val data: DominosNotificationData) : DominosExtractionResult
    data object NotDominosLiveNotification : DominosExtractionResult
    data class UnreadableDominosLiveNotification(val reason: String) : DominosExtractionResult
}

data class DominosNotificationData(
    val title: CharSequence,
    val text: CharSequence?,
    val progress: ProgressInfo,
    val deliveryStatus: DominosDeliveryStatus,
    val countdownDeadlineElapsedRealtime: Long?
)

enum class DominosDeliveryStatus {
    NONE,
    REACHED,
    DELIVERED
}

enum class DominosMirrorRoute {
    CUSTOM_PROGRESS,
    NATIVE_PROGRESS,
    ADDITIONAL,
    NONE
}

object DominosNotificationRouting {
    fun decide(
        progressEnabled: Boolean,
        recognizedCustomLayout: Boolean,
        hasCustomCandidate: Boolean,
        hasNativeProgress: Boolean,
        additionalEnabled: Boolean
    ): DominosMirrorRoute {
        if (progressEnabled && hasCustomCandidate) return DominosMirrorRoute.CUSTOM_PROGRESS
        if (progressEnabled && !recognizedCustomLayout && hasNativeProgress) {
            return DominosMirrorRoute.NATIVE_PROGRESS
        }
        return if (additionalEnabled) DominosMirrorRoute.ADDITIONAL else DominosMirrorRoute.NONE
    }
}

object DominosNotificationSupport {
    const val PACKAGE_NAME = "com.Dominos"

    private const val LIVE_NOTIFICATION_LAYOUT = "live_notification_layout"
    private const val TITLE = "notification_title"
    private const val MESSAGE = "notification_message"
    private const val PROGRESS_BAR = "notification_progressBar"

    fun isDominos(sbn: StatusBarNotification): Boolean = sbn.packageName == PACKAGE_NAME

    fun extract(context: Context, sbn: StatusBarNotification): DominosExtractionResult {
        if (!isDominos(sbn)) return DominosExtractionResult.NotDominosLiveNotification
        val notification = sbn.notification ?: return DominosExtractionResult.NotDominosLiveNotification
        val dominosContext = try {
            context.createPackageContext(PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY)
        } catch (_: Exception) {
            return DominosExtractionResult.UnreadableDominosLiveNotification("package context unavailable")
        }
        val layoutId = dominosContext.resources.getIdentifier(
            LIVE_NOTIFICATION_LAYOUT,
            "layout",
            PACKAGE_NAME
        )
        if (layoutId == 0) {
            return DominosExtractionResult.UnreadableDominosLiveNotification("layout unavailable")
        }
        val remoteViews = sourceRemoteViews(notification)
            .firstOrNull { it.layoutId == layoutId }
            ?: return DominosExtractionResult.NotDominosLiveNotification

        return try {
            val view = remoteViews.apply(dominosContext, null)
                ?: return DominosExtractionResult.UnreadableDominosLiveNotification("remote view unavailable")
            val title = view.textFor(dominosContext, TITLE)
                ?: return DominosExtractionResult.UnreadableDominosLiveNotification("title unavailable")
            val text = view.textFor(dominosContext, MESSAGE)
            val notificationText = listOfNotNull(title, text).joinToString(" ")
            val deliveryStatus = DominosDeliveryTime.deliveryStatus(notificationText)
            val deliveryComplete = deliveryStatus != DominosDeliveryStatus.NONE
            val sourceProgress = view.viewFor<ProgressBar>(dominosContext, PROGRESS_BAR)
                ?.takeIf { !it.isIndeterminate && it.max > 0 }
            if (sourceProgress == null && !deliveryComplete) {
                return DominosExtractionResult.UnreadableDominosLiveNotification("determinate progress unavailable")
            }
            val nowElapsedRealtime = SystemClock.elapsedRealtime()
            val deadline = if (deliveryComplete) {
                null
            } else {
                DominosDeliveryTime.parseDeadline(
                    text = notificationText,
                    nowWallTimeMillis = System.currentTimeMillis(),
                    nowElapsedRealtime = nowElapsedRealtime
                )
            }
            DominosExtractionResult.Extracted(
                DominosNotificationData(
                    title = title,
                    text = text,
                    progress = sourceProgress?.let { progressBar ->
                        ProgressInfo(
                            progress = progressBar.progress.coerceIn(0, progressBar.max),
                            max = progressBar.max,
                            indeterminate = false
                        )
                    } ?: ProgressInfo(progress = 1, max = 1, indeterminate = false),
                    deliveryStatus = deliveryStatus,
                    countdownDeadlineElapsedRealtime = deadline
                )
            )
        } catch (_: Exception) {
            DominosExtractionResult.UnreadableDominosLiveNotification("remote view rendering failed")
        }
    }

    private fun sourceRemoteViews(notification: Notification): List<RemoteViews> {
        return listOfNotNull(
            remoteViewsFrom(notification, "bigContentView"),
            remoteViewsFrom(notification, "contentView")
        )
    }

    private fun remoteViewsFrom(notification: Notification, fieldName: String): RemoteViews? {
        return runCatching {
            Notification::class.java.getField(fieldName).get(notification) as? RemoteViews
        }.getOrNull()
    }

    private fun View.textFor(context: Context, name: String): CharSequence? {
        return viewFor<TextView>(context, name)?.text?.takeIf { it.isNotBlank() }
    }

    private inline fun <reified T : View> View.viewFor(context: Context, name: String): T? {
        val id = context.resources.getIdentifier(name, "id", PACKAGE_NAME)
        if (id == 0) return null
        return findViewById<View>(id) as? T
    }
}

object DominosDeliveryTime {
    private const val MINUTE_MILLIS = 60_000L
    private val relativeDuration = Regex(
        """(?i)\b(?:(\d+)\s*(?:h|hr|hrs|hour|hours)(?:\s*(\d+)\s*(?:m|min|mins|minute|minutes))?|(\d+)\s*(?:m|min|mins|minute|minutes))\b"""
    )
    private val arrivalTime = Regex(
        """(?i)\b(?:eta|arrival|arrives?|delivery|delivered|expected)(?:\s+(?:by|at))?(?:\s+is)?\s+(?:at\s+)?(\d{1,2}):(\d{2})\s*(am|pm)?\b"""
    )
    private val deliveredStatus = Regex("""(?i)\bdelivered\b""")
    private val reachedStatus = Regex("""(?i)\b(?:reached|arrived)\b""")

    fun deliveryStatus(text: String): DominosDeliveryStatus = when {
        deliveredStatus.containsMatchIn(text) -> DominosDeliveryStatus.DELIVERED
        reachedStatus.containsMatchIn(text) -> DominosDeliveryStatus.REACHED
        else -> DominosDeliveryStatus.NONE
    }

    fun parseDeadline(
        text: String,
        nowWallTimeMillis: Long,
        nowElapsedRealtime: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (!hasDeliveryContext(text)) return null
        relativeDuration.find(text)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLongOrNull()
                ?: match.groupValues[3].toLongOrNull()
                ?: 0L
            val durationMillis = (hours * 60L + minutes) * MINUTE_MILLIS
            if (durationMillis > 0L) return nowElapsedRealtime + durationMillis
        }
        val match = arrivalTime.find(text) ?: return null
        val hourValue = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        val hour = hourIn24HourClock(hourValue, match.groupValues[3]) ?: return null
        val now = Instant.ofEpochMilli(nowWallTimeMillis).atZone(zoneId)
        var deadline = LocalDateTime.of(now.toLocalDate(), java.time.LocalTime.of(hour, minute))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        if (deadline <= nowWallTimeMillis) deadline += 24L * 60L * MINUTE_MILLIS
        return nowElapsedRealtime + (deadline - nowWallTimeMillis)
    }

    fun remainingText(deadlineElapsedRealtime: Long, nowElapsedRealtime: Long): String {
        val remainingMillis = (deadlineElapsedRealtime - nowElapsedRealtime).coerceAtLeast(0L)
        val minutes = ceil(remainingMillis.toDouble() / MINUTE_MILLIS.toDouble()).toLong()
        return "$minutes min"
    }

    fun nextUpdateDelayMillis(deadlineElapsedRealtime: Long, nowElapsedRealtime: Long): Long? {
        val remainingMillis = deadlineElapsedRealtime - nowElapsedRealtime
        if (remainingMillis <= 0L) return null
        val remainder = remainingMillis % MINUTE_MILLIS
        return if (remainder == 0L) MINUTE_MILLIS else remainder
    }

    private fun hasDeliveryContext(text: String): Boolean {
        return text.contains("deliver", ignoreCase = true) ||
            text.contains("arrival", ignoreCase = true) ||
            text.contains("arrive", ignoreCase = true) ||
            text.contains("eta", ignoreCase = true)
    }

    private fun hourIn24HourClock(hour: Int, meridiem: String): Int? {
        return when (meridiem.lowercase()) {
            "am" -> hour.takeIf { it in 1..12 }?.rem(12)
            "pm" -> hour.takeIf { it in 1..12 }?.let { if (it == 12) 12 else it + 12 }
            "" -> hour.takeIf { it in 0..23 }
            else -> null
        }
    }
}

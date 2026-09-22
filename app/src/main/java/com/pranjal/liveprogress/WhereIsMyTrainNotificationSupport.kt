package com.pranjal.liveprogress

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface WhereIsMyTrainExtractionResult {
    data class Extracted(val data: WhereIsMyTrainNotificationData) : WhereIsMyTrainExtractionResult
    data object NotLiveStatusNotification : WhereIsMyTrainExtractionResult
    data class UnreadableLiveStatusNotification(val reason: String) : WhereIsMyTrainExtractionResult
}

data class WhereIsMyTrainNotificationData(
    val title: CharSequence,
    val currentStatus: CharSequence?,
    val destination: CharSequence?,
    val distance: CharSequence?,
    val eta: CharSequence?,
    val scheduleStatus: CharSequence?,
    val progress: ProgressInfo
)

enum class WhereIsMyTrainMirrorRoute {
    CUSTOM_PROGRESS,
    NATIVE_PROGRESS,
    ADDITIONAL,
    NONE
}

object WhereIsMyTrainNotificationRouting {
    fun decide(
        progressEnabled: Boolean,
        recognizedCustomLayout: Boolean,
        hasCustomCandidate: Boolean,
        hasNativeProgress: Boolean,
        additionalEnabled: Boolean
    ): WhereIsMyTrainMirrorRoute {
        if (progressEnabled && hasCustomCandidate) return WhereIsMyTrainMirrorRoute.CUSTOM_PROGRESS
        if (progressEnabled && !recognizedCustomLayout && hasNativeProgress) {
            return WhereIsMyTrainMirrorRoute.NATIVE_PROGRESS
        }
        return if (additionalEnabled) WhereIsMyTrainMirrorRoute.ADDITIONAL else WhereIsMyTrainMirrorRoute.NONE
    }
}

object WhereIsMyTrainNotificationSupport {
    const val PACKAGE_NAME = "com.whereismytrain.android"

    private const val COLLAPSED_LAYOUT = "spot_notification_collapse"
    private const val EXPANDED_LAYOUT = "notification_expanded"
    private const val TITLE = "collapse_notification_title"
    private const val ETA = "collapse_notification_eta"
    private const val SCHEDULE_STATUS = "collapse_notification_delayed_by"
    private const val TRAIN_PROGRESS_IMAGE = "trainProgressImage"
    private const val PROGRESS_MAX = 1_000
    private const val MIN_ROUTE_WIDTH_RATIO = 0.45
    private val fullTrainRoute = Regex("""\b\d{3,6}\s*[-–]\s*[^\n]+?\s*[-–]\s*[^\n]+""")
    private val trainLabel = Regex("""\b\d{3,6}\s*[-–]\s*[^\n]+""")
    private val destinationDetail = Regex(
        """(?i)\b([A-Za-z][A-Za-z .'-]{1,})\s+(\d+(?:\.\d+)?\s*km)\s*[-–]\s*(\d{1,2}:\d{2}\s*(?:am|pm)?)\b"""
    )
    private val distancePattern = Regex("""(?i)\b(\d+(?:\.\d+)?\s*km)\b""")
    private val timeOfDay = Regex("""(?i)\b\d{1,2}:\d{2}\s*(?:am|pm)\b""")
    private val trailingTimeOfDay = Regex("""(?i)\s+\d{1,2}:\d{2}\s*(?:am|pm)\s*$""")

    fun isWhereIsMyTrain(sbn: StatusBarNotification): Boolean = sbn.packageName == PACKAGE_NAME

    fun extract(context: Context, sbn: StatusBarNotification): WhereIsMyTrainExtractionResult {
        if (!isWhereIsMyTrain(sbn)) return WhereIsMyTrainExtractionResult.NotLiveStatusNotification
        val notification = sbn.notification ?: return WhereIsMyTrainExtractionResult.NotLiveStatusNotification
        val sourceContext = try {
            context.createPackageContext(PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY)
        } catch (_: Exception) {
            return WhereIsMyTrainExtractionResult.UnreadableLiveStatusNotification(
                "package context unavailable"
            )
        }
        val collapsedLayoutId = sourceContext.resources.getIdentifier(
            COLLAPSED_LAYOUT,
            "layout",
            PACKAGE_NAME
        )
        if (collapsedLayoutId == 0) {
            return WhereIsMyTrainExtractionResult.UnreadableLiveStatusNotification("layout unavailable")
        }
        val collapsedRemoteView = sourceRemoteViews(notification)
            .firstOrNull { it.layoutId == collapsedLayoutId }
            ?: return WhereIsMyTrainExtractionResult.NotLiveStatusNotification

        return try {
            val collapsedView = collapsedRemoteView.apply(sourceContext, null)
                ?: return WhereIsMyTrainExtractionResult.UnreadableLiveStatusNotification(
                    "collapsed view unavailable"
                )
            val currentStatus = collapsedView.textFor(sourceContext, TITLE)
                ?: return WhereIsMyTrainExtractionResult.UnreadableLiveStatusNotification(
                    "title unavailable"
                )
            val routeBitmap = expandedRouteBitmap(sourceContext, notification)
            val ocrData = routeBitmap?.let(::recognizedRouteData)
            val compactEta = collapsedView.textFor(sourceContext, ETA)
            val trainTitle = ocrData?.trainTitle ?: currentStatus
            WhereIsMyTrainExtractionResult.Extracted(
                WhereIsMyTrainNotificationData(
                    title = trainTitle,
                    currentStatus = currentStatus,
                    destination = ocrData?.destination,
                    distance = ocrData?.distance,
                    eta = ocrData?.eta ?: compactEta,
                    scheduleStatus = collapsedView.textFor(sourceContext, SCHEDULE_STATUS),
                    progress = routeBitmap?.let(::progressInfoFromRouteBitmap)
                        ?: ProgressInfo(progress = 0, max = 0, indeterminate = true)
                )
            )
        } catch (_: Exception) {
            WhereIsMyTrainExtractionResult.UnreadableLiveStatusNotification(
                "remote view rendering failed"
            )
        }
    }

    internal fun destinationFromTitle(title: String): String? {
        val routeEnd = title.split(" - ").lastOrNull()?.trim().orEmpty()
        if (routeEnd.isBlank() || routeEnd == title.trim()) return null
        return routeEnd.replace(Regex("\\s+(?:MMTS|EXPRESS|SUPERFAST|MAIL|PASSENGER)$", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    internal fun normalizeTrainTitle(title: String): String {
        return title.replace(trailingTimeOfDay, "").trim()
    }

    internal fun parseRecognizedRouteText(text: String): WhereIsMyTrainOcrData? {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val routeTitle = lines.firstNotNullOfOrNull { line ->
            fullTrainRoute.find(line)?.value
                ?: trainLabel.find(line)?.value
        }?.let(::normalizeTrainTitle)
        val detail = lines.asSequence()
            .mapNotNull { line -> destinationDetail.find(line) }
            .lastOrNull()
        val destination = detail?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: routeTitle?.let(::destinationFromTitle)
        val distance = detail?.groupValues?.getOrNull(2)
            ?: lines.asSequence()
                .flatMap { line -> distancePattern.findAll(line).asSequence() }
                .lastOrNull()
                ?.groupValues
                ?.getOrNull(1)
        val normalizedDistance = distance?.replace(Regex("\\s+"), " ")
        val eta = detail?.groupValues?.getOrNull(3)?.uppercase()
            ?: lines.asSequence().flatMap { line -> timeOfDay.findAll(line).asSequence() }.lastOrNull()?.value?.uppercase()
        if (routeTitle == null && destination == null && eta == null) return null
        return WhereIsMyTrainOcrData(
            trainTitle = routeTitle,
            destination = destination,
            distance = normalizedDistance,
            eta = eta
        )
    }

    internal fun progressInfoFromRouteBitmap(bitmap: Bitmap): ProgressInfo? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val minimumWidth = (bitmap.width * MIN_ROUTE_WIDTH_RATIO).roundToInt()
        var best: RailRow? = null
        for (y in bitmap.height / 8 until bitmap.height * 7 / 8) {
            val row = railRow(bitmap, y, minimumWidth) ?: continue
            if (best == null || row.score > best.score) best = row
        }
        val rail = best ?: return null
        val greenEnd = rail.lastGreenX ?: return null
        return progressInfoFromRouteEdges(rail.startX, rail.endX, greenEnd)
    }

    internal fun progressInfoFromRouteEdges(
        startX: Int,
        endX: Int,
        greenEndX: Int
    ): ProgressInfo? {
        if (endX <= startX) return null
        val fraction = ((greenEndX - startX).toDouble() / (endX - startX))
            .coerceIn(0.0, 1.0)
        return ProgressInfo(
            progress = (fraction * PROGRESS_MAX).roundToInt(),
            max = PROGRESS_MAX,
            indeterminate = false
        )
    }

    private fun expandedRouteBitmap(context: Context, notification: Notification): Bitmap? {
        val expandedLayoutId = context.resources.getIdentifier(
            EXPANDED_LAYOUT,
            "layout",
            PACKAGE_NAME
        )
        val remoteViews = sourceRemoteViews(notification)
            .firstOrNull { it.layoutId == expandedLayoutId }
            ?: return null
        val view = remoteViews.apply(context, null) ?: return null
        return view.viewFor<ImageView>(context, TRAIN_PROGRESS_IMAGE)
            ?.drawable
            ?.toBitmap()
    }

    private fun recognizedRouteData(bitmap: Bitmap): WhereIsMyTrainOcrData? {
        return NotificationImageOcr.recognizeBitmap(bitmap)?.let(::parseRecognizedRouteText)
    }

    private fun railRow(bitmap: Bitmap, y: Int, minimumWidth: Int): RailRow? {
        var start = -1
        var end = -1
        var greenCount = 0
        var grayCount = 0
        var lastGreen: Int? = null
        for (x in 0 until bitmap.width) {
            val color = bitmap.getPixel(x, y)
            val isGreen = color.isRouteGreen()
            val isGray = color.isRouteGray()
            if (isGreen || isGray) {
                if (start == -1) start = x
                end = x
                if (isGreen) {
                    greenCount++
                    lastGreen = x
                } else {
                    grayCount++
                }
            }
        }
        if (start == -1 || end - start + 1 < minimumWidth || greenCount < 3 || grayCount < 3) {
            return null
        }
        return RailRow(
            startX = start,
            endX = end,
            lastGreenX = lastGreen,
            score = (end - start + 1) + greenCount + grayCount
        )
    }

    private fun Int.isRouteGreen(): Boolean {
        val red = (this shr 16) and 0xFF
        val green = (this shr 8) and 0xFF
        val blue = this and 0xFF
        return green >= 90 && green >= red + 20 && green >= blue + 8
    }

    private fun Int.isRouteGray(): Boolean {
        val red = (this shr 16) and 0xFF
        val green = (this shr 8) and 0xFF
        val blue = this and 0xFF
        return red >= 110 && green >= 110 && blue >= 110 &&
            abs(red - green) <= 18 && abs(green - blue) <= 18
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

    private fun Drawable.toBitmap(): Bitmap? {
        val width = intrinsicWidth.takeIf { it > 0 } ?: return null
        val height = intrinsicHeight.takeIf { it > 0 } ?: return null
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
    }

    private data class RailRow(
        val startX: Int,
        val endX: Int,
        val lastGreenX: Int?,
        val score: Int
    )
}

data class WhereIsMyTrainOcrData(
    val trainTitle: String?,
    val destination: String?,
    val distance: String?,
    val eta: String?
)

object WhereIsMyTrainText {
    fun statusText(
        currentStatus: CharSequence?,
        scheduleStatus: CharSequence?
    ): CharSequence? {
        val current = currentStatus?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val schedule = scheduleStatus?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::compactScheduleStatus)
        return listOfNotNull(current, schedule)
            .joinToString(" - ")
            .takeIf { it.isNotEmpty() }
    }

    fun destinationText(
        destination: CharSequence?,
        distance: CharSequence?,
        reachingText: String?
    ): CharSequence? {
        return listOfNotNull(
            destination?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            distance?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            reachingText
        ).joinToString(" · ").takeIf { it.isNotEmpty() }
    }

    internal fun compactScheduleStatus(status: String): String {
        val delayed = Regex("""(?i)^delayed by (\d+) minutes?$""").matchEntire(status)
        if (delayed != null) return "+${delayed.groupValues[1]} min"
        val early = Regex("""(?i)^(\d+) minutes? early$""").matchEntire(status)
        if (early != null) return "${early.groupValues[1]} min early"
        val ahead = Regex("""(?i)^ahead by (\d+) minutes?$""").matchEntire(status)
        if (ahead != null) return "Ahead by ${ahead.groupValues[1]} min"
        return status
    }
}

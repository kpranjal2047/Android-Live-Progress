package com.pranjal.liveprogress

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.RemoteViews
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface UberExtractionResult {
    data class Extracted(val data: UberNotificationData) : UberExtractionResult
    data object NotUberRichNotification : UberExtractionResult
    data class UnreadableUberRichNotification(val reason: String) : UberExtractionResult
}

data class UberNotificationData(
    val title: CharSequence,
    val text: CharSequence?,
    val progress: ProgressInfo,
    val largeIcon: Icon?,
    val visualPayloadKey: String?
)

internal enum class UberNotificationLayout(val resourceName: String) {
    RICH("ub__rich_notification_custom_big"),
    RIDER_TRIP("ub__ooa_layout_rider_trip_expanded");

    companion object {
        fun fromResourceName(name: String?): UberNotificationLayout? =
            entries.firstOrNull { it.resourceName == name }
    }
}

object UberNotificationSupport {
    const val PACKAGE_NAME = "com.ubercab"

    private const val TITLE = "ub__rich_notification_title"
    private const val SUBTITLE = "ub__rich_notification_subtitle"
    private const val PROGRESS = "progress_bar_filled"
    private const val DRIVER = "ub__rich_notification_big_left_image"
    private const val CAR = "ub__rich_notification_big_right_image"
    private const val EXPANDED_TITLE = "ub__rich_notification_expanded_title"
    private const val EXPANDED_SUBTITLE = "ub__rich_notification_expanded_subtitle"
    private const val TRIP_HEADER = "ub__ooa_layout_expanded_rider_trip_header"
    private const val TRIP_BODY = "ub__ooa_layout_expanded_rider_trip_body"
    private const val TRIP_TEXT = "ub__ooa_element_text"
    private const val TRIP_PROGRESS = "ub__ooa_layout_rider_trip_expanded_progress_container"
    private const val TRIP_SIMPLE_PROGRESS = "ub__ooa_element_simple_progress"
    private const val TRIP_DRIVER = "ub__ooa_element_expanded_driver_image"
    private const val TRIP_CAR = "ub__ooa_element_expanded_vehicle_image"
    private const val EXPANDED_EDGE_DISTANCE = "progress_bar_expanded_screen_edges_distance_s"
    private const val PROGRESS_EDGE_DISTANCE_DP = 110
    private const val PROGRESS_MAX = 1_000
    private const val ARTWORK_SIZE = 256
    private val clockTime = Regex(
        """(?<![\p{L}\p{N}:])(\p{Nd}{1,2}):(\p{Nd}{2})(?:[\s\u00a0\u202f]*([aApP]\.?[mM]\.?))?(?![\p{L}\p{N}:])"""
    )
    private val relativeTime = Regex(
        """(?i)(?<![\p{L}\p{N}])(\p{Nd}+\s*(?:hours?|hrs?|h)(?:\s*\p{Nd}+\s*(?:minutes?|mins?))?|\p{Nd}+\s*(?:minutes?|mins?))(?![\p{L}\p{N}])"""
    )

    internal fun etaText(title: CharSequence): String {
        val source = title.toString()
        clockTime.findAll(source).firstOrNull { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@firstOrNull false
            val minute = match.groupValues[2].toIntOrNull() ?: return@firstOrNull false
            minute in 0..59 && if (match.groupValues[3].isEmpty()) hour in 0..23 else hour in 1..12
        }?.let { return it.value.replace(Regex("[\\s\\u00a0\\u202f]+"), " ") }
        // Read the ETA header only: destination/body text can contain unrelated numbers.
        return relativeTime.find(source)?.value.orEmpty()
    }

    fun isUber(sbn: StatusBarNotification): Boolean = sbn.packageName == PACKAGE_NAME

    fun extract(context: Context, sbn: StatusBarNotification): UberExtractionResult {
        if (!isUber(sbn)) return UberExtractionResult.NotUberRichNotification
        val notification = sbn.notification ?: return UberExtractionResult.NotUberRichNotification
        val remoteViews = bigContentView(notification)
            ?: return unsupportedLayout(notification)
        val uberContext = try {
            context.createPackageContext(PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY)
        } catch (_: Exception) {
            return UberExtractionResult.UnreadableUberRichNotification("package context unavailable")
        }
        val layoutName = runCatching {
            uberContext.resources.getResourceEntryName(remoteViews.layoutId)
        }.getOrNull()
        val layout = UberNotificationLayout.fromResourceName(layoutName)
            ?: return unsupportedLayout(notification)

        return try {
            val view = remoteViews.apply(uberContext, null)
                ?: return UberExtractionResult.UnreadableUberRichNotification("remote view unavailable")
            val riderTrip = layout == UberNotificationLayout.RIDER_TRIP
            // Header and body reuse the same text ID, so resolve inside each container.
            val title = if (riderTrip) {
                view.viewFor<View>(uberContext, TRIP_HEADER)?.textFor(uberContext, TRIP_TEXT)
            } else {
                view.textFor(uberContext, EXPANDED_TITLE) ?: view.textFor(uberContext, TITLE)
            }
            title
                ?: return UberExtractionResult.UnreadableUberRichNotification("title unavailable")
            val text = if (riderTrip) {
                view.viewFor<View>(uberContext, TRIP_BODY)?.textFor(uberContext, TRIP_TEXT)
            } else {
                view.textFor(uberContext, EXPANDED_SUBTITLE) ?: view.textFor(uberContext, SUBTITLE)
            }
            val progressRoot = if (riderTrip) {
                view.viewFor<View>(uberContext, TRIP_PROGRESS)
            } else {
                view
            }
            val progress = progressRoot?.readProgress(uberContext)
                ?: return UberExtractionResult.UnreadableUberRichNotification("visible progress unavailable")
            val driver = view.imageFor(uberContext, if (riderTrip) TRIP_DRIVER else DRIVER)
            val car = view.imageFor(uberContext, if (riderTrip) TRIP_CAR else CAR)
            val artwork = when {
                driver != null && car != null -> composeArtwork(driver, car)
                else -> car ?: driver
            }
            UberExtractionResult.Extracted(
                UberNotificationData(
                    title = title,
                    text = text,
                    progress = progress,
                    largeIcon = artwork?.let(Icon::createWithBitmap),
                    visualPayloadKey = artwork?.let { "${sbn.postTime}:${it.width}x${it.height}" }
                )
            )
        } catch (_: Exception) {
            UberExtractionResult.UnreadableUberRichNotification("remote view rendering failed")
        }
    }

    private fun unsupportedLayout(notification: Notification): UberExtractionResult {
        // Never interpret placeholder extras on a custom ride notification as native progress.
        val hasCustomViews = listOf("contentView", "bigContentView").any { field ->
            runCatching { Notification::class.java.getField(field).get(notification) != null }
                .getOrDefault(false)
        }
        return if (hasCustomViews) {
            UberExtractionResult.UnreadableUberRichNotification("unsupported custom layout")
        } else {
            UberExtractionResult.NotUberRichNotification
        }
    }

    private fun View.readProgress(context: Context): ProgressInfo? {
        viewFor<FrameLayout>(context, PROGRESS)?.let { bar ->
            return progressInfoFromBar(bar.paddingLeft, usableProgressWidth(context))
        }
        val bar = viewFor<ProgressBar>(context, TRIP_SIMPLE_PROGRESS) ?: return null
        return progressInfoFromNativeBar(bar.progress, bar.max, bar.isIndeterminate)
    }

    internal fun progressInfoFromNativeBar(progress: Int, max: Int, indeterminate: Boolean): ProgressInfo? {
        if (indeterminate || max <= 0) return null
        return ProgressInfo(progress.coerceIn(0, max), max, indeterminate = false)
    }

    internal fun progressInfoFromBar(
        filledPaddingLeft: Int,
        usableWidth: Int
    ): ProgressInfo? {
        if (filledPaddingLeft < 0 || usableWidth <= 0) return null
        val progress = ((filledPaddingLeft.toDouble() / usableWidth) * PROGRESS_MAX)
            .roundToInt()
            .coerceIn(0, PROGRESS_MAX)
        return ProgressInfo(progress = progress, max = PROGRESS_MAX, indeterminate = false)
    }

    private fun usableProgressWidth(context: Context): Int {
        val resourceId = context.resources.getIdentifier(EXPANDED_EDGE_DISTANCE, "dimen", PACKAGE_NAME)
        val edgeDistance = if (resourceId != 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            (PROGRESS_EDGE_DISTANCE_DP * context.resources.displayMetrics.density).roundToInt()
        }
        return context.resources.displayMetrics.widthPixels - edgeDistance
    }

    private fun bigContentView(notification: Notification): RemoteViews? {
        return runCatching {
            Notification::class.java.getField("bigContentView").get(notification) as? RemoteViews
        }.getOrNull()
    }

    private fun View.textFor(context: Context, name: String): CharSequence? {
        return viewFor<TextView>(context, name)?.text?.takeIf { it.isNotBlank() }
    }

    private fun View.imageFor(context: Context, name: String): Bitmap? {
        val drawable = viewFor<ImageView>(context, name)?.drawable ?: return null
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: return null
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: return null
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }

    private inline fun <reified T : View> View.viewFor(context: Context, name: String): T? {
        val id = context.resources.getIdentifier(name, "id", PACKAGE_NAME)
        if (id == 0) return null
        val found = findViewById<View>(id) as? T ?: return null
        var current: View? = found
        while (current != null) {
            if (current.visibility != View.VISIBLE) return null
            if (current === this) return found
            current = current.parent as? View
        }
        return null
    }

    private fun composeArtwork(driver: Bitmap, car: Bitmap): Bitmap {
        val bitmap = Bitmap.createBitmap(ARTWORK_SIZE, ARTWORK_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        drawContained(
            canvas = canvas,
            bitmap = car,
            target = RectF(24f, 72f, 232f, 214f),
            paint = paint
        )
        val driverBounds = RectF(24f, 24f, 104f, 104f)
        canvas.save()
        canvas.clipPath(Path().apply { addOval(driverBounds, Path.Direction.CW) })
        drawContained(canvas, driver, driverBounds, paint)
        canvas.restore()
        return bitmap
    }

    private fun drawContained(
        canvas: Canvas,
        bitmap: Bitmap,
        target: RectF,
        paint: Paint
    ) {
        val scale = min(target.width() / bitmap.width, target.height() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = target.centerX() - (width / 2f)
        val top = target.centerY() - (height / 2f)
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), paint)
    }
}

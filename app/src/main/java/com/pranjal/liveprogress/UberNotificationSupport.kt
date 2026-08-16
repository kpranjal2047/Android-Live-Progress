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
    val largeIcon: Icon,
    val visualPayloadKey: String
)

object UberNotificationSupport {
    const val PACKAGE_NAME = "com.ubercab"

    private const val RICH_LAYOUT = "ub__rich_notification_custom_big"
    private const val TITLE = "ub__rich_notification_title"
    private const val SUBTITLE = "ub__rich_notification_subtitle"
    private const val PROGRESS = "progress_bar_filled"
    private const val DRIVER = "ub__rich_notification_big_left_image"
    private const val CAR = "ub__rich_notification_big_right_image"
    private const val EXPANDED_TITLE = "ub__rich_notification_expanded_title"
    private const val EXPANDED_SUBTITLE = "ub__rich_notification_expanded_subtitle"
    private const val PROGRESS_EDGE_DISTANCE_DP = 110
    private const val PROGRESS_MAX = 1_000
    private const val ARTWORK_SIZE = 256

    fun isUber(sbn: StatusBarNotification): Boolean = sbn.packageName == PACKAGE_NAME

    fun extract(context: Context, sbn: StatusBarNotification): UberExtractionResult {
        if (!isUber(sbn)) return UberExtractionResult.NotUberRichNotification
        val notification = sbn.notification ?: return UberExtractionResult.NotUberRichNotification
        val remoteViews = bigContentView(notification)
            ?: return UberExtractionResult.NotUberRichNotification
        val uberContext = try {
            context.createPackageContext(PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY)
        } catch (_: Exception) {
            return UberExtractionResult.UnreadableUberRichNotification("package context unavailable")
        }
        val layoutId = uberContext.resources.getIdentifier(RICH_LAYOUT, "layout", PACKAGE_NAME)
        if (layoutId == 0 || remoteViews.layoutId != layoutId) {
            return UberExtractionResult.NotUberRichNotification
        }

        return try {
            val view = remoteViews.apply(context, null)
                ?: return UberExtractionResult.UnreadableUberRichNotification("remote view unavailable")
            val normalTitle = view.textFor(uberContext, TITLE)
            val normalSubtitle = view.textFor(uberContext, SUBTITLE)
            val expandedTitle = view.textFor(uberContext, EXPANDED_TITLE)
            val expandedSubtitle = view.textFor(uberContext, EXPANDED_SUBTITLE)
            val title = expandedTitle ?: normalTitle
                ?: return UberExtractionResult.UnreadableUberRichNotification("title unavailable")
            val text = expandedSubtitle ?: normalSubtitle
            val progressView = view.viewFor<FrameLayout>(uberContext, PROGRESS)
                ?: return UberExtractionResult.UnreadableUberRichNotification("progress unavailable")
            val progress = progressInfoFromBar(
                filledPaddingLeft = progressView.paddingLeft,
                usableWidth = usableProgressWidth(context)
            ) ?: return UberExtractionResult.UnreadableUberRichNotification("progress width unavailable")
            val driver = view.imageFor(uberContext, DRIVER)
                ?: return UberExtractionResult.UnreadableUberRichNotification("driver artwork unavailable")
            val car = view.imageFor(uberContext, CAR)
                ?: return UberExtractionResult.UnreadableUberRichNotification("car artwork unavailable")
            UberExtractionResult.Extracted(
                UberNotificationData(
                    title = title,
                    text = text,
                    progress = progress,
                    largeIcon = Icon.createWithBitmap(composeArtwork(driver, car)),
                    visualPayloadKey = "${sbn.postTime}:${driver.width}x${driver.height}:${car.width}x${car.height}"
                )
            )
        } catch (_: Exception) {
            UberExtractionResult.UnreadableUberRichNotification("remote view rendering failed")
        }
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
        val edgeDistance = (PROGRESS_EDGE_DISTANCE_DP * context.resources.displayMetrics.density)
            .roundToInt()
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
        return findViewById<View>(id) as? T
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

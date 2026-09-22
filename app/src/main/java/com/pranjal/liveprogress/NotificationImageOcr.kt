package com.pranjal.liveprogress

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

data class NotificationOcrText(
    val fullText: String,
    val title: CharSequence?,
    val text: CharSequence?
)

object NotificationImageOcr {
    private const val OCR_TIMEOUT_SECONDS = 3L
    private const val MINIMUM_IMAGE_WIDTH = 96
    private const val MINIMUM_IMAGE_HEIGHT = 48

    fun extract(context: Context, sbn: StatusBarNotification): NotificationOcrText? {
        val notification = sbn.notification ?: return null
        val sourceContext = runCatching {
            context.createPackageContext(sbn.packageName, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrElse { context }
        val images = sourceRemoteViews(notification)
            .flatMap { remoteViews -> renderedImages(remoteViews, sourceContext) }
            .sortedByDescending { it.width * it.height }
        return images.firstNotNullOfOrNull { bitmap ->
            recognizeBitmap(bitmap)?.let(::fromRecognizedText)
        }
    }

    internal fun recognizeBitmap(bitmap: Bitmap): String? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            Tasks.await(
                recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                OCR_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            ).text.trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            recognizer.close()
        }
    }

    internal fun fromRecognizedText(text: String): NotificationOcrText? {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.isEmpty()) return null
        return NotificationOcrText(
            fullText = lines.joinToString("\n"),
            title = lines.first(),
            text = lines.drop(1).joinToString("\n").takeIf { it.isNotEmpty() }
        )
    }

    private fun sourceRemoteViews(notification: Notification): List<RemoteViews> {
        return listOfNotNull(
            remoteViewsFrom(notification, "bigContentView"),
            remoteViewsFrom(notification, "contentView")
        )
    }

    private fun renderedImages(remoteViews: RemoteViews, context: Context): List<Bitmap> {
        val root = runCatching { remoteViews.apply(context, null) }.getOrNull() ?: return emptyList()
        return buildList { root.collectImageBitmaps(this) }
    }

    private fun View.collectImageBitmaps(destination: MutableList<Bitmap>) {
        if (this is ImageView) {
            drawable?.toBitmap()?.let { bitmap ->
                if (bitmap.width >= MINIMUM_IMAGE_WIDTH && bitmap.height >= MINIMUM_IMAGE_HEIGHT) {
                    destination += bitmap
                }
            }
        }
        if (this is ViewGroup) {
            repeat(childCount) { index -> getChildAt(index).collectImageBitmaps(destination) }
        }
    }

    private fun remoteViewsFrom(notification: Notification, fieldName: String): RemoteViews? {
        return runCatching {
            Notification::class.java.getField(fieldName).get(notification) as? RemoteViews
        }.getOrNull()
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
}

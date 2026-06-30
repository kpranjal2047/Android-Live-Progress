package com.pranjal.liveprogress

import android.content.Context
import android.service.notification.StatusBarNotification

data class MediaNotificationSource(
    val original: OriginalNotificationSource,
    val smallIcon: android.graphics.drawable.Icon?,
    val contentIntent: android.app.PendingIntent?
)

object MediaNotificationSourceFactory {
    fun from(context: Context, sbn: StatusBarNotification): MediaNotificationSource? {
        val notification = sbn.notification ?: return null
        if (!NotificationClassifier.isMediaLike(notification)) return null
        val label = AppLabelResolver.label(context, sbn.packageName, notification)
        return MediaNotificationSource(
            original = OriginalNotificationSource(
                key = sbn.key,
                packageName = sbn.packageName,
                sourceUid = sbn.uid,
                sourceUser = sbn.user,
                channelId = notification.channelId,
                appLabel = label
            ),
            smallIcon = notification.getSmallIcon(),
            contentIntent = notification.contentIntent
        )
    }
}

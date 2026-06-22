package com.pranjal.liveprogress

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification

data class MediaNotificationSource(
    val original: OriginalNotificationSource,
    val smallIcon: android.graphics.drawable.Icon?,
    val contentIntent: android.app.PendingIntent?
)

object MediaNotificationSourceFactory {
    fun from(context: Context, sbn: StatusBarNotification): MediaNotificationSource? {
        if (AppProgressNotificationSupport.isSupportedPackage(sbn.packageName)) return null
        val notification = sbn.notification ?: return null
        if (!notification.isMediaLike()) return null
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

    private fun Notification.isMediaLike(): Boolean {
        if (category == Notification.CATEGORY_TRANSPORT) return true
        val template = extras?.getString(Notification.EXTRA_TEMPLATE)
        if (template == "android.app.Notification\$MediaStyle" ||
            template == "android.app.Notification\$DecoratedMediaCustomViewStyle"
        ) {
            return true
        }
        return extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
    }
}

package com.pranjal.liveprogress

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import kotlin.math.absoluteValue

object NotificationClassifier {
    private val mediaTemplates = setOf(
        "android.app.Notification\$MediaStyle",
        "android.app.Notification\$DecoratedMediaCustomViewStyle"
    )

    private const val MAX_ACTIONS = 3

    fun toCandidate(context: Context, sbn: StatusBarNotification): MirrorCandidate? {
        if (sbn.packageName == context.packageName) return null

        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null
        if (notification.isAlreadyLiveProgress()) return null
        if (notification.isMediaLike()) return null

        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        if (!indeterminate && max <= 0) return null

        val progressInfo = ProgressInfo(
            progress = progress.coerceAtLeast(0),
            max = max.coerceAtLeast(0),
            indeterminate = indeterminate
        )

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val appLabel = AppLabelResolver.label(context, sbn.packageName, notification)
        val actions = notification.actions
            ?.filter { it.actionIntent != null && it.getRemoteInputs().isNullOrEmpty() && !it.isAuthenticationRequired }
            ?.take(MAX_ACTIONS)
            ?: emptyList()

        return MirrorCandidate(
            key = sbn.key,
            packageName = sbn.packageName,
            sourceId = sbn.id,
            sourceTag = sbn.tag,
            sourceUid = sbn.uid,
            sourceUser = sbn.user,
            channelId = notification.channelId,
            notificationId = mirrorIdFor(sbn.key),
            appLabel = appLabel,
            title = title,
            text = text,
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            contentIntent = notification.contentIntent,
            smallIcon = notification.getSmallIcon(),
            largeIcon = notification.getLargeIcon(),
            color = notification.color,
            whenMillis = notification.`when`,
            showWhen = extras.getBoolean(Notification.EXTRA_SHOW_WHEN, notification.`when` > 0L),
            actions = actions,
            progress = progressInfo
        )
    }

    private fun Notification.isAlreadyLiveProgress(): Boolean {
        return isAlreadyLiveProgressTemplate(
            template = extras?.getString(Notification.EXTRA_TEMPLATE),
            flags = flags,
            requestedPromotedOngoing = PromotedOngoingCompat.isRequested(this)
        )
    }

    internal fun isAlreadyLiveProgressTemplate(
        template: String?,
        flags: Int = 0,
        requestedPromotedOngoing: Boolean = false
    ): Boolean {
        val isProgressStyle = template == Notification.ProgressStyle::class.java.name
        val isPromoted = requestedPromotedOngoing ||
            (flags and Notification.FLAG_PROMOTED_ONGOING) != 0
        return isProgressStyle && isPromoted
    }

    private fun Notification.isMediaLike(): Boolean {
        if (category == Notification.CATEGORY_TRANSPORT) return true
        val template = extras?.getString(Notification.EXTRA_TEMPLATE)
        if (template in mediaTemplates) return true
        return extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
    }

    private fun mirrorIdFor(key: String): Int {
        val hash = key.hashCode()
        return if (hash == Int.MIN_VALUE) 1 else hash.absoluteValue + 1
    }
}

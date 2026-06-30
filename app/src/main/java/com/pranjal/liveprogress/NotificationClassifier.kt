package com.pranjal.liveprogress

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import kotlin.math.absoluteValue

object NotificationClassifier {
    private val mediaTemplates = setOf(
        "android.app.Notification\$MediaStyle",
        "android.app.Notification\$DecoratedMediaCustomViewStyle"
    )

    private const val MAX_ACTIONS = 3

    fun toCandidate(
        context: Context,
        sbn: StatusBarNotification,
        selectedCategory: (packageName: String, uid: Int, channelId: String?) -> Boolean = { _, _, _ -> false }
    ): MirrorCandidate? {
        if (sbn.packageName == context.packageName) return null

        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: Bundle.EMPTY
        if (notification.isAlreadyLiveProgress()) return null
        if (isMediaLike(notification)) return null

        val forceSelectedCategory = selectedCategory(
            sbn.packageName,
            sbn.uid,
            notification.channelId
        )
        val progressInfo = progressInfoFromValues(
            indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
            max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
            progress = extras.getInt(Notification.EXTRA_PROGRESS, 0),
            forceIndeterminate = forceSelectedCategory
        )
            ?: return null

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

    internal fun progressInfoFromValues(
        indeterminate: Boolean,
        max: Int,
        progress: Int,
        forceIndeterminate: Boolean
    ): ProgressInfo? {
        if (!indeterminate && max <= 0) {
            return if (forceIndeterminate) {
                ProgressInfo(progress = 0, max = 0, indeterminate = true)
            } else {
                null
            }
        }
        return ProgressInfo(
            progress = progress.coerceAtLeast(0),
            max = max.coerceAtLeast(0),
            indeterminate = indeterminate
        )
    }

    private fun Notification.isAlreadyLiveProgress(): Boolean {
        return isAlreadyLiveProgressTemplate(
            template = extras?.getString(Notification.EXTRA_TEMPLATE),
            flags = flags,
            requestedPromotedOngoing = PromotedOngoingCompat.isRequested(this)
        )
    }

    internal fun isAlreadyLiveProgress(notification: Notification): Boolean {
        return notification.isAlreadyLiveProgress()
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

    internal fun isMediaLike(notification: Notification): Boolean {
        return notification.isMediaLike()
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

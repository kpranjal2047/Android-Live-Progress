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
        progressEnabled: Boolean,
        progressDisplaySettings: MirrorCandidateDisplaySettings,
        additionalCategorySettings: (
            packageName: String,
            uid: Int,
            channelId: String?
        ) -> NotificationCategorySettings = { _, _, _ -> NotificationCategorySettings() },
        debug: ((String) -> Unit)? = null
    ): MirrorCandidate? {
        if (sbn.packageName == context.packageName) {
            debug?.invoke("ignored=self_notification")
            return null
        }

        val notification = sbn.notification ?: run {
            debug?.invoke("ignored=no_notification_payload")
            return null
        }
        val extras = notification.extras ?: Bundle.EMPTY
        if (notification.isAlreadyLiveProgress()) {
            debug?.invoke("ignored=already_live_progress")
            return null
        }
        if (isMediaLike(notification)) {
            debug?.invoke("ignored=media_notification")
            return null
        }

        val standardProgressInfo = progressInfoFromValues(
            indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
            max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
            progress = extras.getInt(Notification.EXTRA_PROGRESS, 0),
            forceIndeterminate = false
        )
        val additionalSettings = additionalCategorySettings(
            sbn.packageName,
            sbn.uid,
            notification.channelId
        )
        val displaySettings = when {
            standardProgressInfo != null && progressEnabled -> progressDisplaySettings
            additionalSettings.enabled -> MirrorCandidateDisplaySettings(
                source = MirrorCandidateSource.ADDITIONAL,
                showOnAod = additionalSettings.showOnAod,
                showOnLockScreen = additionalSettings.showOnLockScreen,
                hideOriginalNotification = additionalSettings.hideOriginalNotification,
                keepAfterOriginalDismissed = additionalSettings.keepAfterOriginalDismissed
            )
            else -> {
                val reason = if (standardProgressInfo != null) {
                    "ignored=progress_disabled"
                } else {
                    "ignored=no_progress_or_additional_category"
                }
                debug?.invoke(
                    "$reason; channel=${notification.channelId.orEmpty()}; " +
                        "additionalEnabled=${additionalSettings.enabled}"
                )
                return null
            }
        }
        val progressInfo = standardProgressInfo
            ?: ProgressInfo(progress = 0, max = 0, indeterminate = true)

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val appLabel = AppLabelResolver.label(context, sbn.packageName, notification)
        val actions = notification.actions
            ?.filter { it.actionIntent != null && it.remoteInputs.isNullOrEmpty() && !it.isAuthenticationRequired }
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
            smallIcon = notification.smallIcon,
            largeIcon = notification.getLargeIcon(),
            color = notification.color,
            whenMillis = notification.`when`,
            showWhen = extras.getBoolean(Notification.EXTRA_SHOW_WHEN, notification.`when` > 0L),
            actions = actions,
            progress = progressInfo,
            displaySettings = displaySettings
        ).also {
            debug?.invoke(
                "accepted=${displaySettings.source}; channel=${notification.channelId.orEmpty()}; " +
                    "progress=${progressInfo.progress}; max=${progressInfo.max}; " +
                    "indeterminate=${progressInfo.indeterminate}; actions=${actions.size}"
            )
        }
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

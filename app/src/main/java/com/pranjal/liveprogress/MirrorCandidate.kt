package com.pranjal.liveprogress

import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.UserHandle

data class ProgressInfo(
    val progress: Int,
    val max: Int,
    val indeterminate: Boolean
) {
    val shortText: String = ProgressMath.shortText(progress, max, indeterminate)
}

enum class MirrorCandidateSource {
    PROGRESS,
    ADDITIONAL
}

data class MirrorCandidateDisplaySettings(
    val source: MirrorCandidateSource,
    val showOnAod: Boolean,
    val showOnLockScreen: Boolean,
    val hideOriginalNotification: Boolean,
    val keepAfterOriginalDismissed: Boolean = false
)

data class MirrorCandidate(
    val key: String,
    val packageName: String,
    val sourceId: Int,
    val sourceTag: String?,
    val sourceUid: Int,
    val sourceUser: UserHandle,
    val channelId: String?,
    val notificationId: Int,
    val appLabel: String,
    val title: CharSequence?,
    val text: CharSequence?,
    val subText: CharSequence?,
    val contentIntent: PendingIntent?,
    val smallIcon: Icon?,
    val largeIcon: Icon?,
    val color: Int,
    val whenMillis: Long,
    val showWhen: Boolean,
    val actions: List<Notification.Action>,
    val progress: ProgressInfo,
    val displaySettings: MirrorCandidateDisplaySettings
)

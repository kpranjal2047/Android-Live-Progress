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
    val percent: Int? = ProgressMath.percent(progress, max, indeterminate)
    val shortText: String = ProgressMath.shortText(progress, max, indeterminate)
}

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
    val progress: ProgressInfo
)

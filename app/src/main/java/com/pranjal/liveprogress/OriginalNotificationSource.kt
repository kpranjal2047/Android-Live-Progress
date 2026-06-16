package com.pranjal.liveprogress

import android.os.UserHandle

data class OriginalNotificationSource(
    val key: String,
    val packageName: String,
    val sourceUid: Int,
    val sourceUser: UserHandle,
    val channelId: String?,
    val appLabel: String
)

fun MirrorCandidate.originalSource(): OriginalNotificationSource {
    return OriginalNotificationSource(
        key = key,
        packageName = packageName,
        sourceUid = sourceUid,
        sourceUser = sourceUser,
        channelId = channelId,
        appLabel = appLabel
    )
}

package com.pranjal.liveprogress

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object MirrorNotificationBuilder {
    const val CHANNEL_ID = "mirrored_live_progress"
    const val LOW_PRIORITY_CHANNEL_ID = "mirrored_live_progress_low"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(
            manager = manager,
            id = CHANNEL_ID,
            name = context.getString(R.string.mirror_channel_name),
            description = context.getString(R.string.mirror_channel_description),
            importance = NotificationManager.IMPORTANCE_DEFAULT
        )
        ensureChannel(
            manager = manager,
            id = LOW_PRIORITY_CHANNEL_ID,
            name = context.getString(R.string.mirror_channel_name),
            description = context.getString(R.string.mirror_channel_description),
            importance = NotificationManager.IMPORTANCE_LOW
        )
    }

    fun build(
        context: Context,
        candidate: MirrorCandidate,
        aodVisible: Boolean = false,
        useSourceSmallIcon: Boolean = true,
        priorityMode: MirrorPriorityMode = MirrorPriorityMode.DEFAULT
    ): Notification {
        val style = Notification.ProgressStyle()
            .setProgressIndeterminate(candidate.progress.indeterminate)
            .setStyledByProgress(true)

        if (!candidate.progress.indeterminate && candidate.progress.max > 0) {
            style
                .setProgressSegments(
                    listOf(Notification.ProgressStyle.Segment(candidate.progress.max))
                )
                .setProgress(candidate.progress.progress.coerceIn(0, candidate.progress.max))
        }

        val title = candidate.title ?: candidate.appLabel
        val progressText = candidate.progress.shortText.takeIf { it.isNotBlank() }
        val contentText = expandedProgressText(candidate.text, progressText)
        val subText = candidate.subText ?: candidate.appLabel
        val color = if (candidate.color != Notification.COLOR_DEFAULT) {
            candidate.color
        } else {
            SystemColorPalette.primary(context)
        }

        val builder = Notification.Builder(context, channelId(priorityMode))
            .apply {
                val sourceIcon = candidate.smallIcon.takeIf { useSourceSmallIcon }
                if (sourceIcon != null) {
                    setSmallIcon(sourceIcon)
                } else {
                    setSmallIcon(R.drawable.ic_notification_empty)
                }
            }
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(subText)
            .setContentIntent(candidate.contentIntent)
            .setLargeIcon(candidate.largeIcon)
            .setWhen(candidate.whenMillis)
            .setShowWhen(candidate.showWhen)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(color)
            .setStyle(style)
            .apply {
                if (candidate.progress.indeterminate) {
                    setProgress(0, 0, true)
                } else if (candidate.progress.max > 0) {
                    setProgress(
                        candidate.progress.max,
                        candidate.progress.progress.coerceIn(0, candidate.progress.max),
                        false
                    )
                }
                if (candidate.progress.shortText.isNotBlank()) {
                    setShortCriticalText(candidate.progress.shortText)
                }
                candidate.actions.forEach { addAction(it) }
            }

        return PromotedOngoingCompat.request(builder).build()
    }

    fun channelId(priorityMode: MirrorPriorityMode): String {
        return if (priorityMode == MirrorPriorityMode.LOW) LOW_PRIORITY_CHANNEL_ID else CHANNEL_ID
    }

    private fun expandedProgressText(
        sourceText: CharSequence?,
        progressText: String?
    ): CharSequence? {
        if (progressText.isNullOrBlank()) return sourceText
        val base = sourceText?.toString()?.trim().orEmpty()
        if (base.isBlank()) return progressText
        if (base.contains(progressText, ignoreCase = true)) return sourceText
        return "$base - $progressText"
    }

    private fun ensureChannel(
        manager: NotificationManager,
        id: String,
        name: String,
        description: String,
        importance: Int
    ) {
        val existing = manager.getNotificationChannel(id)
        if (existing != null) {
            existing.setName(name)
            existing.description = description
            existing.setSound(null, null)
            existing.enableVibration(false)
            existing.setShowBadge(false)
            manager.createNotificationChannel(existing)
            return
        }

        val channel = NotificationChannel(id, name, importance).apply {
            this.description = description
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}

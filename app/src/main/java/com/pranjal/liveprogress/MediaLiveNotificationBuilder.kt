package com.pranjal.liveprogress

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri

object MediaLiveNotificationBuilder {
    const val CHANNEL_ID = "media_live_updates_default"
    const val LOW_PRIORITY_CHANNEL_ID = "media_live_updates_low"
    const val NOTIFICATION_ID = 2001
    private const val LEGACY_LOW_IMPORTANCE_CHANNEL_ID = "media_live_updates"
    private val albumArtCacheLock = Any()
    private var cachedAlbumArtUri: Uri? = null
    private var cachedAlbumArtBitmap: Bitmap? = null

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(LEGACY_LOW_IMPORTANCE_CHANNEL_ID) != null) {
            manager.deleteNotificationChannel(LEGACY_LOW_IMPORTANCE_CHANNEL_ID)
        }
        ensureChannel(
            manager = manager,
            id = CHANNEL_ID,
            name = context.getString(R.string.media_channel_name),
            description = context.getString(R.string.media_channel_description),
            importance = NotificationManager.IMPORTANCE_DEFAULT
        )
        ensureChannel(
            manager = manager,
            id = LOW_PRIORITY_CHANNEL_ID,
            name = context.getString(R.string.media_channel_name),
            description = context.getString(R.string.media_channel_description),
            importance = NotificationManager.IMPORTANCE_LOW
        )
    }

    fun build(
        context: Context,
        state: MediaState,
        source: MediaNotificationSource?,
        preferences: MediaPreferences,
        titleElapsedMs: Long,
        aodVisible: Boolean,
        showShortCriticalText: Boolean,
        appLabelOverride: String? = null,
        priorityMode: MirrorPriorityMode = MirrorPriorityMode.DEFAULT
    ): Notification {
        val progress = MediaProgressMapper.map(state.positionMs, state.durationMs)
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressIndeterminate(progress.indeterminate)

        if (!progress.indeterminate) {
            style
                .setProgressSegments(listOf(Notification.ProgressStyle.Segment(progress.max)))
                .setProgress(progress.progress)
        }

        val appLabel = appLabelOverride
            ?: source?.original?.appLabel
            ?: AppLabelResolver.label(context, state.packageName)
        val shortCriticalText = if (showShortCriticalText) {
            MediaTextFormatter.shortCriticalText(
                title = state.title,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                isPlaying = state.isPlaying,
                mode = preferences.pillMode,
                scrollTitle = preferences.scrollTitle,
                titleElapsedMs = titleElapsedMs
            )
        } else {
            null
        }
        val contentText = MediaTextFormatter.detailText(
            artist = state.artist,
            album = state.album,
            showArtist = true,
            showAlbum = true
        )
        val subText = if (aodVisible) {
            MediaTextFormatter.aodSubText(
                provider = appLabel,
                positionMs = state.positionMs,
                durationMs = state.durationMs
            )
        } else {
            MediaTextFormatter.subText(
                provider = appLabel,
                showProvider = true,
                showTimestamp = true,
                positionMs = state.positionMs,
                durationMs = state.durationMs
            )
        }
        val builder = Notification.Builder(context, channelId(priorityMode))
            .apply {
                val sourceIcon = source?.smallIcon
                if (sourceIcon != null) {
                    setSmallIcon(sourceIcon)
                } else {
                    setSmallIcon(R.drawable.ic_notification_empty)
                }
            }
            .setContentTitle(state.title)
            .setContentText(contentText)
            .setSubText(subText)
            .setContentIntent(source?.contentIntent ?: launchIntent(context, state.packageName))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(Color.rgb(11, 110, 79))
            .setStyle(style)
            .apply {
                if (shortCriticalText != null) {
                    setShortCriticalText(shortCriticalText)
                }
            }

        builder.setLargeIcon(largeIcon(context, state))
        builder.setProgress(progress.max, progress.progress, progress.indeterminate)
        builder
            .addAction(mediaAction(context, android.R.drawable.ic_media_previous, "Previous", MediaControlReceiver.ACTION_PREVIOUS, 1))
            .addAction(
                mediaAction(
                    context,
                    if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (state.isPlaying) "Pause" else "Play",
                    MediaControlReceiver.ACTION_PLAY_PAUSE,
                    2
                )
            )
            .addAction(mediaAction(context, android.R.drawable.ic_media_next, "Next", MediaControlReceiver.ACTION_NEXT, 3))

        return PromotedOngoingCompat.request(builder).build()
    }

    fun channelId(priorityMode: MirrorPriorityMode): String {
        return if (priorityMode == MirrorPriorityMode.LOW) LOW_PRIORITY_CHANNEL_ID else CHANNEL_ID
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

    private fun mediaAction(
        context: Context,
        icon: Int,
        title: String,
        action: String,
        requestCode: Int
    ): Notification.Action {
        val intent = Intent(context, MediaControlReceiver::class.java).setAction(action)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            Icon.createWithResource("android", icon),
            title,
            pendingIntent
        ).build()
    }

    private fun launchIntent(context: Context, packageName: String): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun largeIcon(context: Context, state: MediaState): Bitmap? {
        state.albumArt?.let { return it }
        val uri = state.albumArtUri ?: return null
        synchronized(albumArtCacheLock) {
            if (uri == cachedAlbumArtUri) return cachedAlbumArtBitmap
        }
        val decoded = decodeBitmap(context, uri)
        synchronized(albumArtCacheLock) {
            cachedAlbumArtUri = uri
            cachedAlbumArtBitmap = decoded
        }
        return decoded
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
}

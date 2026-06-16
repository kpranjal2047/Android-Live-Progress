package com.pranjal.liveprogress

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock

data class MediaState(
    val title: String,
    val artist: String,
    val album: String,
    val albumArt: Bitmap?,
    val albumArtUri: Uri?,
    val isPlaying: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val packageName: String
) {
    companion object {
        fun from(
            packageName: String,
            metadata: MediaMetadata?,
            playbackState: PlaybackState?
        ): MediaState? {
            if (metadata == null || playbackState == null) return null
            if (playbackState.state == PlaybackState.STATE_NONE ||
                playbackState.state == PlaybackState.STATE_STOPPED
            ) {
                return null
            }

            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
            return MediaState(
                title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    ?: "Unknown Title",
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: "Unknown Artist",
                album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "Unknown Album",
                albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                albumArtUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(Uri::parse)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                        ?.takeIf { it.isNotBlank() }
                        ?.let(Uri::parse)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                        ?.takeIf { it.isNotBlank() }
                        ?.let(Uri::parse),
                isPlaying = playbackState.state == PlaybackState.STATE_PLAYING,
                durationMs = duration,
                positionMs = playbackState.currentPosition(duration),
                packageName = packageName
            )
        }

        private fun PlaybackState.currentPosition(durationMs: Long): Long {
            val base = position.coerceAtLeast(0L)
            val advanced = if (state == PlaybackState.STATE_PLAYING && lastPositionUpdateTime > 0L) {
                val delta = SystemClock.elapsedRealtime() - lastPositionUpdateTime
                base + (delta * playbackSpeed).toLong()
            } else {
                base
            }
            return if (durationMs > 0L) advanced.coerceIn(0L, durationMs) else advanced
        }
    }
}

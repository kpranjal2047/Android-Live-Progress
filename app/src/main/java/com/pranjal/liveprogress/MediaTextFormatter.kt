package com.pranjal.liveprogress

import java.util.Locale

object MediaTextFormatter {
    private const val PILL_LENGTH = 7
    private const val UNKNOWN_ARTIST = "Unknown Artist"
    private const val UNKNOWN_ALBUM = "Unknown Album"
    private const val MAX_DETAIL_LENGTH = 70
    private const val ELLIPSIS = "..."

    fun detailText(
        artist: String,
        album: String,
        showArtist: Boolean,
        showAlbum: Boolean
    ): String? {
        val parts = mutableListOf<String>()
        if (showArtist && artist.isUseful(UNKNOWN_ARTIST)) parts.add(artist.trim())
        if (showAlbum && album.isUseful(UNKNOWN_ALBUM)) parts.add(album.trim())
        val result = parts.joinToString(" - ")
        return result.takeIf { it.isNotBlank() }?.let {
            it.truncateWithEllipsis(MAX_DETAIL_LENGTH)
        }
    }

    fun subText(
        provider: String,
        showProvider: Boolean,
        showTimestamp: Boolean,
        positionMs: Long,
        durationMs: Long
    ): String? {
        val parts = mutableListOf<String>()
        if (showProvider && provider.isNotBlank()) parts.add(provider)
        if (showTimestamp) parts.add(timelineText(positionMs, durationMs))
        return parts.joinToString(" - ").takeIf { it.isNotBlank() }
    }

    fun aodSubText(
        provider: String,
        positionMs: Long,
        durationMs: Long
    ): String? {
        val parts = mutableListOf<String>()
        if (provider.isNotBlank()) parts.add(provider)
        parts.add(timelineText(positionMs, durationMs))
        return parts.joinToString(" - ").takeIf { it.isNotBlank() }
    }

    fun shortCriticalText(
        title: String,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        mode: MediaPillMode,
        scrollTitle: Boolean,
        titleElapsedMs: Long
    ): String {
        val canShowTime = isPlaying && durationMs > 0L
        if (mode == MediaPillMode.ELAPSED && canShowTime) return formatTime(positionMs)
        if (mode == MediaPillMode.REMAINING && canShowTime) {
            return "-${formatTime((durationMs - positionMs).coerceAtLeast(0L))}"
        }

        val trimmed = title.trim().ifBlank { "Media" }
        if (!scrollTitle || trimmed.length <= PILL_LENGTH) {
            return trimmed.truncateWithEllipsis(PILL_LENGTH)
        }
        return scrollingWindow(trimmed, titleElapsedMs)
    }

    private fun timelineText(
        positionMs: Long,
        durationMs: Long
    ): String {
        if (durationMs <= 0L) return formatTime(positionMs)
        return "${formatTime(positionMs)} / ${formatTime(durationMs)}"
    }

    fun formatTime(millis: Long): String {
        if (millis <= 0L) return "0:00"
        val totalSeconds = millis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private fun scrollingWindow(title: String, titleElapsedMs: Long): String {
        val scrollRange = title.length - PILL_LENGTH
        val waitAtStartSteps = 2
        val waitAtEndSteps = 2
        val cycleSteps = waitAtStartSteps + scrollRange + waitAtEndSteps
        val step = ((titleElapsedMs / 500L) % cycleSteps).toInt()
        val offset = when {
            step < waitAtStartSteps -> 0
            step < waitAtStartSteps + scrollRange -> step - waitAtStartSteps
            else -> scrollRange
        }
        return title.substring(offset, offset + PILL_LENGTH)
    }

    private fun String.isUseful(unknownValue: String): Boolean {
        val value = trim()
        return value.isNotBlank() && !value.equals(unknownValue, ignoreCase = true)
    }

    private fun String.truncateWithEllipsis(maxLength: Int): String {
        if (length <= maxLength) return this
        if (maxLength <= ELLIPSIS.length) return ELLIPSIS.take(maxLength)
        return take(maxLength - ELLIPSIS.length).trimEnd() + ELLIPSIS
    }
}

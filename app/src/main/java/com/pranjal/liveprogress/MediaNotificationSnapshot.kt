package com.pranjal.liveprogress

data class MediaNotificationSnapshot(
    val title: String,
    val contentText: String?,
    val subText: String?,
    val shortCriticalText: String?,
    val isPlaying: Boolean,
    val positionSecond: Long,
    val durationSecond: Long,
    val aodVisible: Boolean,
    val showShortCriticalText: Boolean,
    val priorityMode: MirrorPriorityMode,
    val sourceKey: String?,
    val appLabel: String,
    val artworkKey: String?
) {
    companion object {
        fun from(
            state: MediaState,
            source: MediaNotificationSource?,
            preferences: MediaPreferences,
            appLabel: String,
            titleElapsedMs: Long,
            aodVisible: Boolean,
            showShortCriticalText: Boolean,
            priorityMode: MirrorPriorityMode
        ): MediaNotificationSnapshot {
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

            return MediaNotificationSnapshot(
                title = state.title,
                contentText = contentText,
                subText = subText,
                shortCriticalText = shortCriticalText,
                isPlaying = state.isPlaying,
                positionSecond = state.positionMs / 1_000L,
                durationSecond = state.durationMs / 1_000L,
                aodVisible = aodVisible,
                showShortCriticalText = showShortCriticalText,
                priorityMode = priorityMode,
                sourceKey = source?.original?.key,
                appLabel = appLabel,
                artworkKey = artworkKey(state)
            )
        }

        private fun artworkKey(state: MediaState): String? {
            state.albumArt?.let { return "bitmap:${System.identityHashCode(it)}" }
            return state.albumArtUri?.toString()
        }
    }
}

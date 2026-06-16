package com.pranjal.liveprogress

object MediaUpdateScheduler {
    const val SCROLL_UPDATE_MS = 500L
    const val TIME_UPDATE_MS = 1_000L

    fun nextDelayMs(
        showMirror: Boolean,
        isPlaying: Boolean,
        title: String,
        pillMode: MediaPillMode,
        scrollTitle: Boolean,
        aodVisible: Boolean,
        showShortCriticalText: Boolean,
        expandedTimelineVisible: Boolean = false
    ): Long? {
        if (!showMirror || !isPlaying) return null

        val titleIsScrolling = showShortCriticalText &&
            scrollTitle &&
            pillMode == MediaPillMode.TITLE &&
            title.trim().length > PILL_LENGTH
        if (titleIsScrolling) return SCROLL_UPDATE_MS

        val visibleTimeText = aodVisible ||
            expandedTimelineVisible ||
            (showShortCriticalText && pillMode != MediaPillMode.TITLE)
        return if (visibleTimeText) TIME_UPDATE_MS else null
    }

    private const val PILL_LENGTH = 7
}

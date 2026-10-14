package com.pranjal.liveprogress

object MediaUpdateScheduler {
    const val TIME_UPDATE_MS = 1_000L

    fun nextDelayMs(
        showMirror: Boolean,
        isPlaying: Boolean,
        criticalText: String,
        timePillVisible: Boolean,
        aodVisible: Boolean,
        showShortCriticalText: Boolean,
        expandedTimelineVisible: Boolean = false
    ): Long? {
        if (!showMirror) return null

        if (showShortCriticalText && PillTextScroller.needsScrolling(criticalText)) {
            return PillTextScroller.UPDATE_MS
        }
        if (!isPlaying) return null

        val visibleTimeText = aodVisible ||
            expandedTimelineVisible ||
            (showShortCriticalText && timePillVisible)
        return if (visibleTimeText) TIME_UPDATE_MS else null
    }

}

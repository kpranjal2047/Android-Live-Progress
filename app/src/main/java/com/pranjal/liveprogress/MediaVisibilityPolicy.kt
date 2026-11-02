package com.pranjal.liveprogress

data class MediaVisibilityDecision(
    val showMirror: Boolean,
    val suppressOriginal: Boolean,
    val aodVisible: Boolean,
    val showShortCriticalText: Boolean,
    val reason: String
)

object MediaVisibilityPolicy {
    fun decide(
        mediaEnabled: Boolean,
        hasActiveMedia: Boolean,
        locked: Boolean,
        screenOff: Boolean,
        quickSettingsExpanded: Boolean,
        hideWhenQuickSettingsExpanded: Boolean = true,
        hideWhenSourceAppInForeground: Boolean = true,
        sourceAppInForeground: Boolean = false,
        progressMirrorActive: Boolean,
        showOnAod: Boolean,
        hideOriginalNotification: Boolean = false
    ): MediaVisibilityDecision {
        if (!mediaEnabled) return hidden("media live notifications disabled")
        if (!hasActiveMedia) return hidden("no active media")
        if (progressMirrorActive) return hidden("progress mirror active")

        if (screenOff && !showOnAod) {
            return hidden("media AOD disabled")
        }

        if (screenOff) {
            return MediaVisibilityDecision(
                showMirror = true,
                suppressOriginal = hideOriginalNotification,
                aodVisible = true,
                showShortCriticalText = true,
                reason = "screen off; mirror required"
            )
        }

        if (!locked && hideWhenSourceAppInForeground && sourceAppInForeground) {
            return hidden("source app foreground")
        }

        if (!locked && quickSettingsExpanded && hideWhenQuickSettingsExpanded) {
            return hidden("quick settings expanded")
        }

        if (locked) return hidden("awake lock screen uses original media")

        return MediaVisibilityDecision(
            showMirror = true,
            suppressOriginal = false,
            aodVisible = false,
            showShortCriticalText = true,
            reason = "status bar critical text"
        )
    }

    private fun hidden(reason: String) = MediaVisibilityDecision(
        showMirror = false,
        suppressOriginal = false,
        aodVisible = false,
        showShortCriticalText = false,
        reason = reason
    )
}

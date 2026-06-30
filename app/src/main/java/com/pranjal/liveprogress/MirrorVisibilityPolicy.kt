package com.pranjal.liveprogress

object MirrorVisibilityPolicy {
    fun shouldShow(
        locked: Boolean,
        quickSettingsExpanded: Boolean,
        hideWhenQuickSettingsExpanded: Boolean,
        sourceAppInForeground: Boolean = false
    ): Boolean {
        return locked || (!sourceAppInForeground &&
            (!hideWhenQuickSettingsExpanded || !quickSettingsExpanded))
    }
}

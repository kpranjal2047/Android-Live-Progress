package com.pranjal.liveprogress

object MirrorVisibilityPolicy {
    fun shouldShow(
        locked: Boolean,
        quickSettingsExpanded: Boolean,
        hideWhenQuickSettingsExpanded: Boolean,
        hideWhenSourceAppInForeground: Boolean = true,
        sourceAppInForeground: Boolean = false
    ): Boolean {
        return locked || ((!hideWhenSourceAppInForeground || !sourceAppInForeground) &&
            (!hideWhenQuickSettingsExpanded || !quickSettingsExpanded))
    }
}

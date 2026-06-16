package com.pranjal.liveprogress

object MirrorVisibilityPolicy {
    fun shouldShow(
        locked: Boolean,
        quickSettingsExpanded: Boolean,
        hideWhenQuickSettingsExpanded: Boolean
    ): Boolean {
        return locked || !hideWhenQuickSettingsExpanded || !quickSettingsExpanded
    }
}

package com.pranjal.liveprogress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorVisibilityPolicyTest {
    @Test
    fun hidesUnlockedMirrorWhenQuickSettingsExpandedAndSettingOn() {
        assertFalse(
            MirrorVisibilityPolicy.shouldShow(
                locked = false,
                quickSettingsExpanded = true,
                hideWhenQuickSettingsExpanded = true
            )
        )
    }

    @Test
    fun showsUnlockedMirrorWhenQuickSettingsExpandedAndSettingOff() {
        assertTrue(
            MirrorVisibilityPolicy.shouldShow(
                locked = false,
                quickSettingsExpanded = true,
                hideWhenQuickSettingsExpanded = false
            )
        )
    }

    @Test
    fun showsLockedMirrorEvenWhenQuickSettingsExpanded() {
        assertTrue(
            MirrorVisibilityPolicy.shouldShow(
                locked = true,
                quickSettingsExpanded = true,
                hideWhenQuickSettingsExpanded = true
            )
        )
    }

    @Test
    fun hidesUnlockedMirrorWhenSourceAppIsForeground() {
        assertFalse(
            MirrorVisibilityPolicy.shouldShow(
                locked = false,
                quickSettingsExpanded = false,
                hideWhenQuickSettingsExpanded = false,
                sourceAppInForeground = true
            )
        )
    }

    @Test
    fun showsLockedMirrorEvenWhenSourceAppIsForeground() {
        assertTrue(
            MirrorVisibilityPolicy.shouldShow(
                locked = true,
                quickSettingsExpanded = false,
                hideWhenQuickSettingsExpanded = false,
                sourceAppInForeground = true
            )
        )
    }
}

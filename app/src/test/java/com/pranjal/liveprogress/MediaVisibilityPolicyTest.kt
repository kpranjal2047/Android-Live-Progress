package com.pranjal.liveprogress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaVisibilityPolicyTest {
    @Test
    fun hidesWhenMediaIsDisabled() {
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = false,
            hasActiveMedia = true,
            locked = false,
            screenOff = false,
            quickSettingsExpanded = false,
            progressMirrorActive = false,
            showOnAod = true,
            showOnLockScreen = false
        )
        assertFalse(decision.showMirror)
        assertFalse(decision.suppressOriginal)
        assertFalse(decision.showShortCriticalText)
    }

    @Test
    fun showsUnlockedForStatusBarCriticalText() {
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = true,
            hasActiveMedia = true,
            locked = false,
            screenOff = false,
            quickSettingsExpanded = false,
            progressMirrorActive = false,
            showOnAod = true,
            showOnLockScreen = false
        )
        assertTrue(decision.showMirror)
        assertFalse(decision.suppressOriginal)
        assertTrue(decision.showShortCriticalText)
    }

    @Test
    fun hidesOnScreenOnLockScreen() {
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = true,
            hasActiveMedia = true,
            locked = true,
            screenOff = false,
            quickSettingsExpanded = false,
            progressMirrorActive = false,
            showOnAod = true,
            showOnLockScreen = false
        )
        assertFalse(decision.showMirror)
        assertFalse(decision.suppressOriginal)
        assertFalse(decision.showShortCriticalText)
    }

    @Test
    fun lockScreenToggleShowsMirrorWithoutSuppressingOriginalByDefault() {
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = true,
            hasActiveMedia = true,
            locked = true,
            screenOff = false,
            quickSettingsExpanded = false,
            progressMirrorActive = false,
            showOnAod = true,
            showOnLockScreen = true
        )
        assertTrue(decision.showMirror)
        assertFalse(decision.suppressOriginal)
        assertFalse(decision.aodVisible)
        assertTrue(decision.showShortCriticalText)
    }

    @Test
    fun screenOffShowsMirrorWithoutSuppressingOriginalByDefault() {
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = true,
            hasActiveMedia = true,
            locked = true,
            screenOff = true,
            quickSettingsExpanded = false,
            progressMirrorActive = false,
            showOnAod = true,
            showOnLockScreen = false
        )
        assertTrue(decision.showMirror)
        assertFalse(decision.suppressOriginal)
        assertTrue(decision.aodVisible)
        assertTrue(decision.showShortCriticalText)
    }

    @Test
    fun hidesWhileQuickSettingsIsExpandedWhenConfigured() {
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = true,
            hasActiveMedia = true,
            locked = false,
            screenOff = false,
            quickSettingsExpanded = true,
            progressMirrorActive = false,
            showOnAod = true,
            showOnLockScreen = false
        )
        assertFalse(decision.showMirror)
        assertFalse(decision.suppressOriginal)
        assertFalse(decision.showShortCriticalText)
    }

    @Test
    fun showsWhileQuickSettingsIsExpandedWhenConfiguredOff() {
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = true,
            hasActiveMedia = true,
            locked = false,
            screenOff = false,
            quickSettingsExpanded = true,
            hideWhenQuickSettingsExpanded = false,
            progressMirrorActive = false,
            showOnAod = true,
            showOnLockScreen = false
        )
        assertTrue(decision.showMirror)
        assertFalse(decision.suppressOriginal)
        assertTrue(decision.showShortCriticalText)
    }

    @Test
    fun progressActiveHidesUnlockedMediaMirror() {
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = true,
            hasActiveMedia = true,
            locked = false,
            screenOff = false,
            quickSettingsExpanded = false,
            progressMirrorActive = true,
            showOnAod = true,
            showOnLockScreen = false
        )
        assertFalse(decision.showMirror)
        assertFalse(decision.suppressOriginal)
        assertFalse(decision.aodVisible)
        assertFalse(decision.showShortCriticalText)
    }

    @Test
    fun progressActiveHidesMediaMirrorOnScreenOff() {
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = true,
            hasActiveMedia = true,
            locked = true,
            screenOff = true,
            quickSettingsExpanded = false,
            progressMirrorActive = true,
            showOnAod = true,
            showOnLockScreen = false
        )
        assertFalse(decision.showMirror)
        assertFalse(decision.suppressOriginal)
        assertFalse(decision.showShortCriticalText)
    }

    @Test
    fun progressActiveStillHidesMediaMirrorWhenQuickSettingsIsExpanded() {
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = true,
            hasActiveMedia = true,
            locked = false,
            screenOff = false,
            quickSettingsExpanded = true,
            progressMirrorActive = true,
            showOnAod = true,
            showOnLockScreen = false
        )
        assertFalse(decision.showMirror)
        assertFalse(decision.suppressOriginal)
        assertFalse(decision.showShortCriticalText)
    }

    @Test
    fun screenOffHidesMirrorWhenAodIsDisabled() {
        val decision = MediaVisibilityPolicy.decide(
            mediaEnabled = true,
            hasActiveMedia = true,
            locked = true,
            screenOff = true,
            quickSettingsExpanded = false,
            progressMirrorActive = false,
            showOnAod = false,
            showOnLockScreen = false
        )
        assertFalse(decision.showMirror)
        assertFalse(decision.suppressOriginal)
        assertFalse(decision.showShortCriticalText)
    }
}

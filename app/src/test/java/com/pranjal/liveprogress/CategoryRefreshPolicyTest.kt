package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRefreshPolicyTest {
    @Test
    fun hidesRefreshWhenShizukuIsNotInstalled() {
        assertEquals(
            CategoryRefreshAction.HIDDEN,
            CategoryRefreshPolicy.actionFor(shizukuInstalled = false, shizukuAvailable = false, shizukuGranted = false)
        )
    }

    @Test
    fun opensSetupBeforeConfirmationWhenShizukuIsUngranted() {
        assertEquals(
            CategoryRefreshAction.OPEN_SHIZUKU_SETUP,
            CategoryRefreshPolicy.actionFor(shizukuInstalled = true, shizukuAvailable = true, shizukuGranted = false)
        )
    }

    @Test
    fun confirmsOnlyWhenShizukuPermissionIsGranted() {
        assertEquals(
            CategoryRefreshAction.CONFIRM_REFRESH,
            CategoryRefreshPolicy.actionFor(shizukuInstalled = true, shizukuAvailable = true, shizukuGranted = true)
        )
    }

    @Test
    fun computesRefreshPercentageFromAppsScanned() {
        assertEquals(0, CategoryRefreshProgress(0, 12).percentage)
        assertEquals(50, CategoryRefreshProgress(6, 12).percentage)
        assertEquals(100, CategoryRefreshProgress(12, 12).percentage)
        assertEquals(100, CategoryRefreshProgress(0, 0).percentage)
    }

    @Test
    fun onlyReleasesAssistantWhenTheRefreshStartedIt() {
        assertTrue(CategoryRefreshAssistantReleasePolicy.shouldRelease(false))
        assertFalse(CategoryRefreshAssistantReleasePolicy.shouldRelease(true))
    }
    @Test fun installedButStoppedShizukuOpensSetup() {
        assertEquals(CategoryRefreshAction.OPEN_SHIZUKU_SETUP,
            CategoryRefreshPolicy.actionFor(true, false, false))
    }
    @Test fun uninstallOverridesStaleGrantedState() {
        assertEquals(CategoryRefreshAction.HIDDEN,
            CategoryRefreshPolicy.actionFor(false, true, true))
    }
    @Test fun accessLostAfterConfirmationReturnsToSetup() {
        assertEquals(CategoryRefreshAction.CONFIRM_REFRESH, CategoryRefreshPolicy.actionFor(true, true, true))
        assertEquals(CategoryRefreshAction.OPEN_SHIZUKU_SETUP, CategoryRefreshPolicy.actionFor(true, true, false))
    }
}

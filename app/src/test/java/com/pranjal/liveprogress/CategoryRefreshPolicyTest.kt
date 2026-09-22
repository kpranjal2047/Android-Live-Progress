package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRefreshPolicyTest {
    @Test
    fun hidesRefreshWhenShizukuIsUnavailable() {
        assertEquals(
            CategoryRefreshAction.HIDDEN,
            CategoryRefreshPolicy.actionFor(shizukuAvailable = false, shizukuGranted = false)
        )
    }

    @Test
    fun requestsPermissionBeforeConfirmationWhenShizukuIsUngranted() {
        assertEquals(
            CategoryRefreshAction.REQUEST_SHIZUKU_PERMISSION,
            CategoryRefreshPolicy.actionFor(shizukuAvailable = true, shizukuGranted = false)
        )
    }

    @Test
    fun confirmsOnlyWhenShizukuPermissionIsGranted() {
        assertEquals(
            CategoryRefreshAction.CONFIRM_REFRESH,
            CategoryRefreshPolicy.actionFor(shizukuAvailable = true, shizukuGranted = true)
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
}

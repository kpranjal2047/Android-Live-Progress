package com.pranjal.liveprogress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunCategoryRefreshPolicyTest {
    @Test
    fun startsOnlyForAReadyInitialOnboardingWithShizuku() {
        assertTrue(
            FirstRunCategoryRefreshPolicy.shouldStart(
                initialOnboarding = true,
                refreshHandled = false,
                shizukuGranted = true,
                manualSetupComplete = true
            )
        )
        assertFalse(
            FirstRunCategoryRefreshPolicy.shouldStart(
                initialOnboarding = false,
                refreshHandled = false,
                shizukuGranted = true,
                manualSetupComplete = true
            )
        )
        assertFalse(
            FirstRunCategoryRefreshPolicy.shouldStart(
                initialOnboarding = true,
                refreshHandled = false,
                shizukuGranted = false,
                manualSetupComplete = true
            )
        )
    }

    @Test
    fun unavailableOrSkippedShizukuConsumesTheInitialRefresh() {
        assertTrue(
            FirstRunCategoryRefreshPolicy.shouldMarkHandledWithoutRefresh(
                initialOnboarding = true,
                refreshHandled = false,
                shizukuAvailable = false,
                shizukuDeferred = false
            )
        )
        assertTrue(
            FirstRunCategoryRefreshPolicy.shouldMarkHandledWithoutRefresh(
                initialOnboarding = true,
                refreshHandled = false,
                shizukuAvailable = true,
                shizukuDeferred = true
            )
        )
        assertFalse(
            FirstRunCategoryRefreshPolicy.shouldMarkHandledWithoutRefresh(
                initialOnboarding = false,
                refreshHandled = false,
                shizukuAvailable = false,
                shizukuDeferred = false
            )
        )
    }
}

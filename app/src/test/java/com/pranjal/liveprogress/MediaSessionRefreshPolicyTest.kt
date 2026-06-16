package com.pranjal.liveprogress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionRefreshPolicyTest {
    @Test
    fun sourcePackageChangesAndInvalidControllerQuerySessions() {
        assertTrue(
            MediaSessionRefreshPolicy.shouldQuerySessions(
                hasCachedControllers = true,
                hasActiveController = true,
                sourcePackageChanged = true,
                currentControllerInvalid = false,
                explicitRefresh = false
            )
        )
        assertTrue(
            MediaSessionRefreshPolicy.shouldQuerySessions(
                hasCachedControllers = true,
                hasActiveController = true,
                sourcePackageChanged = false,
                currentControllerInvalid = true,
                explicitRefresh = false
            )
        )
    }

    @Test
    fun steadyStateUsesCachedController() {
        assertFalse(
            MediaSessionRefreshPolicy.shouldQuerySessions(
                hasCachedControllers = true,
                hasActiveController = true,
                sourcePackageChanged = false,
                currentControllerInvalid = false,
                explicitRefresh = false
            )
        )
    }

    @Test
    fun missingControllerWithoutCacheQueriesSessions() {
        assertTrue(
            MediaSessionRefreshPolicy.shouldQuerySessions(
                hasCachedControllers = false,
                hasActiveController = false,
                sourcePackageChanged = false,
                currentControllerInvalid = false,
                explicitRefresh = false
            )
        )
    }
}

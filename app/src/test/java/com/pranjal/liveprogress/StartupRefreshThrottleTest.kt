package com.pranjal.liveprogress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupRefreshThrottleTest {
    @Test
    fun disconnectedListenerSkipsRefresh() {
        assertFalse(
            StartupRefreshThrottle.shouldRefresh(
                listenerConnected = false,
                nowUptimeMs = 2_000L,
                lastRefreshUptimeMs = 0L,
                minRefreshIntervalMs = 1_000L
            )
        )
    }

    @Test
    fun connectedListenerRefreshesWhenNoRefreshHasRun() {
        assertTrue(
            StartupRefreshThrottle.shouldRefresh(
                listenerConnected = true,
                nowUptimeMs = 2_000L,
                lastRefreshUptimeMs = 0L,
                minRefreshIntervalMs = 1_000L
            )
        )
    }

    @Test
    fun recentRefreshSkipsStartupRefresh() {
        assertFalse(
            StartupRefreshThrottle.shouldRefresh(
                listenerConnected = true,
                nowUptimeMs = 2_500L,
                lastRefreshUptimeMs = 2_000L,
                minRefreshIntervalMs = 1_000L
            )
        )
    }
}

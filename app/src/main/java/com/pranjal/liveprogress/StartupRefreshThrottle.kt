package com.pranjal.liveprogress

object StartupRefreshThrottle {
    const val MIN_REFRESH_INTERVAL_MS = 1_000L

    fun shouldRefresh(
        listenerConnected: Boolean,
        nowUptimeMs: Long,
        lastRefreshUptimeMs: Long,
        minRefreshIntervalMs: Long = MIN_REFRESH_INTERVAL_MS
    ): Boolean {
        if (!listenerConnected) return false
        if (lastRefreshUptimeMs <= 0L) return true
        return nowUptimeMs - lastRefreshUptimeMs >= minRefreshIntervalMs
    }
}

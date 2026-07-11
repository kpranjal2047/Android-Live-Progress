package com.pranjal.liveprogress

object BatteryDiagnostics {
    private val lock = Any()
    private val counts = linkedMapOf<Counter, Long>()

    fun increment(counter: Counter) {
        synchronized(lock) {
            counts[counter] = (counts[counter] ?: 0L) + 1L
        }
    }

    fun reset() {
        synchronized(lock) {
            counts.clear()
        }
    }

    enum class Counter {
        MEDIA_SESSION_SCANS,
        MEDIA_REPOSTS,
        MEDIA_SKIPPED_REPOSTS,
        PROGRESS_REPOSTS,
        PROGRESS_SKIPPED_REPOSTS,
        STARTUP_REFRESH_SKIPS
    }
}

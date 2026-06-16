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

    fun summary(): String {
        val snapshot = synchronized(lock) { counts.toMap() }
        if (snapshot.isEmpty()) return "No battery counters yet"
        return Counter.entries.joinToString(separator = ", ") { counter ->
            "${counter.label}=${snapshot[counter] ?: 0L}"
        }
    }

    enum class Counter(val label: String) {
        MEDIA_SESSION_SCANS("media scans"),
        MEDIA_REPOSTS("media posts"),
        MEDIA_SKIPPED_REPOSTS("media skipped"),
        PROGRESS_REPOSTS("progress posts"),
        PROGRESS_SKIPPED_REPOSTS("progress skipped"),
        STARTUP_REFRESH_SKIPS("startup skips")
    }
}

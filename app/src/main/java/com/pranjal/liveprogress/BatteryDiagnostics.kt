package com.pranjal.liveprogress

import android.content.Context

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

    fun summary(context: Context): String {
        val snapshot = synchronized(lock) { counts.toMap() }
        if (snapshot.isEmpty()) return context.getString(R.string.battery_no_counters)
        return Counter.entries.joinToString(separator = ", ") { counter ->
            "${context.getString(counter.labelRes)}=${snapshot[counter] ?: 0L}"
        }
    }

    enum class Counter(val labelRes: Int) {
        MEDIA_SESSION_SCANS(R.string.battery_label_media_scans),
        MEDIA_REPOSTS(R.string.battery_label_media_posts),
        MEDIA_SKIPPED_REPOSTS(R.string.battery_label_media_skipped),
        PROGRESS_REPOSTS(R.string.battery_label_progress_posts),
        PROGRESS_SKIPPED_REPOSTS(R.string.battery_label_progress_skipped),
        STARTUP_REFRESH_SKIPS(R.string.battery_label_startup_skips)
    }
}

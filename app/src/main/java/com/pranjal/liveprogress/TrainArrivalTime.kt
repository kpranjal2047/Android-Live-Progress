package com.pranjal.liveprogress

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A fixed source snapshot: refreshing the pill never renews the source's freshness. */
data class TrainArrivalPill(
    val deadlineElapsedRealtime: Long?,
    val expiresAtElapsedRealtime: Long,
    val arrived: Boolean
) {
    fun text(nowElapsedRealtime: Long, strings: NotificationText): String {
        if (nowElapsedRealtime >= expiresAtElapsedRealtime) return ""
        if (arrived) return strings.get(R.string.train_arrived)
        val remaining = (deadlineElapsedRealtime ?: return "") - nowElapsedRealtime
        if (remaining <= 0L) return ""
        if (remaining < MINUTE_MILLIS) return strings.get(R.string.notification_under_minute)
        val minutes = (remaining + MINUTE_MILLIS - 1L) / MINUTE_MILLIS
        return when {
            minutes < 60L -> strings.get(R.string.notification_minutes_short, minutes)
            minutes % 60L == 0L -> strings.get(R.string.notification_hours_short, minutes / 60L)
            else -> strings.get(R.string.notification_hours_minutes_short, minutes / 60L, minutes % 60L)
        }
    }

    fun nextUpdateDelayMillis(nowElapsedRealtime: Long): Long? {
        val untilExpiry = expiresAtElapsedRealtime - nowElapsedRealtime
        if (untilExpiry <= 0L) return null
        if (arrived) return untilExpiry
        val remaining = (deadlineElapsedRealtime ?: return null) - nowElapsedRealtime
        if (remaining <= 0L) return null
        val untilTextChange = when {
            remaining < MINUTE_MILLIS -> remaining
            // At exactly one minute, switch to <1 min as soon as the boundary passes.
            remaining == MINUTE_MILLIS -> 1L
            remaining % MINUTE_MILLIS == 0L -> MINUTE_MILLIS
            else -> remaining % MINUTE_MILLIS
        }
        return minOf(untilTextChange, untilExpiry)
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

object TrainArrivalTime {
    private const val MINUTE_MILLIS = 60_000L
    private const val FRESHNESS_MILLIS = 30L * MINUTE_MILLIS
    private const val HALF_DAY_MILLIS = 12L * 60L * MINUTE_MILLIS
    private val clock = Regex("""(?i)^(\p{Nd}{1,2})\s*:\s*(\p{Nd}{2})\s*([ap])\s*m$""")
    private val arrival = Regex("""(?i)^(?:arrived|reached)(?: at)? (.+)$""")
    private val whitespace = Regex("""[\s\u00a0\u202f]+""")

    fun create(
        eta: CharSequence?,
        lastUpdatedTime: String?,
        currentStatus: CharSequence?,
        destination: CharSequence?,
        sourcePostTimeMillis: Long,
        nowWallTimeMillis: Long,
        nowElapsedRealtime: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): TrainArrivalPill? {
        if (sourcePostTimeMillis <= 0L || sourcePostTimeMillis > nowWallTimeMillis + MINUTE_MILLIS) return null
        if (nowWallTimeMillis - sourcePostTimeMillis >= FRESHNESS_MILLIS) return null
        val updatedClock = parseClock(lastUpdatedTime) ?: return null
        val posted = Instant.ofEpochMilli(sourcePostTimeMillis).atZone(zoneId)
        var updated = posted.toLocalDate().atTime(updatedClock).atZone(zoneId)
        // Date-less clocks refer to the source post, not the time a retained mirror is reread.
        if (updated.toInstant().toEpochMilli() > sourcePostTimeMillis + MINUTE_MILLIS) {
            updated = updated.minusDays(1)
        }
        val updatedMillis = minOf(updated.toInstant().toEpochMilli(), sourcePostTimeMillis, nowWallTimeMillis)
        val age = nowWallTimeMillis - updatedMillis
        if (age >= FRESHNESS_MILLIS) return null
        val expiry = nowElapsedRealtime + FRESHNESS_MILLIS - age
        val station = normalize(destination)
        val arrivedStation = arrival.matchEntire(normalize(currentStatus))?.groupValues?.get(1)
        if (station.isNotEmpty() && !station.endsWith("...") && !station.endsWith("…") &&
            arrivedStation.equals(station, ignoreCase = true)) {
            return TrainArrivalPill(null, expiry, arrived = true)
        }
        val etaClock = parseClock(eta) ?: return null
        var deadline = updated.toLocalDate().atTime(etaClock).atZone(zoneId)
        val difference = deadline.toInstant().toEpochMilli() - updated.toInstant().toEpochMilli()
        // Only wrap an overnight clock when it is more than half a day behind the update.
        // A recently passed ETA must not become tomorrow's arrival.
        if (difference < -HALF_DAY_MILLIS) deadline = deadline.plusDays(1)
        val journeyRemaining = deadline.toInstant().toEpochMilli() - updatedMillis
        if (journeyRemaining <= 0L) return null
        val remaining = deadline.toInstant().toEpochMilli() - nowWallTimeMillis
        if (remaining <= 0L) return null
        return TrainArrivalPill(nowElapsedRealtime + remaining, expiry, arrived = false)
    }

    private fun normalize(text: CharSequence?): String = text?.toString()
        ?.replace(whitespace, " ")?.trim().orEmpty()

    private fun parseClock(text: CharSequence?): LocalTime? {
        val match = clock.matchEntire(normalize(text)) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 1..12 || minute !in 0..59) return null
        val hour24 = hour % 12 + if (match.groupValues[3].equals("p", ignoreCase = true)) 12 else 0
        return LocalTime.of(hour24, minute)
    }
}

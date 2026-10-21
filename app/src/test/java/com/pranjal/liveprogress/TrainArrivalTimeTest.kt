package com.pranjal.liveprogress

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainArrivalTimeTest {
    private fun TrainArrivalPill.text(now: Long) = text(now, TestNotificationText.english)
    private val minute = 60_000L
    private val elapsed = 1_000_000L
    private fun wall(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun create(
        eta: String? = "12:18 PM",
        updated: String? = "12:00 PM",
        posted: String = "2026-10-21T12:00:00Z",
        now: String = posted,
        status: String = "Departed Central",
        destination: String? = "Terminal Junction",
        zone: String = "UTC"
    ) = TrainArrivalTime.create(
        eta, updated, status, destination, wall(posted), wall(now), elapsed, ZoneId.of(zone)
    )

    @Test
    fun countsDownWithoutChangingTheOriginalDeadline() {
        val pill = requireNotNull(create())
        assertEquals("18 min", pill.text(elapsed))
        assertEquals("17 min", pill.text(elapsed + minute))
        assertEquals("1 min", pill.text(elapsed + 17 * minute))
        assertEquals("<1 min", pill.text(elapsed + 17 * minute + 1))
        assertEquals("", pill.text(elapsed + 18 * minute))
        assertNull(pill.nextUpdateDelayMillis(elapsed + 18 * minute))
    }

    @Test
    fun usesCompactHoursAndRoundsMinutesUp() {
        assertEquals("1h 12m", create(eta = "1:12 PM")?.text(elapsed))
        assertEquals("1h", create(eta = "1:00 PM")?.text(elapsed))
        assertEquals("22h", create("11:00 PM", "1:00 AM", "2026-10-21T01:00:00Z")?.text(elapsed))
        assertEquals("2 min", TrainArrivalPill(elapsed + minute + 1, elapsed + 10 * minute, false).text(elapsed))
        assertEquals("<1 min", create(eta = "12:01 PM", now = "2026-10-21T12:00:30Z")?.text(elapsed))
    }

    @Test
    fun schedulesTextBoundariesAndStopsAtExpiry() {
        val pill = requireNotNull(create())
        assertEquals(minute, pill.nextUpdateDelayMillis(elapsed))
        assertEquals(minute - 1, pill.nextUpdateDelayMillis(elapsed + 1))
        assertEquals(1L, pill.nextUpdateDelayMillis(elapsed + 17 * minute))
        assertEquals(minute - 1, pill.nextUpdateDelayMillis(elapsed + 17 * minute + 1))
        val almostStale = TrainArrivalPill(elapsed + 60 * minute, elapsed + 7_000, false)
        assertEquals(7_000L, almostStale.nextUpdateDelayMillis(elapsed))
        assertEquals("", almostStale.text(elapsed + 7_000))
        assertNull(almostStale.nextUpdateDelayMillis(elapsed + 7_000))
    }

    @Test
    fun expiresThirtyMinutesAfterSourceUpdateEvenWithFutureEta() {
        val pill = requireNotNull(create(eta = "1:12 PM", updated = "11:40 AM"))
        assertEquals("1h 12m", pill.text(elapsed))
        assertEquals("", pill.text(elapsed + 10 * minute))
        assertNull(create(updated = "11:30 AM"))
        assertNull(create(posted = "2026-10-21T11:30:00Z", now = "2026-10-21T12:00:00Z"))
        // Re-extracting the same snapshot must not reset its freshness window.
        val reread = requireNotNull(create(eta = "1:12 PM", now = "2026-10-21T12:20:00Z"))
        assertEquals("", reread.text(elapsed + 10 * minute))
    }

    @Test
    fun resolvesMidnightUsingSourceUpdateDate() {
        val posted = "2026-10-21T23:50:00Z"
        assertEquals("20 min", create("12:10 AM", "11:50 PM", posted)?.text(elapsed))
        assertEquals("5 min", create("12:10 AM", "11:50 PM", posted, "2026-10-22T00:05:00Z")?.text(elapsed))
        assertNull(create("12:10 AM", "11:50 PM", posted, "2026-10-22T00:11:00Z"))
        // Source was posted after midnight, but its last-update clock belongs to yesterday.
        assertEquals("10 min", create("12:10 AM", "11:50 PM", "2026-10-22T00:00:00Z")?.text(elapsed))
    }

    @Test
    fun neverRollsARecentlyExpiredEtaForwardToTomorrow() {
        assertNull(create(eta = "11:59 AM"))
        assertNull(create(eta = "12:00 PM"))
        assertNull(create(now = "2026-10-21T12:19:00Z"))
        assertNull(create(eta = "1:00 AM")) // A nearby past clock cannot safely be assigned to tomorrow.
    }

    @Test
    fun validatesClockTextAndRequiresSourceFreshnessEvidence() {
        for (eta in listOf(null, "", "13:18 PM", "12:60 PM", "0:15 AM", "12:18", "soon")) {
            assertNull(create(eta = eta))
        }
        assertNull(create(updated = null))
        assertNull(create(updated = "yesterday"))
        assertNull(create(updated = "12:05 PM"))
        assertNull(create(posted = "2026-10-21T12:05:00Z", now = "2026-10-21T12:00:00Z"))
        assertEquals("18 min", create(eta = " 12 : 18 p m ")?.text(elapsed))
    }

    @Test
    fun usesProvidedTimeZoneAndCorrectNoonAndMidnight() {
        assertEquals("18 min", create(posted = "2026-10-21T06:30:00Z", zone = "Asia/Kolkata")?.text(elapsed))
        assertEquals("10 min", create("12:10 PM", "12:00 PM")?.text(elapsed))
        assertEquals("10 min", create("12:10 AM", "12:00 AM", "2026-10-21T00:00:00Z")?.text(elapsed))
    }

    @Test
    fun onlyDestinationArrivalCanShowArrivedAndItAlsoExpires() {
        val arrived = requireNotNull(create(eta = null, status = "Arrived at Terminal Junction"))
        assertEquals("Arrived", arrived.text(elapsed))
        assertEquals(30 * minute, arrived.nextUpdateDelayMillis(elapsed))
        assertEquals("", arrived.text(elapsed + 30 * minute))
        assertNull(arrived.nextUpdateDelayMillis(elapsed + 30 * minute))
        assertEquals("Arrived", create(eta = null, status = "Reached terminal junction")?.text(elapsed))
        assertNull(create(eta = null, status = "Arrived at Central"))
        assertNull(create(eta = null, status = "Approaching Terminal Junction"))
        assertNull(create(eta = null, status = "Arrived at Terminal...", destination = "Terminal..."))
        assertNull(create(eta = null, status = "Arrived", destination = null))
    }

    @Test
    fun changedEtaReplacesDeadlineAndExpiredDataDoesNotBecomeArrival() {
        val original = requireNotNull(create(eta = "12:05 PM"))
        val revised = requireNotNull(create(eta = "12:15 PM", updated = "12:04 PM", posted = "2026-10-21T12:04:00Z"))
        assertEquals("", original.text(elapsed + 5 * minute))
        assertEquals("11 min", revised.text(elapsed))
        assertEquals("", revised.text(elapsed + 11 * minute))
    }
}

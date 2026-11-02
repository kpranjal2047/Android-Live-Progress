package com.pranjal.liveprogress

import org.junit.Assert.*
import org.junit.Test

class HotspotPollSessionTest {
    @Test fun unrelatedRefreshesKeepCountsAndDoNotRestartPolling() {
        val session = HotspotPollSession()
        assertTrue(session.updateEligibility(true))
        val generation = session.generation
        assertTrue(session.complete(generation, 3))
        repeat(20) { assertFalse(session.updateEligibility(true)) }
        assertEquals(generation, session.generation)
        assertEquals(3, session.count)
    }
    @Test fun accessLossOrHiddenCardInvalidatesInFlightResult() {
        val session = HotspotPollSession()
        session.updateEligibility(true)
        val generation = session.generation
        session.complete(generation, 2)
        session.updateEligibility(false)
        assertNull(session.count)
        assertFalse(session.complete(generation, 8))
        session.updateEligibility(true)
        assertFalse(session.complete(generation, 8))
        assertTrue(session.complete(session.generation, 1))
        assertEquals(1, session.count)
    }
    @Test fun failedReadClearsOldCountAndInactiveSessionNeverAcceptsResults() {
        val session = HotspotPollSession()
        assertFalse(session.complete(0, 3))
        session.updateEligibility(true)
        session.complete(session.generation, 3)
        assertTrue(session.complete(session.generation, null))
        assertNull(session.count)
    }

    @Test fun readsCannotStartWithoutEligibilityOrOverlap() {
        val session = HotspotPollSession()
        assertNull(session.beginRead())
        session.updateEligibility(true)
        val generation = session.beginRead()!!
        repeat(20) { assertNull(session.beginRead()) }
        assertTrue(session.reading)
        assertTrue(session.complete(generation, 2))
        assertFalse(session.reading)
        assertNotNull(session.beginRead())
    }
    @Test fun revokeAndRegrantWhileReadingDoesNotStartAnOverlappingRead() {
        val session = HotspotPollSession()
        session.updateEligibility(true)
        val stale = session.beginRead()!!
        session.updateEligibility(false)
        session.updateEligibility(true)
        assertNull(session.beginRead())
        assertFalse(session.complete(stale, 7))
        assertNull(session.count)
        val fresh = session.beginRead()!!
        assertTrue(session.complete(fresh, 1))
        assertEquals(1, session.count)
    }
    @Test fun disconnectFailureAndReconnectionNeverReusePreviousCounts() {
        val session = HotspotPollSession()
        repeat(10) {
            session.updateEligibility(true)
            assertTrue(session.complete(session.beginRead()!!, 3))
            assertTrue(session.complete(session.beginRead()!!, null))
            assertNull(session.count)
            session.updateEligibility(false)
            assertNull(session.count)
            assertNull(session.beginRead())
        }
    }
}

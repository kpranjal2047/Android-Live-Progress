package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Test

class HotspotStopSessionTest {
    @Test fun successfulShutdownFinishesWithoutOpeningSettings() {
        assertEquals(HotspotStopSession.Effect.FINISH, HotspotStopSession().accept(HotspotStopSession.Result.SUCCEEDED))
    }
    @Test fun rejectionTimeoutAndContentTapOpenSettings() {
        for (result in listOf(HotspotStopSession.Result.FAILED, HotspotStopSession.Result.TIMED_OUT, HotspotStopSession.Result.OPEN_SETTINGS)) {
            assertEquals(HotspotStopSession.Effect.OPEN_SETTINGS, HotspotStopSession().accept(result))
        }
    }
    @Test fun everyCallbackOrderingProducesOnlyOneEffect() {
        for (first in HotspotStopSession.Result.entries) for (later in HotspotStopSession.Result.entries) {
            val session = HotspotStopSession()
            session.accept(first)
            assertEquals("$first then $later", HotspotStopSession.Effect.NONE, session.accept(later))
        }
    }
    @Test fun destroyedActivityIgnoresAllLateCallbacksAndTimeouts() {
        val session = HotspotStopSession()
        session.cancel()
        for (result in HotspotStopSession.Result.entries) assertEquals(HotspotStopSession.Effect.NONE, session.accept(result))
    }
}

package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUiVisibilityCounterTest {
    @Test
    fun appUiExitsOnlyAfterAllActivitiesStop() {
        val counter = AppUiVisibilityCounter()

        counter.onActivityStarted()
        counter.onActivityStarted()
        assertFalse(counter.isAppUiExited())

        assertEquals(1, counter.onActivityStopped())
        assertFalse(counter.isAppUiExited())

        assertEquals(0, counter.onActivityStopped())
        assertTrue(counter.isAppUiExited())
    }

    @Test
    fun extraStopsDoNotMakeCountNegative() {
        val counter = AppUiVisibilityCounter()

        assertEquals(0, counter.onActivityStopped())
        assertTrue(counter.isAppUiExited())
    }
}

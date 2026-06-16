package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaProgressMapperTest {
    @Test
    fun mapsUnknownDurationAsIndeterminate() {
        val progress = MediaProgressMapper.map(positionMs = 1_000, durationMs = 0)
        assertTrue(progress.indeterminate)
        assertEquals(0, progress.max)
        assertEquals(0, progress.progress)
    }

    @Test
    fun clampsPositionWithinDuration() {
        val progress = MediaProgressMapper.map(positionMs = 150_000, durationMs = 100_000)
        assertFalse(progress.indeterminate)
        assertEquals(100_000, progress.max)
        assertEquals(100_000, progress.progress)
    }

    @Test
    fun scalesVeryLongDurations() {
        val progress = MediaProgressMapper.map(
            positionMs = 5_000_000_000,
            durationMs = 10_000_000_000
        )
        assertFalse(progress.indeterminate)
        assertEquals(Int.MAX_VALUE, progress.max)
        assertEquals(Int.MAX_VALUE / 2, progress.progress)
    }
}

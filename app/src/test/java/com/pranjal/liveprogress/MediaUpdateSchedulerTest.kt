package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUpdateSchedulerTest {
    @Test
    fun pausedLongTitleStillScrollsButHiddenTextNeverTicks() {
        assertEquals(PillTextScroller.UPDATE_MS, MediaUpdateScheduler.nextDelayMs(
            showMirror = true, isPlaying = false, criticalText = "Long paused title",
            timePillVisible = false, aodVisible = false, showShortCriticalText = true
        ))
        assertNull(MediaUpdateScheduler.nextDelayMs(
            showMirror = false, isPlaying = true, criticalText = "Long hidden title",
            timePillVisible = false, aodVisible = false, showShortCriticalText = true
        ))
        assertNull(MediaUpdateScheduler.nextDelayMs(
            showMirror = true, isPlaying = false, criticalText = "Long hidden title",
            timePillVisible = false, aodVisible = false, showShortCriticalText = false
        ))
    }

    @Test
    fun longTimesAndUnknownDurationTitleFallbackScroll() {
        for (mode in MediaPillMode.entries) {
            val text = MediaTextFormatter.shortCriticalText(
                fallbackTitle = "Media",
                title = "Long fallback title", positionMs = 0, durationMs = 0,
                isPlaying = true, mode = mode
            )
            assertEquals(PillTextScroller.UPDATE_MS, MediaUpdateScheduler.nextDelayMs(
                showMirror = true, isPlaying = true, criticalText = text,
                timePillVisible = false, aodVisible = false, showShortCriticalText = true
            ))
        }
        assertEquals(PillTextScroller.UPDATE_MS, MediaUpdateScheduler.nextDelayMs(
            showMirror = true, isPlaying = true, criticalText = "-10:00:00",
            timePillVisible = true, aodVisible = false, showShortCriticalText = true
        ))
        assertEquals(MediaUpdateScheduler.TIME_UPDATE_MS, MediaUpdateScheduler.nextDelayMs(
            showMirror = true, isPlaying = true, criticalText = "1:00:00",
            timePillVisible = true, aodVisible = false, showShortCriticalText = true
        ))
    }

    @Test
    fun shortTitleTicksWhenExpandedTimelineIsVisible() {
        assertEquals(
            MediaUpdateScheduler.TIME_UPDATE_MS,
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = true,
                criticalText = "Track",
                timePillVisible = false,
                aodVisible = false,
                showShortCriticalText = true,
                expandedTimelineVisible = true
            )
        )
    }

    @Test
    fun shortTitleDoesNotTickWhenNoTimeTextIsVisible() {
        assertNull(
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = true,
                criticalText = "Track",
                timePillVisible = false,
                aodVisible = false,
                showShortCriticalText = true
            )
        )
    }

    @Test
    fun elapsedAndRemainingModesTickWhenVisible() {
        assertEquals(
            MediaUpdateScheduler.TIME_UPDATE_MS,
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = true,
                criticalText = "Track",
                timePillVisible = true,
                aodVisible = false,
                showShortCriticalText = true
            )
        )
        assertEquals(
            MediaUpdateScheduler.TIME_UPDATE_MS,
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = true,
                criticalText = "Track",
                timePillVisible = true,
                aodVisible = false,
                showShortCriticalText = true
            )
        )
    }

    @Test
    fun aodTicksEvenWhenStatusBarTitleModeIsSelected() {
        assertEquals(
            MediaUpdateScheduler.TIME_UPDATE_MS,
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = true,
                criticalText = "Track",
                timePillVisible = false,
                aodVisible = true,
                showShortCriticalText = true
            )
        )
    }

    @Test
    fun scrollingTitleTicksAtScrollCadence() {
        assertEquals(
            PillTextScroller.UPDATE_MS,
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = true,
                criticalText = "Very Long Track",
                timePillVisible = false,
                aodVisible = false,
                showShortCriticalText = true
            )
        )
    }

    @Test
    fun hiddenOrPausedShortTextDoesNotTick() {
        assertNull(
            MediaUpdateScheduler.nextDelayMs(
                showMirror = false,
                isPlaying = true,
                criticalText = "Track",
                timePillVisible = true,
                aodVisible = false,
                showShortCriticalText = true
            )
        )
        assertNull(
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = false,
                criticalText = "Track",
                timePillVisible = true,
                aodVisible = false,
                showShortCriticalText = true
            )
        )
    }
}

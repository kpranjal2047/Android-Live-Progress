package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUpdateSchedulerTest {
    @Test
    fun titleModeWithoutScrollingTicksWhenExpandedTimelineIsVisible() {
        assertEquals(
            MediaUpdateScheduler.TIME_UPDATE_MS,
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = true,
                title = "Track",
                pillMode = MediaPillMode.TITLE,
                scrollTitle = false,
                aodVisible = false,
                showShortCriticalText = true,
                expandedTimelineVisible = true
            )
        )
    }

    @Test
    fun titleModeWithoutScrollingDoesNotTickWhenNoTimeTextIsVisible() {
        assertNull(
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = true,
                title = "Track",
                pillMode = MediaPillMode.TITLE,
                scrollTitle = false,
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
                title = "Track",
                pillMode = MediaPillMode.ELAPSED,
                scrollTitle = false,
                aodVisible = false,
                showShortCriticalText = true
            )
        )
        assertEquals(
            MediaUpdateScheduler.TIME_UPDATE_MS,
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = true,
                title = "Track",
                pillMode = MediaPillMode.REMAINING,
                scrollTitle = false,
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
                title = "Track",
                pillMode = MediaPillMode.TITLE,
                scrollTitle = false,
                aodVisible = true,
                showShortCriticalText = true
            )
        )
    }

    @Test
    fun scrollingTitleTicksAtScrollCadence() {
        assertEquals(
            MediaUpdateScheduler.SCROLL_UPDATE_MS,
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = true,
                title = "Very Long Track",
                pillMode = MediaPillMode.TITLE,
                scrollTitle = true,
                aodVisible = false,
                showShortCriticalText = true
            )
        )
    }

    @Test
    fun hiddenOrPausedMediaDoesNotTick() {
        assertNull(
            MediaUpdateScheduler.nextDelayMs(
                showMirror = false,
                isPlaying = true,
                title = "Track",
                pillMode = MediaPillMode.ELAPSED,
                scrollTitle = false,
                aodVisible = false,
                showShortCriticalText = true
            )
        )
        assertNull(
            MediaUpdateScheduler.nextDelayMs(
                showMirror = true,
                isPlaying = false,
                title = "Track",
                pillMode = MediaPillMode.ELAPSED,
                scrollTitle = false,
                aodVisible = false,
                showShortCriticalText = true
            )
        )
    }
}

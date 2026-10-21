package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaTextFormatterTest {
    @Test
    fun formatsElapsedAndRemainingPillText() {
        assertEquals(
            "1:05",
            MediaTextFormatter.shortCriticalText(
                fallbackTitle = "Media",
                title = "Track",
                positionMs = 65_000,
                durationMs = 180_000,
                isPlaying = true,
                mode = MediaPillMode.ELAPSED
            )
        )
        assertEquals(
            "-1:55",
            MediaTextFormatter.shortCriticalText(
                fallbackTitle = "Media",
                title = "Track",
                positionMs = 65_000,
                durationMs = 180_000,
                isPlaying = true,
                mode = MediaPillMode.REMAINING
            )
        )
    }

    @Test
    fun fallsBackToTitleWhenTimeIsUnavailable() {
        assertEquals(
            "Long Track",
            MediaTextFormatter.shortCriticalText(
                fallbackTitle = "Media",
                title = "Long Track",
                positionMs = 0,
                durationMs = 0,
                isPlaying = true,
                mode = MediaPillMode.ELAPSED
            )
        )
    }

    @Test
    fun preservesFullTitleForSharedScroller() {
        assertEquals(
            "Track",
            MediaTextFormatter.shortCriticalText(
                fallbackTitle = "Media",
                title = "Track",
                positionMs = 0,
                durationMs = 0,
                isPlaying = false,
                mode = MediaPillMode.TITLE
            )
        )
        assertEquals(
            "Very Long Track",
            MediaTextFormatter.shortCriticalText(
                fallbackTitle = "Media",
                title = "Very Long Track",
                positionMs = 0,
                durationMs = 0,
                isPlaying = false,
                mode = MediaPillMode.TITLE
            )
        )
    }

    @Test
    fun aodSubTextAlwaysShowsElapsedAndTotalWithHeaderText() {
        assertEquals(
            "Player - 1:05 / 3:00",
            MediaTextFormatter.aodSubText(
                provider = "Player",
                positionMs = 65_000,
                durationMs = 180_000
            )
        )
        assertEquals(
            "Player - 1:05",
            MediaTextFormatter.aodSubText(
                provider = "Player",
                positionMs = 65_000,
                durationMs = 0
            )
        )
    }

    @Test
    fun longTimeModesKeepFullTextForSharedScroller() {
        assertEquals(
            "10:00:00",
            MediaTextFormatter.shortCriticalText(
                fallbackTitle = "Media",
                title = "Track",
                positionMs = 36_000_000,
                durationMs = 72_000_000,
                isPlaying = true,
                mode = MediaPillMode.ELAPSED
            )
        )
        assertEquals(
            "-10:00:00",
            MediaTextFormatter.shortCriticalText(
                fallbackTitle = "Media",
                title = "Track",
                positionMs = 36_000_000,
                durationMs = 72_000_000,
                isPlaying = true,
                mode = MediaPillMode.REMAINING
            )
        )
    }

    @Test
    fun buildsDetailAndSubText() {
        assertEquals(
            "Artist - Album",
            MediaTextFormatter.detailText(
                artist = "Artist",
                album = "Album",
                showArtist = true,
                showAlbum = true
            )
        )
        assertNull(
            MediaTextFormatter.detailText(
                artist = "Unknown Artist",
                album = "Unknown Album",
                showArtist = true,
                showAlbum = true
            )
        )
        assertEquals(
            "Player - 0:30 / 2:00",
            MediaTextFormatter.subText(
                provider = "Player",
                showProvider = true,
                showTimestamp = true,
                positionMs = 30_000,
                durationMs = 120_000
            )
        )
        assertEquals(
            "Player - 0:30",
            MediaTextFormatter.subText(
                provider = "Player",
                showProvider = true,
                showTimestamp = true,
                positionMs = 30_000,
                durationMs = 0
            )
        )
    }

    @Test
    fun addsEllipsisOnlyForOverflowingDetailText() {
        assertEquals(
            "Artist - Album",
            MediaTextFormatter.detailText(
                artist = "Artist",
                album = "Album",
                showArtist = true,
                showAlbum = true
            )
        )
        assertEquals(
            "A".repeat(67) + "...",
            MediaTextFormatter.detailText(
                artist = "A".repeat(80),
                album = "Album",
                showArtist = true,
                showAlbum = true
            )
        )
    }
}

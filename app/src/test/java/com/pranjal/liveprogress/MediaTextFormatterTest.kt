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
                title = "Track",
                positionMs = 65_000,
                durationMs = 180_000,
                isPlaying = true,
                mode = MediaPillMode.ELAPSED,
                scrollTitle = false,
                titleElapsedMs = 0
            )
        )
        assertEquals(
            "-1:55",
            MediaTextFormatter.shortCriticalText(
                title = "Track",
                positionMs = 65_000,
                durationMs = 180_000,
                isPlaying = true,
                mode = MediaPillMode.REMAINING,
                scrollTitle = false,
                titleElapsedMs = 0
            )
        )
    }

    @Test
    fun fallsBackToTitleWhenTimeIsUnavailable() {
        assertEquals(
            "Long...",
            MediaTextFormatter.shortCriticalText(
                title = "Long Track",
                positionMs = 0,
                durationMs = 0,
                isPlaying = true,
                mode = MediaPillMode.ELAPSED,
                scrollTitle = false,
                titleElapsedMs = 0
            )
        )
    }

    @Test
    fun addsEllipsisOnlyForOverflowingTitlePill() {
        assertEquals(
            "Track",
            MediaTextFormatter.shortCriticalText(
                title = "Track",
                positionMs = 0,
                durationMs = 0,
                isPlaying = false,
                mode = MediaPillMode.TITLE,
                scrollTitle = false,
                titleElapsedMs = 0
            )
        )
        assertEquals(
            "Very...",
            MediaTextFormatter.shortCriticalText(
                title = "Very Long Track",
                positionMs = 0,
                durationMs = 0,
                isPlaying = false,
                mode = MediaPillMode.TITLE,
                scrollTitle = false,
                titleElapsedMs = 0
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
    fun scrollsTitleWindow() {
        assertEquals(
            "Very Lo",
            MediaTextFormatter.shortCriticalText(
                title = "Very Long Track",
                positionMs = 0,
                durationMs = 0,
                isPlaying = false,
                mode = MediaPillMode.TITLE,
                scrollTitle = true,
                titleElapsedMs = 0
            )
        )
        assertEquals(
            "ery Lon",
            MediaTextFormatter.shortCriticalText(
                title = "Very Long Track",
                positionMs = 0,
                durationMs = 0,
                isPlaying = false,
                mode = MediaPillMode.TITLE,
                scrollTitle = true,
                titleElapsedMs = 1_500
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

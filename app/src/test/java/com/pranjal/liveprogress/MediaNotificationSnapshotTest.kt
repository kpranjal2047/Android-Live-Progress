package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaNotificationSnapshotTest {
    @Test
    fun scrollingFramesKeepBodyAndAodTextStable() {
        val scroller = PillTextScroller()
        val title = "Long paused title"
        val original = snapshot().copy(isPlaying = false)
        val first = original.copy(shortCriticalText = scroller.render(title, title, 0))
        val held = original.copy(shortCriticalText = scroller.render(title, title, 500))
        val moved = original.copy(shortCriticalText = scroller.render(title, title, 1_500))
        assertEquals(first, held)
        assertNotEquals(first, moved)
        assertEquals(first, moved.copy(shortCriticalText = first.shortCriticalText))
    }

    @Test
    fun identicalVisiblePayloadsCompareEqual() {
        val snapshot = snapshot()

        assertEquals(snapshot, snapshot.copy())
    }

    @Test
    fun positionAndTextChangesRequireNewPost() {
        val snapshot = snapshot()

        assertNotEquals(snapshot, snapshot.copy(positionSecond = 66))
        assertNotEquals(snapshot, snapshot.copy(shortCriticalText = "1:06"))
        assertNotEquals(snapshot, snapshot.copy(subText = "Player - 1:06 / 3:00"))
    }

    @Test
    fun sourceArtworkAndVisibilityChangesRequireNewPost() {
        val snapshot = snapshot()

        assertNotEquals(snapshot, snapshot.copy(sourceKey = "source-2"))
        assertNotEquals(snapshot, snapshot.copy(artworkKey = "artwork-2"))
        assertNotEquals(snapshot, snapshot.copy(aodVisible = false))
        assertNotEquals(snapshot, snapshot.copy(priorityMode = MirrorPriorityMode.LOW))
    }

    @Test
    fun aodVisibleIgnoresArtworkKey() {
        assertNull(MediaNotificationSnapshot.effectiveArtworkKey("artwork-1", aodVisible = true))
        assertEquals(
            "artwork-1",
            MediaNotificationSnapshot.effectiveArtworkKey("artwork-1", aodVisible = false)
        )
    }

    private fun snapshot(): MediaNotificationSnapshot {
        return MediaNotificationSnapshot(
            title = "Track",
            contentText = "Artist - Album",
            subText = "Player - 1:05 / 3:00",
            shortCriticalText = "1:05",
            isPlaying = true,
            positionSecond = 65,
            durationSecond = 180,
            aodVisible = true,
            showShortCriticalText = true,
            priorityMode = MirrorPriorityMode.DEFAULT,
            sourceKey = "source-1",
            appLabel = "Player",
            artworkKey = "artwork-1"
        )
    }
}

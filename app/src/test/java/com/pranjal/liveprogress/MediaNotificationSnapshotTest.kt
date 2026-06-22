package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaNotificationSnapshotTest {
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

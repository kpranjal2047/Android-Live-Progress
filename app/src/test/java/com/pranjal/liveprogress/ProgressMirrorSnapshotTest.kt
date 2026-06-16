package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProgressMirrorSnapshotTest {
    @Test
    fun identicalSnapshotsCompareEqual() {
        val snapshot = snapshot()

        assertEquals(snapshot, snapshot.copy())
    }

    @Test
    fun progressAndVisibilityChangesRequireRepost() {
        val snapshot = snapshot()

        assertNotEquals(snapshot, snapshot.copy(progress = 43, shortText = "43%"))
        assertNotEquals(snapshot, snapshot.copy(locked = true))
        assertNotEquals(snapshot, snapshot.copy(screenOff = true))
        assertNotEquals(snapshot, snapshot.copy(shouldSuppressOriginal = true))
        assertNotEquals(snapshot, snapshot.copy(priorityMode = MirrorPriorityMode.LOW))
    }

    @Test
    fun iconAndActionChangesRequireRepost() {
        val snapshot = snapshot()

        assertNotEquals(snapshot, snapshot.copy(actionsCount = 2))
        assertNotEquals(snapshot, snapshot.copy(sourceSmallIconKey = "icon-2"))
        assertNotEquals(snapshot, snapshot.copy(largeIconKey = "large-2"))
    }

    private fun snapshot(): ProgressMirrorSnapshot {
        return ProgressMirrorSnapshot(
            title = "Download",
            text = "File.zip",
            subText = "Downloader",
            appLabel = "Downloader",
            progress = 42,
            max = 100,
            indeterminate = false,
            shortText = "42%",
            color = 0,
            actionsCount = 1,
            sourceSmallIconKey = "icon-1",
            largeIconKey = "large-1",
            locked = false,
            screenOff = false,
            shouldSuppressOriginal = false,
            priorityMode = MirrorPriorityMode.DEFAULT
        )
    }
}

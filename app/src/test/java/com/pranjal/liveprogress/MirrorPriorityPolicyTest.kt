package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorPriorityPolicyTest {
    @Test
    fun screenOnLockScreenUsesDefaultPriority() {
        val mode = MirrorPriorityPolicy.forSurface(
            locked = true,
            screenOff = false
        )

        assertEquals(MirrorPriorityMode.DEFAULT, mode)
        assertEquals(MirrorNotificationBuilder.CHANNEL_ID, MirrorNotificationBuilder.channelId(mode))
        assertEquals(MediaLiveNotificationBuilder.CHANNEL_ID, MediaLiveNotificationBuilder.channelId(mode))
    }

    @Test
    fun unlockedSurfaceUsesLowPriority() {
        val mode = MirrorPriorityPolicy.forSurface(
            locked = false,
            screenOff = false
        )

        assertEquals(MirrorPriorityMode.LOW, mode)
        assertEquals(
            MirrorNotificationBuilder.LOW_PRIORITY_CHANNEL_ID,
            MirrorNotificationBuilder.channelId(mode)
        )
        assertEquals(
            MediaLiveNotificationBuilder.LOW_PRIORITY_CHANNEL_ID,
            MediaLiveNotificationBuilder.channelId(mode)
        )
    }

    @Test
    fun aodSurfaceUsesLowPriority() {
        val mode = MirrorPriorityPolicy.forSurface(
            locked = true,
            screenOff = true
        )

        assertEquals(MirrorPriorityMode.LOW, mode)
        assertEquals(
            MirrorNotificationBuilder.LOW_PRIORITY_CHANNEL_ID,
            MirrorNotificationBuilder.channelId(mode)
        )
        assertEquals(
            MediaLiveNotificationBuilder.LOW_PRIORITY_CHANNEL_ID,
            MediaLiveNotificationBuilder.channelId(mode)
        )
    }
}

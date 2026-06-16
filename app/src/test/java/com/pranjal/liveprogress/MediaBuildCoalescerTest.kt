package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaBuildCoalescerTest {
    @Test
    fun keepsOnlyLatestQueuedRequestWhileBuildIsRunning() {
        val coalescer = MediaBuildCoalescer<String>()
        val started = mutableListOf<String>()

        coalescer.submit("first", started::add)
        coalescer.submit("second", started::add)
        coalescer.submit("third", started::add)

        assertEquals(listOf("first"), started)

        coalescer.complete(started::add)

        assertEquals(listOf("first", "third"), started)
    }

    @Test
    fun startsImmediatelyAfterRunningBuildCompletesWithoutQueue() {
        val coalescer = MediaBuildCoalescer<String>()
        val started = mutableListOf<String>()

        coalescer.submit("first", started::add)
        coalescer.complete(started::add)
        coalescer.submit("second", started::add)

        assertEquals(listOf("first", "second"), started)
    }
}

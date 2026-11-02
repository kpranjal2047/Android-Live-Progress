package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotspotClientCountParserTest {
    private fun current(count: Int, mode: Int = 1, up: Boolean = true) = """
        Dump of SoftApManager id=1
        current StateMachine mode: StartedState
        mIfaceIsUp: $up
        mSpecifiedModeConfiguration.targetMode: $mode
        getConnectedClientList().size(): $count
        mTimeoutEnabled: true
    """.trimIndent()

    @Test fun readsCurrentZeroOneAndMultipleClients() {
        for (count in listOf(0, 1, 4)) assertEquals(count, HotspotClientCountParser.parse(current(count)))
    }
    @Test fun ignoresHistoricalEventsAndUnrelatedCounts() {
        val history = "\nrec[0]: getConnectedClientList().size(): 99\nclients=99"
        assertEquals(2, HotspotClientCountParser.parse(current(2) + history))
        assertNull(HotspotClientCountParser.parse(history))
    }
    @Test fun excludesLocalOnlyAndStoppedHotspots() {
        assertNull(HotspotClientCountParser.parse(current(3, mode = 2)))
        assertNull(HotspotClientCountParser.parse(current(3, up = false)))
    }
    @Test fun ambiguousManagersAndMalformedCountsAreUnknown() {
        assertNull(HotspotClientCountParser.parse(current(2) + "\n" + current(2)))
        assertNull(HotspotClientCountParser.parse(current(2).replace("size(): 2", "size(): unknown")))
        assertNull(HotspotClientCountParser.parse("Permission denied"))
    }
    @Test fun currentClientMapDeduplicatesClientsAcrossBands() {
        val dump = current(3).replace("getConnectedClientList().size(): 3",
            "mConnectedClientWithApInfoMap: {ap0=[WifiClient{macAddress=aa:bb:cc:dd:ee:01}], " +
                "ap1=[WifiClient{macAddress=AA:BB:CC:DD:EE:01}, WifiClient{macAddress=aa:bb:cc:dd:ee:02}]}")
        assertEquals(2, HotspotClientCountParser.parse(dump))
    }

    @Test fun emptyCurrentMapOverridesStaleNonzeroTotals() {
        val dump = current(9).replace("getConnectedClientList().size(): 9",
            "mConnectedClientWithApInfoMap: {}\ngetConnectedClientList().size(): 9")
        assertEquals(0, HotspotClientCountParser.parse(dump))
    }
    @Test fun malformedCurrentMapDoesNotFallBackToAnUntrustedTotal() {
        val dump = current(9).replace("getConnectedClientList().size(): 9",
            "mConnectedClientWithApInfoMap: {redacted}\ngetConnectedClientList().size(): 9")
        assertNull(HotspotClientCountParser.parse(dump))
    }
    @Test fun supportedCurrentFieldVariantsAreRecognized() {
        val dump = current(2).replace("mSpecifiedModeConfiguration", "mApConfig")
            .replace("getConnectedClientList().size()", "mConnectedClients.size()")
        assertEquals(2, HotspotClientCountParser.parse(dump))
    }
    @Test fun negativeOverflowDuplicateAndMissingCountsAreUnknown() {
        for (value in listOf("-1", "2147483648", "")) {
            assertNull(HotspotClientCountParser.parse(current(2).replace("size(): 2", "size(): $value")))
        }
        assertNull(HotspotClientCountParser.parse(current(2).replace("mTimeoutEnabled: true", "getConnectedClientList().size(): 3")))
    }
}

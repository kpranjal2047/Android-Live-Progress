package com.pranjal.liveprogress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticMessageDeduperTest {
    @Test
    fun suppressesRepeatedMessageForSameKey() {
        val deduper = DiagnosticMessageDeduper()

        assertTrue(deduper.shouldWrite("media", "shown"))
        assertFalse(deduper.shouldWrite("media", "shown"))
        assertTrue(deduper.shouldWrite("media", "hidden"))
    }

    @Test
    fun tracksKeysIndependentlyAndCanClear() {
        val deduper = DiagnosticMessageDeduper()

        assertTrue(deduper.shouldWrite("media", "shown"))
        assertTrue(deduper.shouldWrite("mirror", "shown"))
        assertFalse(deduper.shouldWrite("media", "shown"))

        deduper.clear()
        assertTrue(deduper.shouldWrite("media", "shown"))
    }
}

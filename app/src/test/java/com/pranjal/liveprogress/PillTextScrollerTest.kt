package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PillTextScrollerTest {
    @Test
    fun blankShortAndExactlySevenCharactersStayStatic() {
        for (text in listOf("", "   ", "42%", "Reached", "1234567")) {
            val scroller = PillTextScroller()
            assertFalse(PillTextScroller.needsScrolling(text))
            assertEquals(text.trim(), scroller.render(text, text, 0))
            assertEquals(text.trim(), scroller.render(text, text, 5_000))
        }
    }

    @Test
    fun pausesAtBothEndsThenWrapsToStart() {
        val scroller = PillTextScroller()
        val text = "Delivered"
        assertTrue(PillTextScroller.needsScrolling(text))
        assertEquals("Deliver", scroller.render(text, text, 0))
        assertEquals("Deliver", scroller.render(text, text, 500))
        assertEquals("Deliver", scroller.render(text, text, 1_000))
        assertEquals("elivere", scroller.render(text, text, 1_500))
        assertEquals("livered", scroller.render(text, text, 2_000))
        assertEquals("livered", scroller.render(text, text, 2_500))
        assertEquals("Deliver", scroller.render(text, text, 3_000))
    }

    @Test
    fun eachNotificationHasAnIndependentClockAndNewIdentityResetsIt() {
        val train = PillTextScroller()
        val delivery = PillTextScroller()
        assertEquals("Reachin", train.render("Reaching 9:43 PM", "train", 100))
        assertEquals("aching ", train.render("Reaching 9:43 PM", "train", 2_100))
        assertEquals("Deliver", delivery.render("Delivered", "delivery", 2_100))
        assertEquals("Reachin", train.render("Reaching 9:43 PM", "new-train", 2_100))
    }

    @Test
    fun changingCountdownDoesNotRestartTheWindow() {
        val scroller = PillTextScroller()
        assertEquals("-10:00:", scroller.render("-10:00:02", "remaining", 0))
        assertEquals("10:00:0", scroller.render("-10:00:01", "remaining", 1_500))
        assertEquals("0:00:00", scroller.render("-10:00:00", "remaining", 2_000))
        assertEquals("-10:00:", scroller.render("-10:00:00", "new-mode", 2_000))
    }

    @Test
    fun unicodeWindowsPreserveCombiningMarksFlagsAndJoinedEmoji() {
        val clusters = listOf("e\u0301", "\uD83C\uDDEE\uD83C\uDDF3",
            "\uD83D\uDC69\u200D\uD83D\uDCBB", "\uD83D\uDC4D\uD83C\uDFFD")
        for (cluster in clusters) {
            val short = cluster.repeat(7)
            assertFalse(PillTextScroller.needsScrolling(short))
            val text = "a" + short
            val scroller = PillTextScroller()
            assertTrue(PillTextScroller.needsScrolling(text))
            assertEquals("a" + cluster.repeat(6), scroller.render(text, text, 0))
            assertEquals(short, scroller.render(text, text, 1_500))
        }
    }

    @Test
    fun localizedStatusAndProgressOverrideScrollWithoutEllipsis() {
        val text = "Entregado"
        val scroller = PillTextScroller()
        assertEquals("Entrega", scroller.render(text, text, 0))
        assertEquals("tregado", scroller.render(text, text, 2_000))
        assertEquals("42%", scroller.render("42%", "42%", 2_500))
    }
}

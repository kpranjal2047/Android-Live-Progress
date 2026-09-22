package com.pranjal.liveprogress

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DominosDeliveryTimeTest {
    private val zone = ZoneId.of("UTC")
    private val nowWallTimeMillis = Instant.parse("2026-09-11T12:00:00Z").toEpochMilli()
    private val nowElapsedRealtime = 1_000_000L

    @Test
    fun parsesRelativeMinutes() {
        assertEquals(
            nowElapsedRealtime + 18L * 60_000L,
            DominosDeliveryTime.parseDeadline(
                text = "Your order will be delivered in 18 min",
                nowWallTimeMillis = nowWallTimeMillis,
                nowElapsedRealtime = nowElapsedRealtime,
                zoneId = zone
            )
        )
    }

    @Test
    fun parsesRelativeHoursAndMinutes() {
        assertEquals(
            nowElapsedRealtime + 80L * 60_000L,
            DominosDeliveryTime.parseDeadline(
                text = "Delivery expected in 1 hr 20 min",
                nowWallTimeMillis = nowWallTimeMillis,
                nowElapsedRealtime = nowElapsedRealtime,
                zoneId = zone
            )
        )
    }

    @Test
    fun parsesLabeledArrivalTime() {
        assertEquals(
            nowElapsedRealtime + 90L * 60_000L,
            DominosDeliveryTime.parseDeadline(
                text = "ETA at 1:30 PM",
                nowWallTimeMillis = nowWallTimeMillis,
                nowElapsedRealtime = nowElapsedRealtime,
                zoneId = zone
            )
        )
    }

    @Test
    fun rejectsAmbiguousOrInvalidText() {
        assertNull(
            DominosDeliveryTime.parseDeadline(
                text = "Enjoy your pizza in 20 min",
                nowWallTimeMillis = nowWallTimeMillis,
                nowElapsedRealtime = nowElapsedRealtime,
                zoneId = zone
            )
        )
        assertNull(
            DominosDeliveryTime.parseDeadline(
                text = "Delivery ETA at 25:90",
                nowWallTimeMillis = nowWallTimeMillis,
                nowElapsedRealtime = nowElapsedRealtime,
                zoneId = zone
            )
        )
    }

    @Test
    fun distinguishesReachedAndDeliveredStates() {
        assertEquals(
            DominosDeliveryStatus.REACHED,
            DominosDeliveryTime.deliveryStatus("Your order has reached you")
        )
        assertEquals(
            DominosDeliveryStatus.REACHED,
            DominosDeliveryTime.deliveryStatus("Your order arrived")
        )
        assertEquals(
            DominosDeliveryStatus.DELIVERED,
            DominosDeliveryTime.deliveryStatus("Order delivered")
        )
        assertEquals(
            DominosDeliveryStatus.NONE,
            DominosDeliveryTime.deliveryStatus("Delivery in 20 min")
        )
    }

    @Test
    fun formatsAndSchedulesCountdownAtMinuteBoundary() {
        val deadline = nowElapsedRealtime + 18L * 60_000L

        assertEquals("18 min", DominosDeliveryTime.remainingText(deadline, nowElapsedRealtime))
        assertEquals("18 min", DominosDeliveryTime.remainingText(deadline, nowElapsedRealtime + 1L))
        assertEquals("0 min", DominosDeliveryTime.remainingText(nowElapsedRealtime, nowElapsedRealtime))
        assertEquals(60_000L, DominosDeliveryTime.nextUpdateDelayMillis(deadline, nowElapsedRealtime))
        assertEquals(59_999L, DominosDeliveryTime.nextUpdateDelayMillis(deadline, nowElapsedRealtime + 1L))
        assertNull(DominosDeliveryTime.nextUpdateDelayMillis(nowElapsedRealtime, nowElapsedRealtime))
    }
}

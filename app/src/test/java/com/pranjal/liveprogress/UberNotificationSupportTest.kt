package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UberNotificationSupportTest {
    @Test
    fun etaPillUsesSourceClockInsteadOfPercentage() {
        assertEquals("4:12 PM", UberNotificationSupport.etaText("Drop-off at 4:12 PM"))
        assertEquals("16:12", UberNotificationSupport.etaText("Arrival at 16:12"))
        assertEquals("4:12 PM", UberNotificationSupport.etaText("Drop-off at 4:12\u202fPM"))
        assertEquals("४:१२ PM", UberNotificationSupport.etaText("आगमन ४:१२ PM"))
    }

    @Test
    fun missingOrInvalidEtaNeverFallsBackToPercentage() {
        assertEquals("", UberNotificationSupport.etaText("Your driver has arrived"))
        assertEquals("", UberNotificationSupport.etaText("Trip 90% complete"))
        assertEquals("", UberNotificationSupport.etaText("Drop-off at 25:12"))
        assertEquals("", UberNotificationSupport.etaText("Drop-off at 4:99 PM"))
        assertEquals("", UberNotificationSupport.etaText("Drop-off at 0:12 PM"))
    }

    @Test
    fun relativeEtaIsCopiedWithoutEstimatingOrCountingDown() {
        assertEquals("5 min", UberNotificationSupport.etaText("Arriving in 5 min"))
        assertEquals("1 hour 5 minutes", UberNotificationSupport.etaText("Drop-off in 1 hour 5 minutes"))
        assertEquals("4:25 PM", UberNotificationSupport.etaText("Drop-off at 4:25 PM"))
    }

    @Test
    fun currentTripLayoutIsRecognizedWithoutAcceptingArbitraryLayouts() {
        assertEquals(
            UberNotificationLayout.RIDER_TRIP,
            UberNotificationLayout.fromResourceName("ub__ooa_layout_rider_trip_expanded")
        )
        assertEquals(
            UberNotificationLayout.RICH,
            UberNotificationLayout.fromResourceName("ub__rich_notification_custom_big")
        )
        assertNull(UberNotificationLayout.fromResourceName("ub__ooa_layout_unknown"))
        assertNull(UberNotificationLayout.fromResourceName(null))
    }

    @Test
    fun currentTripBarUsesRenderedPaddingRatherThanEmptyNotificationExtras() {
        // Observed device: 1440px width, 560dpi, Uber's 110dp expanded edge distance.
        val progress = UberNotificationSupport.progressInfoFromBar(954, 1440 - 385)
        assertEquals(904, progress?.progress)
        assertEquals("90%", progress?.shortText)
    }

    @Test
    fun simpleRenderedProgressRequiresDeterminateValidRange() {
        assertEquals("60%", UberNotificationSupport.progressInfoFromNativeBar(60, 100, false)?.shortText)
        assertEquals(100, UberNotificationSupport.progressInfoFromNativeBar(120, 100, false)?.progress)
        assertEquals(0, UberNotificationSupport.progressInfoFromNativeBar(-1, 100, false)?.progress)
        assertNull(UberNotificationSupport.progressInfoFromNativeBar(0, 0, false))
        assertNull(UberNotificationSupport.progressInfoFromNativeBar(50, 100, true))
    }

    @Test
    fun customTripStillNeedsProgressEnabledOrExplicitAdditionalOptIn() {
        assertEquals(
            UberMirrorRoute.NONE,
            UberNotificationRouting.decide(false, true, false, false, false)
        )
        assertEquals(
            UberMirrorRoute.ADDITIONAL,
            UberNotificationRouting.decide(false, true, false, false, true)
        )
    }

    @Test
    fun visualProgressMapsToDeterminateProgress() {
        val progress = UberNotificationSupport.progressInfoFromBar(
            filledPaddingLeft = 275,
            usableWidth = 550
        )

        assertEquals(500, progress?.progress)
        assertEquals(1_000, progress?.max)
        assertFalse(progress?.indeterminate == true)
        assertEquals("50%", progress?.shortText)
    }

    @Test
    fun visualProgressIsClampedAndRejectsInvalidWidth() {
        assertEquals(
            1_000,
            UberNotificationSupport.progressInfoFromBar(900, 550)?.progress
        )
        assertEquals(0, UberNotificationSupport.progressInfoFromBar(0, 550)?.progress)
        assertNull(UberNotificationSupport.progressInfoFromBar(-1, 550))
        assertNull(UberNotificationSupport.progressInfoFromBar(1, 0))
    }

    @Test
    fun validCustomUberNotificationUsesProgressRoute() {
        assertEquals(
            UberMirrorRoute.CUSTOM_PROGRESS,
            UberNotificationRouting.decide(
                progressEnabled = true,
                hasCustomCandidate = true,
                allowNativeProgress = false,
                hasNativeProgress = false,
                additionalEnabled = false
            )
        )
    }

    @Test
    fun unreadableCustomUberNotificationUsesOnlyAdditionalFallback() {
        assertEquals(
            UberMirrorRoute.ADDITIONAL,
            UberNotificationRouting.decide(
                progressEnabled = true,
                hasCustomCandidate = false,
                allowNativeProgress = false,
                hasNativeProgress = true,
                additionalEnabled = true
            )
        )
        assertEquals(
            UberMirrorRoute.NONE,
            UberNotificationRouting.decide(
                progressEnabled = true,
                hasCustomCandidate = false,
                allowNativeProgress = false,
                hasNativeProgress = true,
                additionalEnabled = false
            )
        )
    }

    @Test
    fun standardProgressIsOnlyUsedForNonRichUberNotifications() {
        assertEquals(
            UberMirrorRoute.NATIVE_PROGRESS,
            UberNotificationRouting.decide(
                progressEnabled = true,
                hasCustomCandidate = false,
                allowNativeProgress = true,
                hasNativeProgress = true,
                additionalEnabled = true
            )
        )
        assertEquals(
            UberMirrorRoute.ADDITIONAL,
            UberNotificationRouting.decide(
                progressEnabled = false,
                hasCustomCandidate = false,
                allowNativeProgress = true,
                hasNativeProgress = true,
                additionalEnabled = true
            )
        )
    }
}

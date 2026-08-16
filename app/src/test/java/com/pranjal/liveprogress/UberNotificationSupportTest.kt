package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UberNotificationSupportTest {
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

package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Test

class DominosNotificationRoutingTest {
    @Test
    fun validCustomNotificationUsesProgressSettings() {
        assertEquals(
            DominosMirrorRoute.CUSTOM_PROGRESS,
            DominosNotificationRouting.decide(
                progressEnabled = true,
                recognizedCustomLayout = true,
                hasCustomCandidate = true,
                hasNativeProgress = false,
                additionalEnabled = false
            )
        )
    }

    @Test
    fun unreadableCustomNotificationUsesOnlyAdditionalFallback() {
        assertEquals(
            DominosMirrorRoute.ADDITIONAL,
            DominosNotificationRouting.decide(
                progressEnabled = true,
                recognizedCustomLayout = true,
                hasCustomCandidate = false,
                hasNativeProgress = true,
                additionalEnabled = true
            )
        )
        assertEquals(
            DominosMirrorRoute.NONE,
            DominosNotificationRouting.decide(
                progressEnabled = true,
                recognizedCustomLayout = true,
                hasCustomCandidate = false,
                hasNativeProgress = true,
                additionalEnabled = false
            )
        )
    }

    @Test
    fun nonCustomNativeProgressUsesExistingProgressRoute() {
        assertEquals(
            DominosMirrorRoute.NATIVE_PROGRESS,
            DominosNotificationRouting.decide(
                progressEnabled = true,
                recognizedCustomLayout = false,
                hasCustomCandidate = false,
                hasNativeProgress = true,
                additionalEnabled = true
            )
        )
    }

    @Test
    fun progressDisabledUsesOnlyAdditionalFallback() {
        assertEquals(
            DominosMirrorRoute.ADDITIONAL,
            DominosNotificationRouting.decide(
                progressEnabled = false,
                recognizedCustomLayout = true,
                hasCustomCandidate = true,
                hasNativeProgress = true,
                additionalEnabled = true
            )
        )
    }
}

package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhereIsMyTrainNotificationSupportTest {
    @Test
    fun mapsVisualRouteEdgesToClampedProgress() {
        assertEquals(
            ProgressInfo(progress = 500, max = 1_000, indeterminate = false),
            WhereIsMyTrainNotificationSupport.progressInfoFromRouteEdges(100, 300, 200)
        )
        assertEquals(
            ProgressInfo(progress = 0, max = 1_000, indeterminate = false),
            WhereIsMyTrainNotificationSupport.progressInfoFromRouteEdges(100, 300, 20)
        )
        assertEquals(
            ProgressInfo(progress = 1_000, max = 1_000, indeterminate = false),
            WhereIsMyTrainNotificationSupport.progressInfoFromRouteEdges(100, 300, 400)
        )
        assertNull(WhereIsMyTrainNotificationSupport.progressInfoFromRouteEdges(200, 200, 200))
    }

    @Test
    fun combinesStatusDistanceAndScheduleForAdditionalText() {
        assertEquals(
            "Departed Chandanagar · 33 km remaining · Behind by 2 min",
            WhereIsMyTrainText.additionalText(
                TestNotificationText.english,
                currentStatus = "Departed Chandanagar",
                distance = "33 km",
                scheduleStatus = "Delayed by 2 minutes"
            )
        )
        assertEquals(
            "Departed Chandanagar · Ahead by 5 min",
            WhereIsMyTrainText.additionalText(TestNotificationText.english, "Departed Chandanagar", null, "Ahead by 5 minutes")
        )
        assertEquals("33 km remaining", WhereIsMyTrainText.additionalText(TestNotificationText.english, " ", " 33 km ", "On time"))
        assertNull(WhereIsMyTrainText.additionalText(TestNotificationText.english, null, null, null))
    }

    @Test
    fun compactsDelayedAndEarlyScheduleStatuses() {
        assertEquals("Behind by 8 min", WhereIsMyTrainText.compactScheduleStatus("Delayed by 8 minutes", TestNotificationText.english))
        assertEquals("Behind by 8 min", WhereIsMyTrainText.compactScheduleStatus("Behind by 8 minutes", TestNotificationText.english))
        assertEquals("On time", WhereIsMyTrainText.compactScheduleStatus("On time", TestNotificationText.english))
        assertEquals("4 min early", WhereIsMyTrainText.compactScheduleStatus("4 minutes early", TestNotificationText.english))
        assertEquals("Ahead by 24 min", WhereIsMyTrainText.compactScheduleStatus("Ahead by 24 minutes", TestNotificationText.english))
    }

    @Test
    fun routesCustomLiveStatusBeforeAdditionalFallback() {
        assertEquals(
            WhereIsMyTrainMirrorRoute.CUSTOM_PROGRESS,
            WhereIsMyTrainNotificationRouting.decide(
                progressEnabled = true,
                recognizedCustomLayout = true,
                hasCustomCandidate = true,
                hasNativeProgress = false,
                additionalEnabled = true
            )
        )
        assertEquals(
            WhereIsMyTrainMirrorRoute.ADDITIONAL,
            WhereIsMyTrainNotificationRouting.decide(
                progressEnabled = true,
                recognizedCustomLayout = true,
                hasCustomCandidate = false,
                hasNativeProgress = false,
                additionalEnabled = true
            )
        )
        assertEquals(
            WhereIsMyTrainMirrorRoute.NONE,
            WhereIsMyTrainNotificationRouting.decide(
                progressEnabled = false,
                recognizedCustomLayout = true,
                hasCustomCandidate = false,
                hasNativeProgress = false,
                additionalEnabled = false
            )
        )
    }
}

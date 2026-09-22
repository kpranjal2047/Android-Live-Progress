package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhereIsMyTrainNotificationSupportTest {
    @Test
    fun extractsDestinationFromTrainRouteTitle() {
        assertEquals(
            "Falaknuma",
            WhereIsMyTrainNotificationSupport.destinationFromTitle(
                "47191 - Lingampalli - Falaknuma MMTS"
            )
        )
        assertEquals(
            "Mumbai Central",
            WhereIsMyTrainNotificationSupport.destinationFromTitle(
                "12952 - New Delhi - Mumbai Central Express"
            )
        )
        assertNull(WhereIsMyTrainNotificationSupport.destinationFromTitle("Where Is My Train"))
        assertEquals(
            "47191 - Lingampalli - Falaknuma MMTS",
            WhereIsMyTrainNotificationSupport.normalizeTrainTitle(
                "47191 - Lingampalli - Falaknuma MMTS 8:10 PM"
            )
        )
    }

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
    fun separatesStatusAndDestinationDetailsForProgressStyle() {
        assertEquals(
            "Departed Chandanagar - +2 min",
            WhereIsMyTrainText.statusText(
                currentStatus = "Departed Chandanagar",
                scheduleStatus = "Delayed by 2 minutes"
            )
        )
        assertEquals(
            "Falaknuma · 33 km · Reaching 9:43 PM",
            WhereIsMyTrainText.destinationText(
                destination = "Falaknuma",
                distance = "33 km",
                reachingText = "Reaching 9:43 PM"
            )
        )
    }

    @Test
    fun compactsDelayedAndEarlyScheduleStatuses() {
        assertEquals("+8 min", WhereIsMyTrainText.compactScheduleStatus("Delayed by 8 minutes"))
        assertEquals("4 min early", WhereIsMyTrainText.compactScheduleStatus("4 minutes early"))
        assertEquals("Ahead by 24 min", WhereIsMyTrainText.compactScheduleStatus("Ahead by 24 minutes"))
    }

    @Test
    fun parsesTrainRouteDestinationAndEtaFromRecognizedText() {
        assertEquals(
            WhereIsMyTrainOcrData(
                trainTitle = "47191 - Lingampalli - Falaknuma MMTS",
                destination = "Falaknuma",
                distance = "33 km",
                eta = "9:43 PM"
            ),
            WhereIsMyTrainNotificationSupport.parseRecognizedRouteText(
                """
                    47191 - Lingampalli - Falaknuma MMTS 8:10 PM
                    Departed Chandanagar
                    Falaknuma 33 km - 9:43 PM
                """.trimIndent()
            )
        )
    }

    @Test
    fun parsesSingleDashTrainLabelFromCurrentLiveStatusLayout() {
        assertEquals(
            WhereIsMyTrainOcrData(
                trainTitle = "17030 - Vijayapura Express",
                destination = "Vijayapura",
                distance = "421 km",
                eta = "7:40 AM"
            ),
            WhereIsMyTrainNotificationSupport.parseRecognizedRouteText(
                """
                    17030 - Vijayapura Express 9:27 PM
                    Lingampalli
                    Vijayapura
                    Not started from Lingampalli
                    Vijayapura 421 km - 7:40 AM
                    Ahead by 24 minutes
                """.trimIndent()
            )
        )
    }

    @Test
    fun parsesDistanceWhenDestinationAndEtaAreOnSeparateLines() {
        val data = WhereIsMyTrainNotificationSupport.parseRecognizedRouteText(
            """
                17030 - Vijayapura Express 9:27 PM
                Vijayapura
                421 km - 7:40 AM
            """.trimIndent()
        )

        requireNotNull(data)
        assertEquals("Vijayapura", data.destination)
        assertEquals("421 km", data.distance)
        assertEquals("7:40 AM", data.eta)
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

package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhereIsMyTrainRouteParserTest {
    private fun element(text: String, x: Int, y: Int, width: Int = 100) =
        NotificationOcrElement(text, x, y, x + width, y + 30)

    private fun parse(vararg elements: NotificationOcrElement) = WhereIsMyTrainRouteParser.parse(
        NotificationOcrLayout("Unused OCR block order", 1440, 700, elements.toList())
    )

    @Test
    fun parsesAnnotatedFieldsEvenWhenOcrBlocksAreOutOfOrder() {
        val data = parse(
            element("Ahead by 5 minutes", 490, 630, 450),
            element("Departed Example Town", 110, 370, 500),
            element("12345", 170, 55),
            element("-", 280, 55, 20),
            element("Example Town - Secunderabad M...", 320, 55, 880),
            element("10:51 PM", 1230, 55, 170),
            element("Example Town", 110, 195, 300),
            element("Secunderabad Junction", 815, 195, 520),
            element("Secunderabad Junction", 110, 505, 520),
            element("20km", 930, 505),
            element("-", 1050, 505, 20),
            element("11:33 PM", 1100, 505, 230)
        )!!

        assertEquals("12345", data.trainNumber)
        assertEquals("Example Town - Secunderabad M...", data.trainName)
        assertEquals("12345 - Example Town - Secunderabad M...", data.trainTitle)
        assertEquals("10:51 PM", data.lastUpdatedTime)
        assertEquals("Example Town", data.sourceStation)
        assertEquals("Secunderabad Junction", data.destination)
        assertEquals("Departed Example Town", data.currentStatus)
        assertEquals("20 km", data.distance)
        assertEquals("11:33 PM", data.eta)
        assertEquals("Ahead by 5 minutes", data.scheduleStatus)
    }

    @Test
    fun usesPositionsInsteadOfWhetherOcrMergedColumnsIntoOneLine() {
        val data = parse(
            element("Sector", 110, 195), element("2", 225, 195, 20),
            element("St.", 900, 195, 50), element("John's", 965, 195, 120),
            element("Junction", 1100, 195, 200),
            element("St. John's Junction", 110, 505, 500),
            element("15.5", 930, 505, 90), element("KM", 1035, 505, 70),
            element("–", 1110, 505, 20), element("9:05", 1140, 505, 100),
            element("am", 1250, 505, 70)
        )!!
        assertEquals("Sector 2", data.sourceStation)
        assertEquals("St. John's Junction", data.destination)
        assertEquals("15.5 km", data.distance)
        assertEquals("9:05 AM", data.eta)
    }

    @Test
    fun retainsArbitraryTrainNamesAndLeadingZeroTrainNumbers() {
        val data = parse(element("001 – रात / Express (Special) - M… 10:51\u202fpm", 170, 55, 1230))!!
        assertEquals("001", data.trainNumber)
        assertEquals("रात / Express (Special) - M…", data.trainName)
        assertEquals("10:51 PM", data.lastUpdatedTime)
        assertNull(data.destination)
        assertNull(data.eta)
    }

    @Test
    fun neverUsesHeaderUpdateTimeOrTrainNameAsArrivalFields() {
        val data = parse(
            element("12345 - Destination Express 10:51 PM", 170, 55, 1230),
            element("Departed Origin", 110, 370, 400),
            element("Ahead by 5 minutes", 490, 630, 450)
        )!!
        assertEquals("10:51 PM", data.lastUpdatedTime)
        assertNull(data.eta)
        assertNull(data.destination)
        assertNull(data.distance)
    }

    @Test
    fun readsArrivalEvenWithoutHeaderOrDistance() {
        val data = parse(
            element("Full Destination", 110, 505, 500),
            element("12:01 AM", 1110, 505, 220)
        )!!
        assertNull(data.trainNumber)
        assertEquals("Full Destination", data.destination)
        assertNull(data.distance)
        assertEquals("12:01 AM", data.eta)
    }

    @Test
    fun destinationLabelSurvivesMissingEta() {
        val data = parse(
            element("Full Destination", 815, 195, 520),
            element("20 km", 930, 505, 150)
        )!!
        assertEquals("Full Destination", data.destination)
        assertEquals("20 km", data.distance)
        assertNull(data.eta)
    }

    @Test
    fun prefersCompleteDestinationInEitherStationRow() {
        assertEquals("Full Destination Junction", parse(
            element("Full Destination Junction", 815, 195, 520),
            element("Full Destination J...", 110, 505, 500),
            element("20 km - 11:33 PM", 930, 505, 400)
        )?.destination)
        assertEquals("Full Destination Junction", parse(
            element("Full Destination J…", 815, 195, 520),
            element("Full Destination Junction", 110, 505, 500),
            element("20 km - 11:33 PM", 930, 505, 400)
        )?.destination)
    }

    @Test
    fun missingTopDestinationDoesNotUseSourceStation() {
        val data = parse(
            element("Source Station", 110, 195, 300),
            element("Final Junction", 110, 505, 500),
            element("20 km - 11:33 PM", 930, 505, 400)
        )!!
        assertEquals("Source Station", data.sourceStation)
        assertEquals("Final Junction", data.destination)
    }

    @Test
    fun validatesBothClockFieldsIndependently() {
        for (invalid in listOf("0:12 PM", "13:12 PM", "10:60 PM", "25:99 AM")) {
            val data = parse(
                element("12345 - Arbitrary name $invalid", 170, 55, 1230),
                element("Full Destination", 815, 195, 520),
                element("20 km - $invalid", 930, 505, 400)
            )!!
            assertNull(data.lastUpdatedTime)
            assertNull(data.eta)
            assertEquals("Full Destination", data.destination)
        }
        assertEquals("12:00 PM", parse(element("20 km - 12:00 PM", 930, 505, 400))?.eta)
        assertEquals("12:00 AM", parse(element("20 km - 12:00 AM", 930, 505, 400))?.eta)
    }

    @Test
    fun requiresNumericTrainNumberAndPreservesLocalizedStationLabels() {
        val data = parse(
            element("Train ABC - Express 10:51 PM", 170, 55, 1230),
            element("नई दिल्ली", 110, 195, 300),
            element("சென்னை சென்ட்ரல்", 815, 195, 520),
            element("On time", 490, 630, 450)
        )!!
        assertNull(data.trainNumber)
        assertNull(data.lastUpdatedTime)
        assertEquals("नई दिल्ली", data.sourceStation)
        assertEquals("சென்னை சென்ட்ரல்", data.destination)
        assertEquals("On time", data.scheduleStatus)
    }

    @Test
    fun scalesWithBitmapSizeAndIgnoresNoiseInProgressRail() {
        val elements = listOf(
            element("12345 - Express 10:51 PM", 170, 55, 1230),
            element("Destination", 815, 195, 520),
            element("1:23 PM", 900, 280, 300),
            element("20 km - 11:33 PM", 930, 505, 400)
        )
        val original = WhereIsMyTrainRouteParser.parse(NotificationOcrLayout("", 1440, 700, elements))
        val scaled = elements.map { it.copy(left = it.left * 2, top = it.top * 2, right = it.right * 2, bottom = it.bottom * 2) }
        assertEquals(original, WhereIsMyTrainRouteParser.parse(NotificationOcrLayout("", 2880, 1400, scaled)))
        assertEquals("11:33 PM", original?.eta)
    }

    @Test
    fun absentGeometryNeverFallsBackToFlattenedText() {
        assertNull(WhereIsMyTrainRouteParser.parse(NotificationOcrLayout("12345 - Name 10:51 PM", 1440, 700, emptyList())))
        assertNull(WhereIsMyTrainRouteParser.parse(NotificationOcrLayout("", 0, 700, listOf(element("Destination", 815, 195, 520)))))
        assertNull(parse(element("Out of bounds", 1500, 195)))
    }
}

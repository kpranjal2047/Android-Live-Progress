package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationImageOcrTest {
    @Test
    fun recognizedLinesBecomeTitleAndBody() {
        val result = NotificationImageOcr.fromRecognizedText(
            "  Train 47191  \n\nDeparted Chandanagar\nReaching 9:43 PM  "
        )

        requireNotNull(result)
        assertEquals("Train 47191", result.title)
        assertEquals("Departed Chandanagar\nReaching 9:43 PM", result.text)
        assertEquals("Train 47191\nDeparted Chandanagar\nReaching 9:43 PM", result.fullText)
    }

    @Test
    fun blankRecognitionIsIgnored() {
        assertNull(NotificationImageOcr.fromRecognizedText(" \n\t "))
    }
}

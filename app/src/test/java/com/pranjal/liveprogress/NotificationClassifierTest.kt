package com.pranjal.liveprogress

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationClassifierTest {
    @Test
    fun onlyPromotedProgressStyleCountsAsAlreadyLiveProgress() {
        assertFalse(
            NotificationClassifier.isAlreadyLiveProgressTemplate(
                "android.app.Notification\$ProgressStyle"
            )
        )

        assertTrue(
            NotificationClassifier.isAlreadyLiveProgressTemplate(
                template = "android.app.Notification\$ProgressStyle",
                flags = Notification.FLAG_PROMOTED_ONGOING
            )
        )
        assertTrue(
            NotificationClassifier.isAlreadyLiveProgressTemplate(
                template = "android.app.Notification\$ProgressStyle",
                requestedPromotedOngoing = true
            )
        )

        assertFalse(NotificationClassifier.isAlreadyLiveProgressTemplate(null))
        assertFalse(
            NotificationClassifier.isAlreadyLiveProgressTemplate(
                template = "android.app.Notification\$BigTextStyle",
                flags = Notification.FLAG_PROMOTED_ONGOING,
                requestedPromotedOngoing = true
            )
        )
    }

    @Test
    fun realProgressValuesMapToDeterminateProgress() {
        val progress = NotificationClassifier.progressInfoFromValues(
            indeterminate = false,
            max = 100,
            progress = 42,
            forceIndeterminate = false
        )

        assertEquals(42, progress?.progress)
        assertEquals(100, progress?.max)
        assertFalse(progress?.indeterminate == true)
    }

    @Test
    fun missingProgressValuesAreIgnoredWhenCategoryIsNotSelected() {
        assertNull(
            NotificationClassifier.progressInfoFromValues(
                indeterminate = false,
                max = 0,
                progress = 0,
                forceIndeterminate = false
            )
        )
    }

    @Test
    fun selectedCategoryWithoutProgressBecomesIndeterminate() {
        val progress = NotificationClassifier.progressInfoFromValues(
            indeterminate = false,
            max = 0,
            progress = 0,
            forceIndeterminate = true
        )

        assertEquals(0, progress?.progress)
        assertEquals(0, progress?.max)
        assertTrue(progress?.indeterminate == true)
        assertEquals("", progress?.shortText)
    }

    @Test
    fun selectedCategoryDoesNotOverrideRealProgress() {
        val progress = NotificationClassifier.progressInfoFromValues(
            indeterminate = false,
            max = 200,
            progress = 50,
            forceIndeterminate = true
        )

        assertEquals(50, progress?.progress)
        assertEquals(200, progress?.max)
        assertFalse(progress?.indeterminate == true)
        assertEquals("25%", progress?.shortText)
    }
}

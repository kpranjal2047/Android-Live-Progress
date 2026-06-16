package com.pranjal.liveprogress

import android.app.Notification
import org.junit.Assert.assertFalse
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
}

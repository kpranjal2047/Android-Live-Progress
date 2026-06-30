package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationCategoryPreferencesTest {
    @Test
    fun categoryKeyRoundTrips() {
        val key = NotificationCategoryKey(
            packageName = "com.example.source",
            uid = 12345,
            channelId = "delivery_status"
        )

        assertEquals(key, NotificationCategoryKey.parse(key.encode()))
    }

    @Test
    fun categoryKeyRejectsInvalidRecords() {
        assertNull(NotificationCategoryKey.parse("com.example.only.package"))
        assertNull(NotificationCategoryKey.parse("com.example\u001Fnot-a-uid\u001Fchannel"))
    }

    @Test
    fun observedCategoryRoundTrips() {
        val category = ObservedNotificationCategory(
            key = NotificationCategoryKey(
                packageName = "com.example.source",
                uid = 12345,
                channelId = "delivery_status"
            ),
            appLabel = "Example",
            channelName = "Delivery status",
            lastSeenMillis = 123456789L
        )

        assertEquals(category, ObservedNotificationCategory.parse(category.encode()))
    }

    @Test
    fun observedCategoryUsesChannelIdWhenNameIsMissing() {
        val category = ObservedNotificationCategory(
            key = NotificationCategoryKey(
                packageName = "com.example.source",
                uid = 12345,
                channelId = "delivery_status"
            ),
            appLabel = "Example",
            channelName = null,
            lastSeenMillis = 123456789L
        )

        assertEquals("delivery_status", category.displayName)
    }
}

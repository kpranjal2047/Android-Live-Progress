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
            lastSeenMillis = 123456789L,
            isSystemApp = true
        )

        assertEquals(category, ObservedNotificationCategory.parse(category.encode()))
    }

    @Test
    fun observedCategoryReadsOldRecordsAsUserApps() {
        val legacy = listOf(
            "com.example.source",
            "12345",
            "delivery_status",
            "Example",
            "Delivery status",
            "123456789"
        ).joinToString("\u001F")

        assertEquals(
            false,
            ObservedNotificationCategory.parse(legacy)?.isSystemApp
        )
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

    @Test
    fun categorySettingsDefaultsKeepAdditionalCategoriesConservative() {
        val settings = NotificationCategorySettings()

        assertEquals(false, settings.enabled)
        assertEquals(true, settings.showOnAod)
        assertEquals(false, settings.showOnLockScreen)
        assertEquals(false, settings.hideOriginalNotification)
        assertEquals(false, settings.keepAfterOriginalDismissed)
    }

    @Test
    fun categorySettingsRoundTripsWithKey() {
        val key = NotificationCategoryKey(
            packageName = "com.example.source",
            uid = 12345,
            channelId = "delivery_status"
        )
        val settings = NotificationCategorySettings(
            enabled = true,
            showOnAod = false,
            showOnLockScreen = true,
            hideOriginalNotification = true,
            keepAfterOriginalDismissed = true
        )

        assertEquals(
            key to settings,
            NotificationCategorySettings.parse(settings.encode(key))
        )
    }

    @Test
    fun categorySettingsReadsOldRecordsWithKeepAfterDismissDisabled() {
        val encoded = listOf(
            "com.example.source",
            "12345",
            "delivery_status",
            "true",
            "false",
            "true",
            "true"
        ).joinToString("\u001F")

        val settings = NotificationCategorySettings.parse(encoded)?.second

        assertEquals(true, settings?.enabled)
        assertEquals(false, settings?.showOnAod)
        assertEquals(true, settings?.showOnLockScreen)
        assertEquals(true, settings?.hideOriginalNotification)
        assertEquals(false, settings?.keepAfterOriginalDismissed)
    }

    @Test
    fun enabledCategorySettingsCopyProgressDefaults() {
        val settings = NotificationCategorySettings.enabledWithProgressDefaults(
            showOnAod = false,
            showOnLockScreen = true,
            hideOriginalNotification = true
        )

        assertEquals(true, settings.enabled)
        assertEquals(false, settings.showOnAod)
        assertEquals(true, settings.showOnLockScreen)
        assertEquals(true, settings.hideOriginalNotification)
        assertEquals(false, settings.keepAfterOriginalDismissed)
    }
}

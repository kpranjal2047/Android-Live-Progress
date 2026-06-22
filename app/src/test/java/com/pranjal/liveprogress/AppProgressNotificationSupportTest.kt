package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProgressNotificationSupportTest {
    @Test
    fun recognizesKnownAppPackages() {
        assertTrue(AppProgressNotificationSupport.isSupportedPackage("com.ubercab"))
        assertTrue(AppProgressNotificationSupport.isSupportedPackage("me.lyft.android"))
        assertTrue(AppProgressNotificationSupport.isSupportedPackage("com.dd.doordash"))
        assertTrue(AppProgressNotificationSupport.isSupportedPackage("in.swiggy.android"))
        assertTrue(AppProgressNotificationSupport.isSupportedPackage("com.application.zomato"))
        assertTrue(AppProgressNotificationSupport.isSupportedPackage("com.google.android.apps.maps"))
        assertTrue(AppProgressNotificationSupport.isSupportedPackage("com.waze"))
        assertTrue(AppProgressNotificationSupport.isSupportedPackage("com.grofers.customerapp"))
        assertTrue(AppProgressNotificationSupport.isSupportedPackage("com.zeptoconsumerapp"))
        assertTrue(AppProgressNotificationSupport.isSupportedPackage("com.grabtaxi.passenger"))
        assertTrue(AppProgressNotificationSupport.isSupportedPackage("com.gojek.app"))
        assertFalse(AppProgressNotificationSupport.isSupportedPackage("com.example.other"))
    }

    @Test
    fun activeRideStatusMapsToDeterminateStageProgress() {
        val progress = AppProgressNotificationSupport.fallbackProgressInfo(
            packageName = "com.ubercab",
            textFields = listOf("Your driver is arriving", "Meet your driver in 3 min"),
            ongoing = true
        )

        assertEquals(90, progress?.progress)
        assertEquals(100, progress?.max)
        assertFalse(progress?.indeterminate == true)
    }

    @Test
    fun percentageInActiveStatusMapsToDeterminateProgress() {
        val progress = AppProgressNotificationSupport.fallbackProgressInfo(
            packageName = "com.dd.doordash",
            textFields = listOf("Order progress", "42% complete"),
            ongoing = true
        )

        assertEquals(42, progress?.progress)
        assertEquals(100, progress?.max)
        assertFalse(progress?.indeterminate == true)
    }

    @Test
    fun deliveryPreparingStatusMapsToStageProgress() {
        val progress = AppProgressNotificationSupport.fallbackProgressInfo(
            packageName = "in.swiggy.android",
            textFields = listOf("Your order is being prepared", "Restaurant is preparing it now"),
            ongoing = true
        )

        assertEquals(35, progress?.progress)
        assertEquals(100, progress?.max)
        assertFalse(progress?.indeterminate == true)
    }

    @Test
    fun packageOutForDeliveryMapsToStageProgress() {
        val progress = AppProgressNotificationSupport.fallbackProgressInfo(
            packageName = "com.amazon.mShop.android.shopping",
            textFields = listOf("Your package is out for delivery"),
            ongoing = true
        )

        assertEquals(80, progress?.progress)
        assertEquals(100, progress?.max)
        assertFalse(progress?.indeterminate == true)
    }

    @Test
    fun navigationStatusFallsBackToIndeterminateProgress() {
        val progress = AppProgressNotificationSupport.fallbackProgressInfo(
            packageName = "com.google.android.apps.maps",
            textFields = listOf("Navigation", "Continue for 2 mi, ETA 12 min"),
            ongoing = true
        )

        assertEquals(0, progress?.progress)
        assertEquals(0, progress?.max)
        assertTrue(progress?.indeterminate == true)
    }

    @Test
    fun ignoresPromotionalNotifications() {
        assertNull(
            AppProgressNotificationSupport.fallbackProgressInfo(
                packageName = "com.application.zomato",
                textFields = listOf("Save 50% on your next order"),
                ongoing = false
            )
        )
    }

    @Test
    fun ignoresCompletedNotificationsEvenWhenOngoingFlagIsPresent() {
        assertNull(
            AppProgressNotificationSupport.fallbackProgressInfo(
                packageName = "com.grubhub.android",
                textFields = listOf("Your order was delivered"),
                ongoing = true
            )
        )
    }

    @Test
    fun ignoresSupportedAppStatusWhenItIsNotOngoingOrActive() {
        assertNull(
            AppProgressNotificationSupport.fallbackProgressInfo(
                packageName = "me.lyft.android",
                textFields = listOf("Thanks for riding with Lyft"),
                ongoing = false
            )
        )
    }
}

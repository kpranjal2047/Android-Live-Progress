package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupFlowPolicyTest {
    @Test
    fun accessibilityIsOptionalButRequiredWhenQsHideSettingIsOn() {
        assertEquals(
            SetupRequirementKind.ACCESSIBILITY,
            readyState(
                hideWhenQuickSettingsExpanded = true,
                accessibilityEnabled = false
            )
        )
        assertNull(
            readyState(
                hideWhenQuickSettingsExpanded = false,
                accessibilityEnabled = false
            )
        )
    }

    @Test
    fun shizukuIsOptionalUntilSuppressionIsEnabledAndAvailable() {
        assertNull(
            readyState(
                progressEnabled = true,
                suppressOriginalNotification = true,
                shizukuAvailable = false,
                shizukuGranted = false
            )
        )
        assertEquals(
            SetupRequirementKind.SHIZUKU,
            readyState(
                progressEnabled = true,
                suppressOriginalNotification = true,
                shizukuAvailable = true,
                shizukuGranted = false
            )
        )
        assertNull(
            readyState(
                progressEnabled = true,
                suppressOriginalNotification = false,
                shizukuAvailable = true,
                shizukuGranted = false
            )
        )
    }

    @Test
    fun requiredSetupPrecedesOptionalSetup() {
        assertEquals(
            SetupRequirementKind.NOTIFICATIONS,
            readyState(
                notificationsReady = false,
                hideWhenQuickSettingsExpanded = true,
                accessibilityEnabled = false
            )
        )
        assertEquals(
            SetupRequirementKind.PROMOTED_NOTIFICATIONS,
            readyState(promotedNotificationsReady = false)
        )
        assertEquals(
            SetupRequirementKind.NOTIFICATION_LISTENER,
            readyState(notificationListenerReady = false)
        )
    }

    @Test
    fun progressSuppressionToggleStateFollowsShizukuAvailability() {
        assertEquals(
            ToggleState(checked = false, enabled = false),
            SetupFlowPolicy.progressSuppressionToggleState(
                progressEnabled = true,
                suppressOriginalNotification = true,
                shizukuAvailable = false,
                shizukuGranted = false
            )
        )
        assertEquals(
            ToggleState(checked = false, enabled = true),
            SetupFlowPolicy.progressSuppressionToggleState(
                progressEnabled = true,
                suppressOriginalNotification = true,
                shizukuAvailable = true,
                shizukuGranted = false
            )
        )
        assertEquals(
            ToggleState(checked = true, enabled = true),
            SetupFlowPolicy.progressSuppressionToggleState(
                progressEnabled = true,
                suppressOriginalNotification = true,
                shizukuAvailable = true,
                shizukuGranted = true
            )
        )
    }

    private fun readyState(
        notificationsReady: Boolean = true,
        promotedNotificationsReady: Boolean = true,
        notificationListenerReady: Boolean = true,
        progressEnabled: Boolean = true,
        hideWhenQuickSettingsExpanded: Boolean = false,
        accessibilityEnabled: Boolean = true,
        suppressOriginalNotification: Boolean = false,
        shizukuAvailable: Boolean = false,
        shizukuGranted: Boolean = false
    ): SetupRequirementKind? {
        return SetupFlowPolicy.firstMissingRequirement(
            notificationsReady = notificationsReady,
            promotedNotificationsReady = promotedNotificationsReady,
            notificationListenerReady = notificationListenerReady,
            progressEnabled = progressEnabled,
            hideWhenQuickSettingsExpanded = hideWhenQuickSettingsExpanded,
            accessibilityEnabled = accessibilityEnabled,
            suppressOriginalNotification = suppressOriginalNotification,
            shizukuAvailable = shizukuAvailable,
            shizukuGranted = shizukuGranted
        )
    }
}

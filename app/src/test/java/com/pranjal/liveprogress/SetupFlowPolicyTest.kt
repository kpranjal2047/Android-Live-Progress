package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupFlowPolicyTest {
    @Test
    fun ungrantedShizukuPrecedesEveryOtherSetupRequirement() {
        val state = state(
            notificationsReady = false,
            promotedNotificationsReady = false,
            notificationListenerReady = false,
            accessibilityEnabled = false,
            accessibilityRequired = true,
            shizukuAvailable = true,
            shizukuGranted = false
        )

        assertTrue(SetupFlowPolicy.shouldRequestShizukuFirst(state, shizukuDeferred = false))
        assertFalse(SetupFlowPolicy.shouldRequestShizukuFirst(state, shizukuDeferred = true))
    }

    @Test
    fun automaticSetupIncludesEveryMissingRequiredAccess() {
        assertEquals(
            listOf(
                AutomaticSetupItem.NOTIFICATIONS,
                AutomaticSetupItem.PROMOTED_NOTIFICATIONS,
                AutomaticSetupItem.NOTIFICATION_LISTENER,
                AutomaticSetupItem.ACCESSIBILITY
            ),
            SetupFlowPolicy.automaticItemsMissing(
                state(
                    notificationsReady = false,
                    promotedNotificationsReady = false,
                    notificationListenerReady = false,
                    accessibilityEnabled = false,
                    accessibilityRequired = true
                )
            )
        )
    }

    @Test
    fun optionalAccessibilityIsNotIncludedInAutomaticSetup() {
        val state = state(accessibilityEnabled = false, accessibilityRequired = false)
        assertFalse(SetupFlowPolicy.automaticItemsMissing(state).contains(AutomaticSetupItem.ACCESSIBILITY))
        assertNull(SetupFlowPolicy.firstMissingManualRequirement(state))
    }

    @Test
    fun manualFlowKeepsTheExistingRequirementOrder() {
        assertEquals(
            SetupRequirementKind.NOTIFICATIONS,
            SetupFlowPolicy.firstMissingManualRequirement(state(notificationsReady = false))
        )
        assertEquals(
            SetupRequirementKind.PROMOTED_NOTIFICATIONS,
            SetupFlowPolicy.firstMissingManualRequirement(state(promotedNotificationsReady = false))
        )
        assertEquals(
            SetupRequirementKind.NOTIFICATION_LISTENER,
            SetupFlowPolicy.firstMissingManualRequirement(state(notificationListenerReady = false))
        )
        assertEquals(
            SetupRequirementKind.ACCESSIBILITY,
            SetupFlowPolicy.firstMissingManualRequirement(
                state(accessibilityEnabled = false, accessibilityRequired = true)
            )
        )
    }

    @Test
    fun progressSuppressionToggleStateFollowsShizukuAvailability() {
        assertEquals(
            ToggleState(checked = false, enabled = false),
            SetupFlowPolicy.progressSuppressionToggleState(true, true, false, false)
        )
        assertEquals(
            ToggleState(checked = false, enabled = true),
            SetupFlowPolicy.progressSuppressionToggleState(true, true, true, false)
        )
        assertEquals(
            ToggleState(checked = true, enabled = true),
            SetupFlowPolicy.progressSuppressionToggleState(true, true, true, true)
        )
    }

    private fun state(
        notificationsReady: Boolean = true,
        promotedNotificationsReady: Boolean = true,
        notificationListenerReady: Boolean = true,
        accessibilityEnabled: Boolean = true,
        accessibilityRequired: Boolean = false,
        shizukuAvailable: Boolean = false,
        shizukuGranted: Boolean = false
    ): SetupAccessState {
        return SetupAccessState(
            notificationsReady = notificationsReady,
            promotedNotificationsReady = promotedNotificationsReady,
            notificationListenerReady = notificationListenerReady,
            accessibilityEnabled = accessibilityEnabled,
            accessibilityRequired = accessibilityRequired,
            shizukuAvailable = shizukuAvailable,
            shizukuGranted = shizukuGranted
        )
    }
}

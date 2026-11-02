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
            ToggleState(checked = false, enabled = false),
            SetupFlowPolicy.progressSuppressionToggleState(true, true, true, false)
        )
        assertEquals(
            ToggleState(checked = true, enabled = true),
            SetupFlowPolicy.progressSuppressionToggleState(true, true, true, true)
        )
    }

    @Test
    fun allGrantedSkipsEveryPermissionPageAndAutomaticGrant() {
        val ready = state(shizukuAvailable = true, shizukuGranted = true)
        assertFalse(SetupFlowPolicy.shouldRequestShizukuFirst(ready, false))
        assertNull(SetupFlowPolicy.firstMissingManualRequirement(ready))
        assertTrue(SetupFlowPolicy.automaticItemsMissing(ready).isEmpty())
    }

    @Test
    fun currentPermissionCombinationsOnlyRequestMissingAccess() {
        for (mask in 0..15) {
            val ready = state(notificationsReady = mask and 1 != 0,
                promotedNotificationsReady = mask and 2 != 0,
                notificationListenerReady = mask and 4 != 0,
                accessibilityEnabled = mask and 8 != 0, accessibilityRequired = true)
            val expected = AutomaticSetupItem.entries.filterIndexed { index, _ -> mask and (1 shl index) == 0 }
            assertEquals(expected, SetupFlowPolicy.automaticItemsMissing(ready))
            assertEquals(expected.firstOrNull()?.name,
                SetupFlowPolicy.firstMissingManualRequirement(ready)?.name)
        }
    }

    @Test
    fun staleAutomaticOfferDoesNotRegrantAccessGrantedElsewhere() {
        val requested = AutomaticSetupItem.entries.toList()
        val current = state(notificationListenerReady = false)
        assertEquals(listOf(AutomaticSetupItem.NOTIFICATION_LISTENER),
            SetupFlowPolicy.remainingAutomaticItems(requested, current))
        assertTrue(SetupFlowPolicy.remainingAutomaticItems(requested, state()).isEmpty())
        // Access not offered to the user is never added to an in-flight request.
        assertTrue(SetupFlowPolicy.remainingAutomaticItems(listOf(AutomaticSetupItem.NOTIFICATIONS), current).isEmpty())
    }

    @Test
    fun partialFailureOnlyOffersUnresolvedPermission() {
        val after = state(promotedNotificationsReady = false)
        assertEquals(SetupRequirementKind.PROMOTED_NOTIFICATIONS,
            SetupFlowPolicy.firstMissingManualRequirement(after))
        assertEquals(listOf(AutomaticSetupItem.PROMOTED_NOTIFICATIONS),
            SetupFlowPolicy.remainingAutomaticItems(AutomaticSetupItem.entries.toList(), after))
    }

    @Test
    fun blockedNotificationsDoNotRepeatGrantedRuntimePermission() {
        assertEquals(NotificationPermissionAction.CONTINUE, SetupFlowPolicy.notificationAction(true, true))
        assertEquals(NotificationPermissionAction.OPEN_SETTINGS, SetupFlowPolicy.notificationAction(true, false))
        assertEquals(NotificationPermissionAction.REQUEST_PERMISSION, SetupFlowPolicy.notificationAction(false, false))
        assertEquals(NotificationPermissionAction.REQUEST_PERMISSION, SetupFlowPolicy.notificationAction(false, true))
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

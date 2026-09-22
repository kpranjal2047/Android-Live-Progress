package com.pranjal.liveprogress

enum class SetupRequirementKind {
    NOTIFICATIONS,
    PROMOTED_NOTIFICATIONS,
    NOTIFICATION_LISTENER,
    ACCESSIBILITY
}

enum class AutomaticSetupItem {
    NOTIFICATIONS,
    PROMOTED_NOTIFICATIONS,
    NOTIFICATION_LISTENER,
    ACCESSIBILITY
}

data class SetupAccessState(
    val notificationsReady: Boolean,
    val promotedNotificationsReady: Boolean,
    val notificationListenerReady: Boolean,
    val accessibilityEnabled: Boolean,
    val accessibilityRequired: Boolean,
    val shizukuAvailable: Boolean,
    val shizukuGranted: Boolean
)

data class ToggleState(
    val checked: Boolean,
    val enabled: Boolean
)

object SetupFlowPolicy {
    fun shouldRequestShizukuFirst(
        state: SetupAccessState,
        shizukuDeferred: Boolean
    ): Boolean {
        return state.shizukuAvailable && !state.shizukuGranted && !shizukuDeferred
    }

    fun automaticItemsMissing(state: SetupAccessState): List<AutomaticSetupItem> {
        return buildList {
            if (!state.notificationsReady) add(AutomaticSetupItem.NOTIFICATIONS)
            if (!state.promotedNotificationsReady) add(AutomaticSetupItem.PROMOTED_NOTIFICATIONS)
            if (!state.notificationListenerReady) add(AutomaticSetupItem.NOTIFICATION_LISTENER)
            if (state.accessibilityRequired && !state.accessibilityEnabled) {
                add(AutomaticSetupItem.ACCESSIBILITY)
            }
        }
    }

    fun firstMissingManualRequirement(state: SetupAccessState): SetupRequirementKind? {
        if (!state.notificationsReady) return SetupRequirementKind.NOTIFICATIONS
        if (!state.promotedNotificationsReady) return SetupRequirementKind.PROMOTED_NOTIFICATIONS
        if (!state.notificationListenerReady) return SetupRequirementKind.NOTIFICATION_LISTENER
        if (state.accessibilityRequired && !state.accessibilityEnabled) {
            return SetupRequirementKind.ACCESSIBILITY
        }
        return null
    }

    fun progressSuppressionToggleState(
        progressEnabled: Boolean,
        suppressOriginalNotification: Boolean,
        shizukuAvailable: Boolean,
        shizukuGranted: Boolean
    ): ToggleState {
        return ToggleState(
            checked = suppressOriginalNotification && shizukuGranted,
            enabled = progressEnabled && shizukuAvailable
        )
    }
}

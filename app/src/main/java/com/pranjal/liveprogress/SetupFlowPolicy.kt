package com.pranjal.liveprogress

enum class SetupRequirementKind {
    NOTIFICATIONS,
    PROMOTED_NOTIFICATIONS,
    NOTIFICATION_LISTENER,
    ACCESSIBILITY,
    SHIZUKU
}

data class ToggleState(
    val checked: Boolean,
    val enabled: Boolean
)

object SetupFlowPolicy {
    fun firstMissingRequirement(
        notificationsReady: Boolean,
        promotedNotificationsReady: Boolean,
        notificationListenerReady: Boolean,
        progressEnabled: Boolean,
        hideWhenQuickSettingsExpanded: Boolean,
        accessibilityEnabled: Boolean,
        suppressOriginalNotification: Boolean,
        shizukuAvailable: Boolean,
        shizukuGranted: Boolean
    ): SetupRequirementKind? {
        if (!notificationsReady) return SetupRequirementKind.NOTIFICATIONS
        if (!promotedNotificationsReady) return SetupRequirementKind.PROMOTED_NOTIFICATIONS
        if (!notificationListenerReady) return SetupRequirementKind.NOTIFICATION_LISTENER
        if (hideWhenQuickSettingsExpanded && !accessibilityEnabled) {
            return SetupRequirementKind.ACCESSIBILITY
        }
        if (
            progressEnabled &&
            suppressOriginalNotification &&
            shizukuAvailable &&
            !shizukuGranted
        ) {
            return SetupRequirementKind.SHIZUKU
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

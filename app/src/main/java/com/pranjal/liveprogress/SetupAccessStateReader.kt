package com.pranjal.liveprogress

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object SetupAccessStateReader {
    fun read(context: Context): SetupAccessState {
        val appContext = context.applicationContext
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val visibilityPreferences = VisibilityPreferences(appContext)
        val privileged = PrivilegedAccess.currentState(appContext)
        return SetupAccessState(
            notificationsReady = appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED && notificationManager.areNotificationsEnabled(),
            promotedNotificationsReady = notificationManager.canPostPromotedNotifications(),
            notificationListenerReady = isEnabled(
                appContext,
                "enabled_notification_listeners",
                ComponentName(appContext, NotificationMirrorService::class.java)
            ),
            accessibilityEnabled = isEnabled(
                appContext,
                "enabled_accessibility_services",
                ComponentName(appContext, QuickSettingsAccessibilityService::class.java)
            ),
            accessibilityRequired = visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded ||
                visibilityPreferences.hideStatusBarPillWhenSourceAppForeground,
            shizukuAvailable = privileged.shizukuAvailable,
            shizukuGranted = privileged.shizukuGranted
        )
    }

    private fun isEnabled(context: Context, setting: String, component: ComponentName): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, setting) ?: return false
        val componentName = component.flattenToString()
        return enabled.split(':').any { it.equals(componentName, ignoreCase = true) }
    }
}

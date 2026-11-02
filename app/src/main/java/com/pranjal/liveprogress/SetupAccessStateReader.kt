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
            accessibilityEnabled = accessibilityEnabled(appContext),
            accessibilityRequired = visibilityPreferences.hideMirrorsWhenQuickSettingsExpanded ||
                visibilityPreferences.hideStatusBarPillWhenSourceAppForeground,
            shizukuAvailable = privileged.shizukuAvailable,
            shizukuGranted = privileged.shizukuGranted
        )
    }

    fun accessibilityEnabled(context: Context): Boolean =
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            isEnabled(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ComponentName(context, QuickSettingsAccessibilityService::class.java))

    private fun isEnabled(context: Context, setting: String, component: ComponentName): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, setting) ?: return false
        return EnabledServiceComponents.contains(enabled, component.packageName, component.className)
    }
}

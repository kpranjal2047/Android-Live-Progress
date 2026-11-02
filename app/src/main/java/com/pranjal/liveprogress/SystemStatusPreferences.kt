package com.pranjal.liveprogress

import android.content.Context

enum class SystemStatusKind(val notificationId: Int, val titleRes: Int, val iconRes: Int) {
    TORCH(3101, R.string.system_torch, R.drawable.ic_system_torch),
    HOTSPOT(3102, R.string.system_hotspot, R.drawable.ic_system_hotspot)
}

class SystemStatusPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("system_status", Context.MODE_PRIVATE)
    fun enabled(kind: SystemStatusKind) = prefs.getBoolean("${kind.name}_enabled", true)
    fun showOnLockScreen(kind: SystemStatusKind) = prefs.getBoolean("${kind.name}_lock", true)
    fun setEnabled(kind: SystemStatusKind, value: Boolean) {
        prefs.edit().putBoolean("${kind.name}_enabled", value).apply()
        AdditionalNotificationPreferenceEvents.notifyChanged()
    }
    fun setShowOnLockScreen(kind: SystemStatusKind, value: Boolean) {
        prefs.edit().putBoolean("${kind.name}_lock", value).apply()
        AdditionalNotificationPreferenceEvents.notifyChanged()
    }
}

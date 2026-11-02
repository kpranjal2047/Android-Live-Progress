package com.pranjal.liveprogress

import android.content.Context

class ProgressPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var showOnLockScreen: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ON_LOCK_SCREEN, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ON_LOCK_SCREEN, value).apply()

    var suppressOriginalNotification: Boolean
        get() = prefs.getBoolean(KEY_SUPPRESS_ORIGINAL_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_SUPPRESS_ORIGINAL_NOTIFICATION, value).apply()

    companion object {
        private const val PREFS = "live_progress_progress_preferences"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SHOW_ON_LOCK_SCREEN = "show_on_lock_screen"
        private const val KEY_SUPPRESS_ORIGINAL_NOTIFICATION = "suppress_original_notification"
    }
}

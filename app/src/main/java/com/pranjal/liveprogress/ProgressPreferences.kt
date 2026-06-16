package com.pranjal.liveprogress

import android.content.Context

class ProgressPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var showOnLockScreen: Boolean
        get() = if (prefs.contains(KEY_SHOW_ON_LOCK_SCREEN)) {
            prefs.getBoolean(KEY_SHOW_ON_LOCK_SCREEN, false)
        } else if (prefs.contains(KEY_LOCK_SCREEN_MODE)) {
            prefs.enumValue(KEY_LOCK_SCREEN_MODE, LockScreenMirrorMode.ORIGINAL) == LockScreenMirrorMode.MIRROR
        } else {
            false
        }
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ON_LOCK_SCREEN, value).apply()

    var showOnAod: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ON_AOD, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ON_AOD, value).apply()

    var suppressOriginalNotification: Boolean
        get() = prefs.getBoolean(KEY_SUPPRESS_ORIGINAL_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_SUPPRESS_ORIGINAL_NOTIFICATION, value).apply()

    private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
        key: String,
        default: T
    ): T {
        val raw = getString(key, default.name) ?: default.name
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    companion object {
        private const val PREFS = "live_progress_progress_preferences"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LOCK_SCREEN_MODE = "lock_screen_mode"
        private const val KEY_SHOW_ON_LOCK_SCREEN = "show_on_lock_screen"
        private const val KEY_SHOW_ON_AOD = "show_on_aod"
        private const val KEY_SUPPRESS_ORIGINAL_NOTIFICATION = "suppress_original_notification"
    }
}

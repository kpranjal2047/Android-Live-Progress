package com.pranjal.liveprogress

import android.content.Context

enum class MediaPillMode {
    TITLE,
    ELAPSED,
    REMAINING
}

class MediaPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var scrollTitle: Boolean
        get() = prefs.getBoolean(KEY_SCROLL_TITLE, true)
        set(value) = prefs.edit().putBoolean(KEY_SCROLL_TITLE, value).apply()

    var showOnAod: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ON_AOD, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ON_AOD, value).apply()

    var showOnLockScreen: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ON_LOCK_SCREEN, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ON_LOCK_SCREEN, value).apply()

    var pillMode: MediaPillMode
        get() = prefs.enumValue(KEY_PILL_MODE, MediaPillMode.TITLE)
        set(value) = prefs.edit().putString(KEY_PILL_MODE, value.name).apply()

    private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
        key: String,
        default: T
    ): T {
        val raw = getString(key, default.name) ?: default.name
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    companion object {
        private const val PREFS = "live_progress_media_preferences"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SCROLL_TITLE = "scroll_title"
        private const val KEY_SHOW_ON_AOD = "show_on_aod"
        private const val KEY_SHOW_ON_LOCK_SCREEN = "show_on_lock_screen"
        private const val KEY_PILL_MODE = "pill_mode"
    }
}

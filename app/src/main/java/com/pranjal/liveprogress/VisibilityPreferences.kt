package com.pranjal.liveprogress

import android.content.Context
import java.util.concurrent.CopyOnWriteArraySet

class VisibilityPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var hideMirrorsWhenQuickSettingsExpanded: Boolean
        get() = prefs.getBoolean(KEY_HIDE_MIRRORS_WHEN_QS_EXPANDED, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_MIRRORS_WHEN_QS_EXPANDED, value).apply()

    companion object {
        private const val PREFS = "live_progress_visibility_preferences"
        private const val KEY_HIDE_MIRRORS_WHEN_QS_EXPANDED = "hide_mirrors_when_qs_expanded"
    }
}

object VisibilityPreferenceEvents {
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        listeners.forEach { it.invoke() }
    }
}

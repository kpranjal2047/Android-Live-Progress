package com.pranjal.liveprogress

import android.content.Context

object FirstRunCategoryRefreshPolicy {
    fun shouldStart(
        initialOnboarding: Boolean,
        refreshHandled: Boolean,
        shizukuGranted: Boolean,
        manualSetupComplete: Boolean
    ): Boolean {
        return initialOnboarding && !refreshHandled && shizukuGranted && manualSetupComplete
    }

    fun shouldMarkHandledWithoutRefresh(
        initialOnboarding: Boolean,
        refreshHandled: Boolean,
        shizukuAvailable: Boolean,
        shizukuDeferred: Boolean
    ): Boolean {
        return initialOnboarding && !refreshHandled && (!shizukuAvailable || shizukuDeferred)
    }
}

class FirstRunSetupPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun claimInitialLaunch(): Boolean {
        if (prefs.getBoolean(KEY_INITIAL_LAUNCH_CLAIMED, false)) return false
        prefs.edit().putBoolean(KEY_INITIAL_LAUNCH_CLAIMED, true).apply()
        return true
    }

    var initialCategoryRefreshHandled: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_CATEGORY_REFRESH_HANDLED, false)
        set(value) = prefs.edit().putBoolean(KEY_INITIAL_CATEGORY_REFRESH_HANDLED, value).apply()

    companion object {
        private const val PREFS = "live_progress_first_run_setup"
        private const val KEY_INITIAL_LAUNCH_CLAIMED = "initial_launch_claimed"
        private const val KEY_INITIAL_CATEGORY_REFRESH_HANDLED =
            "initial_category_refresh_handled"
    }
}

package com.pranjal.liveprogress

import android.content.Context
import android.os.Handler
import android.os.Looper

object AppUiLifecycleTracker {
    private const val EXIT_CLEAR_DELAY_MS = 700L
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val visibilityCounter = AppUiVisibilityCounter()
    private var clearGeneration = 0

    fun onActivityStarted() {
        synchronized(lock) {
            visibilityCounter.onActivityStarted()
            clearGeneration += 1
        }
        handler.removeCallbacksAndMessages(null)
    }

    fun onActivityStopped(context: Context) {
        val appContext = context.applicationContext
        val remaining = synchronized(lock) {
            visibilityCounter.onActivityStopped()
        }
        if (remaining == 0) {
            val scheduledGeneration = synchronized(lock) {
                clearGeneration += 1
                clearGeneration
            }
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed(
                {
                    val shouldClear = synchronized(lock) {
                        visibilityCounter.isAppUiExited() &&
                            clearGeneration == scheduledGeneration
                    }
                    if (
                        shouldClear &&
                        DeveloperPreferences(appContext).logClearPolicy ==
                        LogClearPolicy.ON_APP_EXIT
                    ) {
                        AppDiagnostics.clear(appContext)
                    }
                },
                EXIT_CLEAR_DELAY_MS
            )
        }
    }

}

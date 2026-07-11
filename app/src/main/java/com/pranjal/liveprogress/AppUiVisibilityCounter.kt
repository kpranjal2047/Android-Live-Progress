package com.pranjal.liveprogress

class AppUiVisibilityCounter {
    private var visibleActivityCount = 0

    fun onActivityStarted(): Int {
        visibleActivityCount += 1
        return visibleActivityCount
    }

    fun onActivityStopped(): Int {
        visibleActivityCount = (visibleActivityCount - 1).coerceAtLeast(0)
        return visibleActivityCount
    }

    fun isAppUiExited(): Boolean = visibleActivityCount == 0

    fun visibleCount(): Int = visibleActivityCount
}

package com.pranjal.liveprogress

object MediaSessionRefreshPolicy {
    fun shouldQuerySessions(
        hasCachedControllers: Boolean,
        hasActiveController: Boolean,
        sourcePackageChanged: Boolean,
        currentControllerInvalid: Boolean,
        explicitRefresh: Boolean
    ): Boolean {
        return explicitRefresh ||
            sourcePackageChanged ||
            currentControllerInvalid ||
            (!hasActiveController && !hasCachedControllers)
    }
}

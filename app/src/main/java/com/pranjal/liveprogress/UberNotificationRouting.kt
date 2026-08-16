package com.pranjal.liveprogress

enum class UberMirrorRoute {
    CUSTOM_PROGRESS,
    NATIVE_PROGRESS,
    ADDITIONAL,
    NONE
}

object UberNotificationRouting {
    fun decide(
        progressEnabled: Boolean,
        hasCustomCandidate: Boolean,
        allowNativeProgress: Boolean,
        hasNativeProgress: Boolean,
        additionalEnabled: Boolean
    ): UberMirrorRoute {
        if (progressEnabled && hasCustomCandidate) {
            return UberMirrorRoute.CUSTOM_PROGRESS
        }
        if (
            progressEnabled &&
            allowNativeProgress &&
            hasNativeProgress
        ) {
            return UberMirrorRoute.NATIVE_PROGRESS
        }
        return if (additionalEnabled) UberMirrorRoute.ADDITIONAL else UberMirrorRoute.NONE
    }
}

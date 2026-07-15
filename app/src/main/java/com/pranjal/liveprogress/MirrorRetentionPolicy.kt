package com.pranjal.liveprogress

object MirrorRetentionPolicy {
    fun shouldRetainAfterSourceRemoval(candidate: MirrorCandidate): Boolean {
        return shouldRetainAfterSourceRemoval(candidate.displaySettings)
    }

    fun shouldUseOriginalSuppression(candidate: MirrorCandidate, retainedAfterSourceRemoval: Boolean): Boolean {
        return shouldUseOriginalSuppression(candidate.displaySettings, retainedAfterSourceRemoval)
    }

    fun shouldRetainAfterSourceRemoval(displaySettings: MirrorCandidateDisplaySettings): Boolean {
        return displaySettings.source == MirrorCandidateSource.ADDITIONAL &&
            displaySettings.keepAfterOriginalDismissed
    }

    fun shouldUseOriginalSuppression(
        displaySettings: MirrorCandidateDisplaySettings,
        retainedAfterSourceRemoval: Boolean
    ): Boolean {
        return displaySettings.hideOriginalNotification && !retainedAfterSourceRemoval
    }
}

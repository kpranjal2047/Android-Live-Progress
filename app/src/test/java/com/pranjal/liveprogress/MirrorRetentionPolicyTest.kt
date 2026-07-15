package com.pranjal.liveprogress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRetentionPolicyTest {
    @Test
    fun onlyAdditionalMirrorsOptedInAreRetainedAfterSourceRemoval() {
        assertTrue(
            MirrorRetentionPolicy.shouldRetainAfterSourceRemoval(
                additionalSettings(keepAfterOriginalDismissed = true)
            )
        )
        assertFalse(
            MirrorRetentionPolicy.shouldRetainAfterSourceRemoval(
                additionalSettings(keepAfterOriginalDismissed = false)
            )
        )
        assertFalse(
            MirrorRetentionPolicy.shouldRetainAfterSourceRemoval(
                progressSettings(keepAfterOriginalDismissed = true)
            )
        )
    }

    @Test
    fun retainedMirrorsDoNotKeepSuppressingOriginalAfterSourceRemoval() {
        val settings = additionalSettings(hideOriginalNotification = true)

        assertTrue(
            MirrorRetentionPolicy.shouldUseOriginalSuppression(
                displaySettings = settings,
                retainedAfterSourceRemoval = false
            )
        )
        assertFalse(
            MirrorRetentionPolicy.shouldUseOriginalSuppression(
                displaySettings = settings,
                retainedAfterSourceRemoval = true
            )
        )
    }

    private fun additionalSettings(
        hideOriginalNotification: Boolean = false,
        keepAfterOriginalDismissed: Boolean = false
    ): MirrorCandidateDisplaySettings {
        return MirrorCandidateDisplaySettings(
            source = MirrorCandidateSource.ADDITIONAL,
            showOnAod = true,
            showOnLockScreen = true,
            hideOriginalNotification = hideOriginalNotification,
            keepAfterOriginalDismissed = keepAfterOriginalDismissed
        )
    }

    private fun progressSettings(keepAfterOriginalDismissed: Boolean): MirrorCandidateDisplaySettings {
        return MirrorCandidateDisplaySettings(
            source = MirrorCandidateSource.PROGRESS,
            showOnAod = true,
            showOnLockScreen = true,
            hideOriginalNotification = false,
            keepAfterOriginalDismissed = keepAfterOriginalDismissed
        )
    }
}

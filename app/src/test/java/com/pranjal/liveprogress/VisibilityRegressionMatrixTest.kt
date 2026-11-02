package com.pranjal.liveprogress

import org.junit.Assert.*
import org.junit.Test

class VisibilityRegressionMatrixTest {
    private val bools = listOf(false, true)

    @Test fun mediaAlwaysRestoresOriginalOnAwakeLockScreenRegardlessOfOtherSettings() {
        for (enabled in bools) for (active in bools) for (aod in bools) for (qs in bools)
        for (hideQs in bools) for (foreground in bools) for (progress in bools) for (suppress in bools) {
            val d = MediaVisibilityPolicy.decide(enabled, active, true, false, qs, hideQs,
                sourceAppInForeground = foreground, progressMirrorActive = progress,
                showOnAod = aod, hideOriginalNotification = suppress)
            assertFalse(d.showMirror)
            assertFalse(d.suppressOriginal)
            assertFalse(d.aodVisible)
        }
    }

    @Test fun aodEligibilityIgnoresUnlockedOnlyHidingButHonorsEnabledAndProgressPriority() {
        for (enabled in bools) for (active in bools) for (aod in bools) for (qs in bools)
        for (foreground in bools) for (progress in bools) for (suppress in bools) {
            val expected = enabled && active && aod && !progress
            val d = MediaVisibilityPolicy.decide(enabled, active, true, true, qs,
                sourceAppInForeground = foreground, progressMirrorActive = progress,
                showOnAod = aod, hideOriginalNotification = suppress)
            assertEquals(expected, d.showMirror)
            assertEquals(expected, d.aodVisible)
            assertEquals(expected && suppress, d.suppressOriginal)
        }
    }

    @Test fun independentSystemStatesCoverEverySurfaceAndDismissalCombination() {
        for (active in bools) for (enabled in bools) for (visible in bools)
        for (locked in bools) for (off in bools) for (dismissed in bools) {
            val state = SystemStatusSession().apply { update(active); if (dismissed) dismiss() }
            assertEquals(active && enabled && !dismissed && (!(locked || off) || visible),
                state.visible(enabled, visible, locked, off))
        }
    }

    @Test fun unlockedMediaNeverSuppressesOriginalAcrossHidingCombinations() {
        for (qs in bools) for (hideQs in bools) for (foreground in bools) for (hideForeground in bools) {
            val d = MediaVisibilityPolicy.decide(true, true, false, false, qs, hideQs, hideForeground,
                foreground, false, true, true)
            assertEquals(!(qs && hideQs) && !(foreground && hideForeground), d.showMirror)
            assertFalse(d.suppressOriginal)
        }
    }
}

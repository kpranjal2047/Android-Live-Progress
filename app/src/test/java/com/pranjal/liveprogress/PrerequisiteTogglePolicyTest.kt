package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Test

class PrerequisiteTogglePolicyTest {
    @Test fun permissionLossAndRestorationPreserveBothSavedChoices() {
        for (saved in listOf(false, true)) {
            assertEquals(ToggleState(saved, true), PrerequisiteTogglePolicy.state(saved, true))
            assertEquals(ToggleState(false, false), PrerequisiteTogglePolicy.state(saved, false))
            assertEquals(ToggleState(saved, true), PrerequisiteTogglePolicy.state(saved, true))
        }
    }
    @Test fun parentDisabledDoesNotForgetSavedSelection() {
        assertEquals(ToggleState(true, false), PrerequisiteTogglePolicy.state(true, true, false))
        assertEquals(ToggleState(false, false), PrerequisiteTogglePolicy.state(true, false, false))
    }
    @Test fun shizukuRequiresBothServiceAndPermission() {
        for (running in listOf(false, true)) for (granted in listOf(false, true)) {
            val available = running && granted
            assertEquals(ToggleState(available, available),
                SetupFlowPolicy.progressSuppressionToggleState(true, true, running, granted))
        }
    }
}

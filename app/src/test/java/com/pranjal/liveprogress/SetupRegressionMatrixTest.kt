package com.pranjal.liveprogress

import org.junit.Assert.*
import org.junit.Test

class SetupRegressionMatrixTest {
    @Test fun everyPermissionCombinationRequestsOnlyMissingRequiredAccessInOrder() {
        for (mask in 0 until 128) {
            fun flag(bit: Int) = mask and (1 shl bit) != 0
            val state = SetupAccessState(flag(0), flag(1), flag(2), flag(3), flag(4), flag(5), flag(6))
            val expected = buildList {
                if (!flag(0)) add(AutomaticSetupItem.NOTIFICATIONS)
                if (!flag(1)) add(AutomaticSetupItem.PROMOTED_NOTIFICATIONS)
                if (!flag(2)) add(AutomaticSetupItem.NOTIFICATION_LISTENER)
                if (flag(4) && !flag(3)) add(AutomaticSetupItem.ACCESSIBILITY)
            }
            assertEquals("mask=$mask", expected, SetupFlowPolicy.automaticItemsMissing(state))
            assertEquals(expected.firstOrNull()?.name, SetupFlowPolicy.firstMissingManualRequirement(state)?.name)
            assertEquals(expected, SetupFlowPolicy.remainingAutomaticItems(AutomaticSetupItem.entries, state))
        }
    }

    @Test fun repeatedAccessLossDoesNotOverwriteEitherSavedToggleValue() {
        for (saved in listOf(false, true)) for (parent in listOf(false, true)) {
            for (access in listOf(true, false, true, false, true)) {
                val state = PrerequisiteTogglePolicy.state(saved, access, parent)
                assertEquals(saved && access, state.checked)
                assertEquals(parent && access, state.enabled)
            }
        }
    }
}

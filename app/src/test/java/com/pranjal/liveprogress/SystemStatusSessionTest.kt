package com.pranjal.liveprogress

import org.junit.Assert.*
import org.junit.Test

class SystemStatusSessionTest {
    private fun SystemStatusSession.shown(enabled: Boolean = true, visibility: Boolean = false,
        locked: Boolean = false, screenOff: Boolean = false) =
        visible(enabled, visibility, locked, screenOff)

    @Test fun inactiveStatesAndDisabledSettingsNeverShow() {
        val state = SystemStatusSession()
        assertFalse(state.shown())
        state.update(true)
        assertTrue(state.shown())
        assertFalse(state.shown(enabled = false))
        state.update(false)
        assertFalse(state.shown())
    }
    @Test fun lockAndAodUseTheSameSetting() {
        val state = SystemStatusSession().apply { update(true) }
        for (enabled in listOf(false, true)) {
            assertEquals(enabled, state.shown(visibility = enabled, locked = true))
            assertEquals(enabled, state.shown(visibility = enabled, screenOff = true))
        }
    }
    @Test fun dismissalLastsForTheWholeActiveEpisode() {
        val state = SystemStatusSession().apply { update(true); dismiss() }
        state.update(true)
        assertFalse(state.shown())
        assertFalse(state.shown(enabled = false))
        assertFalse(state.shown(enabled = true))
        state.update(false)
        state.update(true)
        assertTrue(state.shown())
    }
    @Test fun simultaneousStatesAreIndependentAndReconnectStartsFresh() {
        val torch = SystemStatusSession().apply { update(true) }
        val hotspot = SystemStatusSession().apply { update(true) }
        torch.dismiss()
        assertFalse(torch.shown())
        assertTrue(hotspot.shown())
        assertTrue(SystemStatusSession().apply { update(true) }.shown())
    }
    @Test fun unlockedStatusRemainsVisibleRegardlessOfLockScreenPreference() {
        val state = SystemStatusSession().apply { update(true) }
        assertTrue(state.shown(visibility = false))
        assertTrue(state.shown(visibility = true))
    }
}

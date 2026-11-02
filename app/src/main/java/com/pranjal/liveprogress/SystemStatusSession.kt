package com.pranjal.liveprogress

/** One active device-state episode; dismissal ends only when the device state ends. */
class SystemStatusSession {
    var active = false
        private set
    private var dismissed = false

    fun update(active: Boolean) {
        this.active = active
        if (!active) dismissed = false
    }
    fun dismiss() { dismissed = true }
    fun visible(enabled: Boolean, showOnLockScreen: Boolean, locked: Boolean, screenOff: Boolean): Boolean {
        val lockedSurface = locked || screenOff
        return active && !dismissed && enabled && (!lockedSurface || showOnLockScreen)
    }
}

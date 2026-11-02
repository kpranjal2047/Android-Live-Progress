package com.pranjal.liveprogress

/** Derive presentation without changing the stored choice. */
object PrerequisiteTogglePolicy {
    fun state(saved: Boolean, accessGranted: Boolean, parentEnabled: Boolean = true) = ToggleState(
        checked = saved && accessGranted,
        enabled = parentEnabled && accessGranted
    )
}

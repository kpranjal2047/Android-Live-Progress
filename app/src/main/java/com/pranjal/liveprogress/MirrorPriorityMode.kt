package com.pranjal.liveprogress

enum class MirrorPriorityMode {
    DEFAULT,
    LOW
}

object MirrorPriorityPolicy {
    fun forSurface(locked: Boolean): MirrorPriorityMode {
        // AOD is a locked surface. Promoted live notifications posted on the low channel
        // are not reliably surfaced there by SystemUI, even when AOD visibility is enabled.
        return if (locked) MirrorPriorityMode.DEFAULT else MirrorPriorityMode.LOW
    }
}

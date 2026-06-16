package com.pranjal.liveprogress

enum class MirrorPriorityMode {
    DEFAULT,
    LOW
}

object MirrorPriorityPolicy {
    fun forSurface(
        locked: Boolean,
        screenOff: Boolean
    ): MirrorPriorityMode {
        return if (locked && !screenOff) MirrorPriorityMode.DEFAULT else MirrorPriorityMode.LOW
    }
}

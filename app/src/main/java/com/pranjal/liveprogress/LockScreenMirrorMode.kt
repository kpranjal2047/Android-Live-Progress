package com.pranjal.liveprogress

enum class LockScreenMirrorMode {
    ORIGINAL,
    MIRROR;

    val label: String
        get() = when (this) {
            ORIGINAL -> "Original"
            MIRROR -> "Mirror"
        }

    fun next(): LockScreenMirrorMode {
        return when (this) {
            ORIGINAL -> MIRROR
            MIRROR -> ORIGINAL
        }
    }
}

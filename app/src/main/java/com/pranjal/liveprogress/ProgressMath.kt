package com.pranjal.liveprogress

import kotlin.math.roundToInt

object ProgressMath {
    fun percent(progress: Int, max: Int, indeterminate: Boolean): Int? {
        if (indeterminate || max <= 0) return null
        return ((progress.coerceIn(0, max).toDouble() / max.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun shortText(progress: Int, max: Int, indeterminate: Boolean): String {
        return percent(progress, max, indeterminate)?.let { "$it%" }.orEmpty()
    }
}

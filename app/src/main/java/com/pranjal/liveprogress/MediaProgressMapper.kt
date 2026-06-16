package com.pranjal.liveprogress

data class MediaProgress(
    val max: Int,
    val progress: Int,
    val indeterminate: Boolean
)

object MediaProgressMapper {
    fun map(positionMs: Long, durationMs: Long): MediaProgress {
        if (durationMs <= 0L) {
            return MediaProgress(max = 0, progress = 0, indeterminate = true)
        }
        if (durationMs <= Int.MAX_VALUE) {
            val max = durationMs.toInt().coerceAtLeast(1)
            return MediaProgress(
                max = max,
                progress = positionMs.coerceIn(0L, durationMs).toInt(),
                indeterminate = false
            )
        }
        val clamped = positionMs.coerceIn(0L, durationMs)
        return MediaProgress(
            max = Int.MAX_VALUE,
            progress = ((clamped.toDouble() / durationMs.toDouble()) * Int.MAX_VALUE).toInt(),
            indeterminate = false
        )
    }
}

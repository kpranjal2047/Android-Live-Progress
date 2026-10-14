package com.pranjal.liveprogress

import java.text.BreakIterator
import java.util.Locale

/** Holds full text separately from the displayed window; one instance per mirror. */
class PillTextScroller {
    private var identity: Any? = null
    private var startedAt = 0L
    private var text = ""
    private var boundaries = listOf(0)

    fun render(sourceText: String, identity: Any, nowMs: Long): String {
        if (this.identity != identity) {
            this.identity = identity
            startedAt = nowMs
        }
        val normalized = sourceText.trim()
        if (text != normalized) {
            text = normalized
            boundaries = characterBoundaries(text)
        }
        val range = boundaries.size - 1 - WINDOW_SIZE
        if (range <= 0) return text
        val step = ((nowMs - startedAt).coerceAtLeast(0L) / UPDATE_MS % (range + 4)).toInt()
        val offset = (step - 2).coerceIn(0, range)
        return text.substring(boundaries[offset], boundaries[offset + WINDOW_SIZE])
    }

    companion object {
        const val WINDOW_SIZE = 7
        const val UPDATE_MS = 500L

        fun needsScrolling(text: String): Boolean =
            characterBoundaries(text.trim()).size > WINDOW_SIZE + 1

        private fun characterBoundaries(text: String): List<Int> {
            val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
            iterator.setText(text)
            val result = mutableListOf<Int>()
            var boundary = iterator.first()
            while (boundary != BreakIterator.DONE) {
                result.add(boundary)
                boundary = iterator.next()
            }
            return result
        }
    }
}

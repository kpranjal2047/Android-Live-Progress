package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressMathTest {
    @Test
    fun calculatesRoundedPercent() {
        assertEquals(43, ProgressMath.percent(progress = 43, max = 100, indeterminate = false))
        assertEquals(67, ProgressMath.percent(progress = 2, max = 3, indeterminate = false))
    }

    @Test
    fun clampsPercent() {
        assertEquals(0, ProgressMath.percent(progress = -1, max = 100, indeterminate = false))
        assertEquals(100, ProgressMath.percent(progress = 150, max = 100, indeterminate = false))
    }

    @Test
    fun returnsNullForIndeterminateOrInvalidMax() {
        assertNull(ProgressMath.percent(progress = 1, max = 10, indeterminate = true))
        assertNull(ProgressMath.percent(progress = 1, max = 0, indeterminate = false))
    }

    @Test
    fun shortTextHidesUnknownProgress() {
        assertEquals("50%", ProgressMath.shortText(progress = 5, max = 10, indeterminate = false))
        assertEquals("", ProgressMath.shortText(progress = 5, max = 10, indeterminate = true))
    }
}

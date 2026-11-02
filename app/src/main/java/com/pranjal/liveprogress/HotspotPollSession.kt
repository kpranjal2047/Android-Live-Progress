package com.pranjal.liveprogress

/** Changes eligibility only when polling should actually start or stop. */
class HotspotPollSession {
    var eligible: Boolean = false
        private set
    var generation: Int = 0
        private set
    var reading: Boolean = false
        private set
    var count: Int? = null
        private set

    fun updateEligibility(eligible: Boolean): Boolean {
        if (this.eligible == eligible) return false
        this.eligible = eligible
        generation++
        count = null
        return true
    }

    fun beginRead(): Int? {
        if (!eligible || reading) return null
        reading = true
        return generation
    }

    fun complete(requestGeneration: Int, result: Int?): Boolean {
        reading = false
        if (!eligible || requestGeneration != generation) return false
        count = result
        return true
    }
}

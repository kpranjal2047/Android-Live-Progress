package com.pranjal.liveprogress

enum class ShizukuAccessStatus { STOPPED, PERMISSION_REQUIRED, GRANTED }

object ShizukuAccessPolicy {
    fun canOfferSetup(installed: Boolean, available: Boolean, granted: Boolean): Boolean =
        installed && status(available, granted) != ShizukuAccessStatus.GRANTED

    fun status(available: Boolean, granted: Boolean): ShizukuAccessStatus = when {
        !available -> ShizukuAccessStatus.STOPPED
        granted -> ShizukuAccessStatus.GRANTED
        else -> ShizukuAccessStatus.PERMISSION_REQUIRED
    }
}

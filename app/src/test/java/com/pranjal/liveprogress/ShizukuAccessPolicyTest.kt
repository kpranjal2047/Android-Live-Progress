package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuAccessPolicyTest {
    @Test fun grantedAccessNeverOffersPermissionAgain() {
        assertEquals(ShizukuAccessStatus.GRANTED, ShizukuAccessPolicy.status(true, true))
    }
    @Test fun stoppedServiceNeedsStartEvenIfPermissionWasPreviouslyGranted() {
        assertEquals(ShizukuAccessStatus.STOPPED, ShizukuAccessPolicy.status(false, true))
        assertEquals(ShizukuAccessStatus.STOPPED, ShizukuAccessPolicy.status(false, false))
    }
    @Test fun runningServiceWithoutPermissionOffersGrant() {
        assertEquals(ShizukuAccessStatus.PERMISSION_REQUIRED, ShizukuAccessPolicy.status(true, false))
    }
    @Test fun grantRevokeStopAndRestartRefreshTheDisplayedState() {
        val sequence = listOf(true to false, true to true, true to false, false to false, true to true)
        assertEquals(listOf(ShizukuAccessStatus.PERMISSION_REQUIRED, ShizukuAccessStatus.GRANTED,
            ShizukuAccessStatus.PERMISSION_REQUIRED, ShizukuAccessStatus.STOPPED, ShizukuAccessStatus.GRANTED),
            sequence.map { (available, granted) -> ShizukuAccessPolicy.status(available, granted) })
    }
    @Test fun missingInstallationNeverOffersSetupEvenWithStaleServiceState() {
        for (available in listOf(false, true)) for (granted in listOf(false, true)) {
            assertEquals(false, ShizukuAccessPolicy.canOfferSetup(false, available, granted))
        }
    }
    @Test fun installedShizukuOffersSetupOnlyWhenAccessIsMissing() {
        assertEquals(true, ShizukuAccessPolicy.canOfferSetup(true, false, false))
        assertEquals(true, ShizukuAccessPolicy.canOfferSetup(true, false, true))
        assertEquals(true, ShizukuAccessPolicy.canOfferSetup(true, true, false))
        assertEquals(false, ShizukuAccessPolicy.canOfferSetup(true, true, true))
    }
}

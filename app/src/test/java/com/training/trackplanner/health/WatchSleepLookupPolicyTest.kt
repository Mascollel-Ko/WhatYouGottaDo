package com.training.trackplanner.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSleepLookupPolicyTest {
    @Test fun unknownDatabaseStateDoesNotTriggerWatchReads() {
        assertFalse(WatchSleepLookupPolicy.shouldRead(false, null))
    }
    @Test fun aDayWithManualOrImportedSleepMustNotTriggerWatchReads() {
        assertFalse(WatchSleepLookupPolicy.shouldRead(true, 7.5))
        assertFalse(WatchSleepLookupPolicy.shouldRead(true, 0.0))
    }
    @Test fun genuinelyEmptyLoadedDayCanReadTheWatch() {
        assertTrue(WatchSleepLookupPolicy.shouldRead(true, null))
    }
}

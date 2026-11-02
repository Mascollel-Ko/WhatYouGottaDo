package com.training.trackplanner.health

/**
 * A null emitted by an uninitialized Flow is not a verified empty condition.
 * Once sleep hours are stored, no automatic Health Connect lookup is necessary.
 */
internal object WatchSleepLookupPolicy {
    fun shouldRead(checkInLoaded: Boolean, savedSleepHours: Double?): Boolean =
        checkInLoaded && savedSleepHours == null
}

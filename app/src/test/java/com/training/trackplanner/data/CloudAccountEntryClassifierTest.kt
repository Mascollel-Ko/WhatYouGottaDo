package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAccountEntryClassifierTest {
    private val user = "11111111-1111-4111-8111-111111111111"
    private val other = "22222222-2222-4222-8222-222222222222"

    @Test fun freshWithoutCloudEnablesCloud() = assertEquals(
        CloudAccountEntryAction.ENABLE_CLOUD,
        classify(false, null, false)
    )

    @Test fun freshWithCloudRestoresCurrent() = assertEquals(
        CloudAccountEntryAction.AUTO_RESTORE_CURRENT,
        classify(false, null, true)
    )

    @Test fun guestWithoutCloudIsAdopted() = assertEquals(
        CloudAccountEntryAction.ADOPT_GUEST_LOCAL_DATA,
        classify(true, null, false)
    )

    @Test fun freshWithUnknownCloudPresenceWaitsForDiscovery() = assertEquals(
        CloudAccountEntryAction.REQUIRE_CLOUD_CURRENT_DISCOVERY,
        CloudAccountEntryClassifier.classify(
            CloudAccountEntrySnapshot(false, null, false, cloudCurrentKnown = false), user
        )
    )

    @Test fun guestWithCloudRequiresComparison() = assertEquals(
        CloudAccountEntryAction.REQUIRE_GUEST_CLOUD_COMPARISON,
        classify(true, null, true)
    )

    @Test fun guestWithUnknownCloudPresenceRequiresComparison() = assertEquals(
        CloudAccountEntryAction.REQUIRE_GUEST_CLOUD_COMPARISON,
        CloudAccountEntryClassifier.classify(
            CloudAccountEntrySnapshot(true, null, false, cloudCurrentKnown = false), user
        )
    )

    @Test fun sameAccountWithoutLocalDataResumes() = assertEquals(
        CloudAccountEntryAction.RESUME_SAME_ACCOUNT,
        classify(false, user, false)
    )

    @Test fun sameAccountWithLocalDataResumes() = assertEquals(
        CloudAccountEntryAction.RESUME_SAME_ACCOUNT,
        classify(true, user, true)
    )

    @Test fun differentAccountWithNoCloudStillRequiresArchive() = assertEquals(
        CloudAccountEntryAction.REQUIRE_ACCOUNT_ARCHIVE,
        classify(true, other, false)
    )

    @Test fun differentAccountWithCloudRequiresArchive() = assertEquals(
        CloudAccountEntryAction.REQUIRE_ACCOUNT_ARCHIVE,
        classify(false, other, true)
    )

    @Test fun userIdComparisonIsCaseInsensitive() = assertEquals(
        CloudAccountEntryAction.RESUME_SAME_ACCOUNT,
        classify(false, user.uppercase(), true)
    )

    private fun classify(local: Boolean, bound: String?, cloud: Boolean) =
        CloudAccountEntryClassifier.classify(
            CloudAccountEntrySnapshot(local, bound, cloud), user
        )
}

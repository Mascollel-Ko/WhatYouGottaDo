package com.training.trackplanner.data.v22

import com.training.trackplanner.data.CanonicalStrengthExposureCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BCoachStrengthCoveragePolicyTest {
    private val back = BCoachStrengthCoveragePolicy.BACK_SQUAT
    private val front = BCoachStrengthCoveragePolicy.FRONT_SQUAT
    private val dead = "barbell_deadlift"
    private val bench = "barbell_bench_press"
    private val pull = "ex_e41f4c2b"
    private val overhead = "ex_32219f7a"
    private val keys = setOf(back, front, dead, bench, pull, overhead)

    @Test fun fixedAnchorDenominatorAndNoCrossSubsidy() {
        val policy = BCoachStrengthCoveragePolicy
        val complete = mapOf(back to 4.0, dead to 4.0, bench to 4.0, pull to 4.0, overhead to 4.0)
        assertEquals(1.0, policy.meanCoverage(keys, complete, 4.0), 1e-12)
        assertEquals(0.2, policy.meanCoverage(keys, mapOf(back to 4.0), 4.0), 1e-12)
        assertEquals(0.2, policy.meanCoverage(keys, mapOf(back to 20.0), 4.0), 1e-12)
        assertEquals(0.8, policy.meanCoverage(keys, complete - dead, 4.0), 1e-12)
    }

    @Test fun frontPreferenceIsBoundedAndCannotBeBoughtWithExtraSets() {
        val policy = BCoachStrengthCoveragePolicy
        assertEquals(3.6, policy.deficitCredit(4.0, 4.0, 4.0, true), 1e-12)
        assertEquals(3.6, policy.deficitCredit(12.0, 12.0, 4.0, true), 1e-12)
        assertEquals(3.8, policy.deficitCredit(4.0, 2.0, 4.0, true), 1e-12)
        assertEquals(4.0, policy.deficitCredit(4.0, 4.0, 4.0, false), 1e-12)
        assertEquals(1.0, policy.meanCoverage(setOf(front), mapOf(front to 4.0), 4.0), 1e-12)
        assertTrue(policy.preferredKeys(listOf(front, back)).first() == back)
    }

    @Test fun variantsShareOneAnchorAndExactCapabilityIsUnchanged() {
        assertEquals(1, BCoachStrengthCoveragePolicy.anchors(setOf(back, front)).size)
        assertEquals(0.95, BCoachStrengthCoveragePolicy.meanCoverage(setOf(back, front),
            mapOf(back to 2.0, front to 2.0), 4.0), 1e-12)
        assertTrue(CanonicalStrengthExposureCapability.strengthExposureEligible(front, 5))
        assertTrue(!CanonicalStrengthExposureCapability.strengthExposureEligible(front, 8))
    }
}

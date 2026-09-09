package com.training.trackplanner.data.personalized

import org.junit.Assert.*
import org.junit.Test

class FrequencyDemandProvenanceTest {
    private val f = PostGenerationFixture
    @Test fun originalRankPrescriptionOwnersAndPartialFundingSurvive() {
        val snapshot = f.snapshot()
        val original = f.source("press", 4, "LOWER_KNEE", 100)
        val second = f.source("row", 3, "UPPER_PULL", 90)
        val trace = capacityCandidateTrace(snapshot, f.state(), listOf(original to false, second to false),
            listOf(original.copy(targetSets = 2)), PersonalizedPrescriptionPlanner())
        assertEquals(listOf(1, 2), trace.map { it.originalRank })
        assertEquals(listOf(2, 0), trace.map { it.fundedBaseUnits })
        assertEquals(listOf(2, 3), trace.map { it.remainingUnits })
        assertTrue(trace.all { it.rejectionReason == CandidateRejectionReason.FINITE_CAPACITY })
        assertEquals(setOf("LOWER_KNEE"), trace.first().item.representedGapCodes)
        assertEquals(PersonalizedPrescriptionPlanner().prescribe(snapshot, f.state().strengthIntent, original, original.style), trace.first().prescription)
    }
    @Test fun queueNeverRevivesSafetyRowsAndKeepsOriginalOrder() {
        val row = CapacityCandidateTrace(4, f.source("press"), f.rx(2), 0, false, CandidateRejectionReason.FINITE_CAPACITY)
        val frequency = PlanningFrequencyProvenance(WeeklyDosePlanner().resolve(f.state(), 3), 5, PlanningFrequencySource.EXPLICIT_USER)
        val trace = FrequencyDemandProvenance(frequency, listOf(row.copy(originalRank = 8),
            row.copy(originalRank = 6, rejectionReason = CandidateRejectionReason.SAFETY_OR_SEMANTIC_REJECTION), row), emptyList(), f.envelope())
        assertEquals(listOf(4, 8), trace.capacityRejected.map { it.originalRank })
        assertEquals(100, trace.toJson().getInt("computedCapacityUnits"))
        assertEquals(0, trace.toJson().getInt("baseAuthorizedUnits"))
    }
    @Test fun onlyExplicitHigherFrequencyQualifies() {
        val evidence = WeeklyDosePlanner().resolve(f.state(), 3).copy(recommendedDays = 3)
        assertFalse(PlanningFrequencyProvenance(evidence, 3, PlanningFrequencySource.EXPLICIT_USER).explicitIncrease)
        assertFalse(PlanningFrequencyProvenance(evidence.copy(recommendedDays = 4), 3, PlanningFrequencySource.EXPLICIT_USER).explicitIncrease)
        assertFalse(PlanningFrequencyProvenance(evidence, 5, PlanningFrequencySource.AUTO).explicitIncrease)
        assertTrue(PlanningFrequencyProvenance(evidence, 5, PlanningFrequencySource.EXPLICIT_USER).explicitIncrease)
    }
}

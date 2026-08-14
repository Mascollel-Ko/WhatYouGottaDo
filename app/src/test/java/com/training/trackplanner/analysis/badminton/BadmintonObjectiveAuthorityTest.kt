package com.training.trackplanner.analysis.badminton

import org.junit.Assert.assertThrows
import org.junit.Test

class BadmintonObjectiveAuthorityTest {
    @Test
    fun emptyEvidenceIsAllowedOnlyForExplicitUserApprovedRelations() {
        CanonicalBadmintonObjectiveCatalog.of(
            listOf(
                relation(
                    provenance = USER_APPROVED_BADMINTON_OBJECTIVE_PROVENANCE,
                    evidence = emptySet()
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            CanonicalBadmintonObjectiveCatalog.of(
                listOf(
                    relation(
                        provenance = "INHERITED_FROM_EXPLICIT_BADMINTON_RELATION_V1",
                        evidence = emptySet()
                    )
                )
            )
        }
    }

    private fun relation(
        provenance: String,
        evidence: Set<String>
    ) = CanonicalBadmintonObjectiveRelation(
        relationId = "test_relation",
        exerciseStableKey = "test_exercise",
        objective = BadmintonObjective.ANTI_ROTATION,
        transferLevel = BadmintonObjectiveTransferLevel.SUPPORTIVE,
        provenance = provenance,
        evidenceRelationKeys = evidence,
        reviewReason = "Reviewed test decision"
    )
}

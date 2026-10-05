package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalB5B6MaterialDemandBoundaryTest {
    private val powerOwner = StimulusPrescriptionOwnerIdentity("ex_314df428", "CANONICAL_STIMULUS_QUALITY_POWER")
    private val unrelatedLegacyOwner = StimulusPrescriptionOwnerIdentity("legacy_drill", "PERFORMANCE_CONTINUITY")

    private fun item(owner: StimulusPrescriptionOwnerIdentity, material: Boolean = true) = PlannedExercise(
        stableKey = owner.stableKey,
        role = owner.selectionRole,
        reason = "test",
        priority = 100,
        targetSets = 3,
        material = material
    )

    private fun demand(vararg items: PlannedExercise) = MaterialDemand(
        candidates = items.toList(), deferred = emptyMap(), audit = emptyMap()
    )

    @Test
    fun b5SelectedMaterialOwnerWithoutB6IsDeferredBeforeAllocation() {
        val owner = item(powerOwner)
        val unrelated = item(unrelatedLegacyOwner)
        val provider = ExactPrescriptionAuthorizationProvider { _, _ -> null }

        val filtered = filterCanonicalB5DemandWithoutExecutableB6(
            demand(owner, unrelated), setOf(powerOwner), provider
        )

        assertEquals(listOf(unrelated), filtered.candidates)
        assertEquals(
            CanonicalB5MaterialDemandDeferral.NO_EXECUTABLE_EXACT_B6_AUTHORITY.reasonCode,
            filtered.deferred["${powerOwner.stableKey}#${powerOwner.selectionRole}"]
        )
        assertEquals(filtered.deferred, filtered.audit)
    }

    @Test
    fun exactB6AuthorityRetainsTheB5Owner() {
        val owner = item(powerOwner)
        val prescription = PlannedPrescription(
            "authorized Power fixture",
            listOf(ProgramSetPrescription(1, 5, 0.0, 0)),
            restSeconds = 75,
            weightSource = "AUTHORIZED_TEST"
        )
        val provider = object : ExactPrescriptionAuthorizationProvider {
            override fun authorizedPrescriptionFor(item: PlannedExercise, requestedSets: Int): PlannedPrescription? =
                prescription.takeIf { StimulusPrescriptionOwnerIdentity(item.stableKey, item.role) == powerOwner }
                    ?.copy(sets = prescription.sets.take(requestedSets))

            override val authorizedOwners = mapOf(powerOwner to prescription)
        }

        val filtered = filterCanonicalB5DemandWithoutExecutableB6(demand(owner), setOf(powerOwner), provider)

        assertEquals(listOf(owner), filtered.candidates)
        assertTrue(filtered.deferred.isEmpty())
    }

    @Test
    fun sameStableKeyWithDifferentRoleDoesNotBorrowB6Authority() {
        val wrongRole = item(powerOwner.copy(selectionRole = "OTHER_POWER_ROLE"))
        val provider = ExactPrescriptionAuthorizationProvider { _, _ -> null }

        val filtered = filterCanonicalB5DemandWithoutExecutableB6(
            demand(wrongRole), setOf(powerOwner), provider
        )

        assertEquals(listOf(wrongRole), filtered.candidates)
        assertTrue(filtered.deferred.isEmpty())
    }

    @Test
    fun nonMaterialB5OwnerIsNotTreatedAsAnExecutableDemand() {
        val nonMaterial = item(powerOwner, material = false)
        val provider = ExactPrescriptionAuthorizationProvider { _, _ -> null }

        val filtered = filterCanonicalB5DemandWithoutExecutableB6(
            demand(nonMaterial), setOf(powerOwner), provider
        )

        assertEquals(listOf(nonMaterial), filtered.candidates)
        assertFalse(filtered.deferred.containsKey("${powerOwner.stableKey}#${powerOwner.selectionRole}"))
    }
}

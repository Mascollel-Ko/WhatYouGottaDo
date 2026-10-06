package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalB5B6MaterialDemandBoundaryTest {
    private val powerOwner = StimulusPrescriptionOwnerIdentity("ex_314df428", "CANONICAL_STIMULUS_QUALITY_POWER")
    private val taskOwner = StimulusPrescriptionOwnerIdentity("ex_33841b88", "CANONICAL_STIMULUS_TASK_ACCELERATION")
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
    fun rejectedQualityFamiliesRemainB5IdentitiesAndAreDeferredBeforeFrequencyExpansion() {
        val rejected = listOf(
            "squat" to (TrainableQuality.STRENGTH to "CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE"),
            "bench" to (TrainableQuality.STRENGTH to "B4_NUMERIC_AUTHORITY_DOES_NOT_AUTHORIZE_B6_CHANGE"),
            "row" to (TrainableQuality.HYPERTROPHY to "HYPERTROPHY_TARGET_NUMERIC_AUTHORITY_UNAVAILABLE"),
            "press" to (TrainableQuality.HYPERTROPHY to "PLANNED_RESISTANCE_LOAD_UNAVAILABLE")
        ).map { (stableKey, evidence) ->
            val (quality, refusal) = evidence
            val role = "CANONICAL_STIMULUS_QUALITY_${quality.name}"
            StimulusPrescriptionAuthorization(
                targetId = "QUALITY:${quality.name}", quality = quality,
                owner = StimulusPrescriptionOwner(stableKey, role),
                source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
                inputPrescription = null, plannedCompatibility = null, authorizedPrescription = null,
                status = StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
                reasonCodes = listOf(refusal)
            )
        }
        val plan = StimulusPrescriptionAuthorizationPlan(rejected)
        val selected = rejected.map { authorization ->
            val owner = requireNotNull(authorization.owner)
            StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole)
        }.toSet()
        assertEquals(selected, plan.provider().b5SelectedQualityOwners)

        val sourceRows = rejected.map { authorization ->
            val owner = requireNotNull(authorization.owner)
            item(StimulusPrescriptionOwnerIdentity(owner.stableKey, owner.selectionRole))
        }
        val filtered = filterCanonicalB5DemandWithoutExecutableB6(
            demand(*sourceRows.toTypedArray()), selected, plan.provider()
        )
        assertTrue(filtered.candidates.isEmpty())
        assertEquals(selected.size, filtered.deferred.size)
        assertEquals(selected.size, filtered.audit.size)
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

    @Test
    fun selectedTaskOwnerWithoutTaskB6IsDeferredBeforeLegacyFallbackMaterialization() {
        val selectedTask = item(taskOwner)
        val unrelated = item(unrelatedLegacyOwner)

        val filtered = filterCanonicalB5TaskDemandWithoutExecutableB6(
            demand(selectedTask, unrelated), setOf(taskOwner)
        )

        assertEquals(listOf(unrelated), filtered.candidates)
        assertEquals(
            CanonicalB5TaskMaterialDemandDeferral.NO_EXECUTABLE_TASK_B6_AUTHORITY.reasonCode,
            filtered.deferred["${taskOwner.stableKey}#${taskOwner.selectionRole}"]
        )
        assertEquals(filtered.deferred, filtered.audit)
    }

    @Test
    fun taskB5OwnerDoesNotTransferToSameStableKeyWithDifferentRole() {
        val otherRole = item(taskOwner.copy(selectionRole = "CANONICAL_STIMULUS_QUALITY_POWER"))

        val filtered = filterCanonicalB5TaskDemandWithoutExecutableB6(
            demand(otherRole), setOf(taskOwner)
        )

        assertEquals(listOf(otherRole), filtered.candidates)
        assertTrue(filtered.deferred.isEmpty())
    }

    @Test
    fun taskB5DeferralLeavesNonMaterialDemandAndUnselectedTaskOwnersAlone() {
        val nonMaterialSelected = item(taskOwner, material = false)
        val unselectedTask = item(StimulusPrescriptionOwnerIdentity("other_drill", "CANONICAL_STIMULUS_TASK_FOOTWORK"))

        val filtered = filterCanonicalB5TaskDemandWithoutExecutableB6(
            demand(nonMaterialSelected, unselectedTask), setOf(taskOwner)
        )

        assertEquals(listOf(nonMaterialSelected, unselectedTask), filtered.candidates)
        assertTrue(filtered.deferred.isEmpty())
    }
}

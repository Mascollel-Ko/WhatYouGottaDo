package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusPrescriptionMaterializationTest {
    private val key = "b6.materialized.squat"
    private val role = "B5_ROLE"
    private val snapshot = PlanningHistorySnapshot(
        cutoff = LocalDate.of(2026, 9, 23), allConfirmedSets = emptyList(), exercises = emptyMap(),
        metadata = emptyMap(), badmintonObjectives = emptyMap(), profilePrimaryGoal = "MIXED",
        strengthTrainingYears = 1.0, badmintonTrainingYears = 0.0, preferences = PersonalizedPlanningPreferences(),
        canonicalStrengthSignals = mapOf(key to CanonicalStrengthSignal(100.0, observationCount = 2))
    )

    private fun target(quality: TrainableQuality) = StimulusQualityTarget(
        quality = quality, strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
        priority = TargetPriority.PRIMARY, numericAuthority = StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
        baselineSource = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS, baselineConfidence = PlanningConfidence.HIGH,
        weeklyDirectUnitsTarget = null, weeklyDirectSessionsTarget = null,
        exposureWeekDirectUnitsReference = null, exposureWeekDirectSessionsReference = null,
        exposureWeekFrequencyReference = null, reasonCodes = emptyList(), evidence = emptyList()
    )

    private fun candidate(quality: TrainableQuality = TrainableQuality.STRENGTH) = StimulusSelectedCandidate(
        stableKey = key, coveredTargetIds = setOf("QUALITY:${quality.name}"), primaryTargetId = "QUALITY:${quality.name}",
        selectionReasons = emptyList(), currentPrescriptionCompatibility = "REALIZED_INCOMPATIBLE",
        targetSetsFromExistingPrescription = 2, selectionRole = role,
        probePrescriptionCompatibility = SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE
    )

    private fun selection(candidate: StimulusSelectedCandidate) = StimulusCandidateSelectionPlan(
        selectedCandidates = listOf(candidate), traces = emptyList(),
        materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
    )

    private fun prescription(reps: Int, load: Double) = PlannedPrescription(
        text = "test", sets = List(2) { ProgramSetPrescription(it + 1, reps, load, 0) },
        restSeconds = 120, weightSource = "TEST"
    )

    @Test
    fun strengthAuthorizationMaterializesOnlyAnExactOwnerAndAllowsPrefixSubset() {
        val owner = StimulusPrescriptionOwner(key, role, "B5_SELECTION")
        val authorized = prescription(5, 80.0)
        val plan = StimulusPrescriptionAuthorizationPlan(listOf(
            StimulusPrescriptionAuthorization(
                targetId = "QUALITY:STRENGTH", quality = TrainableQuality.STRENGTH, owner = owner,
                source = StimulusPrescriptionAuthorizationSource.B5_SELECTION_PROBE,
                inputPrescription = authorized, plannedCompatibility = null, authorizedPrescription = authorized,
                status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE
            )
        ))
        val provider = plan.provider()
        val item = PlannedExercise(key, role, "test", 1, targetSets = 2)
        val other = item.copy(role = "OTHER_ROLE")
        assertEquals(2, provider.authorizedPrescriptionFor(item)?.sets?.size)
        assertEquals(1, provider.prefixFor(item, 1)?.sets?.size)
        assertNull(provider.authorizedPrescriptionFor(other))
        assertTrue(plan.shadowOnly)
        assertTrue(!plan.productionAuthority)
    }

    @Test
    fun authorizationEngineUsesB61RulesAndKeepsHypertrophyAndProxiesNonExecutable() {
        val engine = StimulusPrescriptionAuthorizationEngine()
        val strengthPlan = engine.build(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            selection(candidate()), snapshot, mapOf(StimulusPrescriptionOwnerIdentity(key, role) to prescription(8, 80.0))
        )
        assertEquals(StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR, strengthPlan.authorizations.single().status)
        assertEquals(5, strengthPlan.authorizations.single().authorizedPrescription?.sets?.first()?.reps)

        val nonExecutable = engine.build(
            StimulusTargetPlan(listOf(target(TrainableQuality.HYPERTROPHY), target(TrainableQuality.POWER)), emptyList(), emptyList()),
            StimulusCandidateSelectionPlan(
                listOf(candidate(TrainableQuality.HYPERTROPHY)), emptyList(), MaterialDemand(emptyList(), emptyMap(), emptyMap())
            ), snapshot, emptyMap()
        )
        assertEquals(StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION,
            nonExecutable.authorizations.first { it.quality == TrainableQuality.HYPERTROPHY }.status)
        assertEquals(StimulusPrescriptionAuthorizationStatus.MODEL_UNAVAILABLE,
            nonExecutable.authorizations.first { it.quality == TrainableQuality.POWER }.status)
    }
}

package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusRealizationPrescriptionInputsTest {
    private val key = "barbell_back_squat"
    private val role = "CANONICAL_STIMULUS_QUALITY_STRENGTH"
    private val owner = StimulusPrescriptionOwnerIdentity(key, role)

    private fun target() = StimulusQualityTarget(
        quality = TrainableQuality.STRENGTH,
        strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
        priority = TargetPriority.PRIMARY,
        numericAuthority = StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
        baselineSource = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS,
        baselineConfidence = PlanningConfidence.HIGH,
        weeklyDirectUnitsTarget = null,
        weeklyDirectSessionsTarget = null,
        exposureWeekDirectUnitsReference = null,
        exposureWeekDirectSessionsReference = null,
        exposureWeekFrequencyReference = null,
        reasonCodes = emptyList(),
        evidence = emptyList()
    )

    private fun selectionPlan(key: String = this.key) = StimulusCandidateSelectionPlan(
        selectedCandidates = listOf(StimulusSelectedCandidate(
            stableKey = key,
            coveredTargetIds = setOf("QUALITY:STRENGTH"),
            primaryTargetId = "QUALITY:STRENGTH",
            selectionReasons = listOf("B4_TARGET_REQUESTED_IDENTITY"),
            currentPrescriptionCompatibility = "REALIZATION_UNCLASSIFIED",
            targetSetsFromExistingPrescription = 2,
            selectionRole = role
        )),
        traces = emptyList(),
        materialDemand = MaterialDemand(
            candidates = listOf(PlannedExercise(key, role, "B5 selected owner", 100, targetSets = 2)),
            deferred = emptyMap(),
            audit = emptyMap()
        )
    )

    private fun snapshot(history: List<PlanningSetRecord>) = PlanningHistorySnapshot(
        cutoff = LocalDate.of(2026, 9, 30),
        allConfirmedSets = history,
        exercises = mapOf(key to Exercise(key, "Barbell Back Squat", "STRENGTH", "RESISTANCE", "BARBELL")),
        metadata = emptyMap(),
        badmintonObjectives = emptyMap(),
        profilePrimaryGoal = "STRENGTH_GAIN",
        strengthTrainingYears = 2.0,
        badmintonTrainingYears = 0.0,
        preferences = PersonalizedPlanningPreferences(
            StrengthIntent.STRENGTH_PRIORITY, BadmintonPlanningIntent.DISABLED, FreeWeightWillingness.WILLING
        )
    )

    @Test
    fun canonicalPrescriptionContextUsesSelectedOwnerAndActualHistoryOnly() {
        val actualHistory = listOf(
            PlanningSetRecord(LocalDate.of(2026, 9, 25), key, "Back Squat", "STRENGTH", 1, 5, 100.0, 0, 8.0),
            PlanningSetRecord(LocalDate.of(2026, 9, 25), key, "Back Squat", "STRENGTH", 2, 5, 100.0, 0, 8.0)
        )
        val context = buildCanonicalPrescriptionContext(
            StimulusTargetPlan(listOf(target()), emptyList(), emptyList()),
            selectionPlan(), snapshot(actualHistory), StrengthIntent.STRENGTH_PRIORITY
        )
        val prescription = context.prescriptions.getValue(owner)

        assertEquals(setOf(owner), context.prescriptions.keys)
        assertEquals(setOf(owner), context.historyBackedOwners)
        assertTrue(prescription.sets.isNotEmpty())
        assertTrue(prescription.sets.all { it.reps == 5 && it.weightKg == 100.0 })
        assertTrue(prescription.weightSource.startsWith("CANONICAL_POSTERIOR_"))
    }

    @Test
    fun noHistoryContextKeepsTheExistingProvisionalPrescriptionWithoutInventingLoad() {
        val context = buildCanonicalPrescriptionContext(
            StimulusTargetPlan(listOf(target()), emptyList(), emptyList()),
            selectionPlan(), snapshot(emptyList()), StrengthIntent.STRENGTH_PRIORITY
        )
        val prescription = context.prescriptions.getValue(owner)

        assertTrue(context.historyBackedOwners.isEmpty())
        assertTrue(prescription.sets.all { it.reps == 8 && it.weightKg == 0.0 })
        assertEquals("PROVISIONAL_RPE_NO_INVENTED_LOAD", prescription.weightSource)
    }

    @Test
    fun realizationUsesOnlyB5OwnersAndFinalExperimentalMaterialization() {
        val planned = PlannedPrescription("history", listOf(ProgramSetPrescription(1, 5, 100.0, 0)), 180, "HISTORY")
        val context = CanonicalPrescriptionContext(mapOf(owner to planned), setOf(owner))
        val materialized = ProgramSkeletonItem(
            localId = "row", weekNumber = 1, dayOfWeek = 1, orderIndex = 1,
            exerciseStableKey = key, exerciseName = "Back Squat", category = "STRENGTH", restSeconds = 180,
            prescription = "experimental", setCount = 1, reps = 4, weightKg = 105.0, seconds = 0,
            selectionReason = "B6", weightSource = "AUTHORIZED", stableKey = key, selectionRole = role,
            setPrescriptions = listOf(ProgramSetPrescription(1, 4, 105.0, 0))
        )
        val unrelated = materialized.copy(exerciseStableKey = "unselected", stableKey = "unselected", selectionRole = "OTHER")
        val actual = buildStimulusRealizationPrescriptionInputs(selectionPlan(), context, listOf(materialized, unrelated))

        assertEquals(setOf(owner), actual.ownerKeys)
        assertEquals(setOf(owner), actual.currentPrescriptions.keys)
        assertEquals("AUTHORIZED", actual.currentPrescriptions.getValue(owner).weightSource)
        assertEquals(4, actual.currentPrescriptions.getValue(owner).sets.single().reps)
        assertFalse(actual.currentPrescriptions.containsKey(StimulusPrescriptionOwnerIdentity("unselected", "OTHER")))
    }
}

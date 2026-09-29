package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StimulusIncumbentPrescriptionBaselineTest {
    private fun prescription(text: String, reps: Int, load: Double, rest: Int, source: String) = PlannedPrescription(
        text = text,
        sets = listOf(
            ProgramSetPrescription(1, reps, load, 30, 7.5),
            ProgramSetPrescription(2, reps + 1, load - 2.5, 45, 8.0)
        ),
        restSeconds = rest,
        weightSource = source
    )

    @Test
    fun finalizedProjectionPreservesRolesFieldsInsertionOrderAndLegacyCollisionSemantics() {
        val main = StimulusPrescriptionOwnerIdentity("shared.exercise", "MAIN")
        val accessory = StimulusPrescriptionOwnerIdentity("shared.exercise", "ACCESSORY")
        val firstMain = prescription("main-first", 5, 80.0, 120, "RECORDED")
        val accessoryPrescription = prescription("accessory", 12, 25.0, 60, "ESTIMATED")
        val lastMain = prescription("main-last", 6, 77.5, 150, "RECENT")
        val rows = listOf(
            StimulusFinalizedPrescriptionRow(main, firstMain),
            StimulusFinalizedPrescriptionRow(accessory, accessoryPrescription),
            StimulusFinalizedPrescriptionRow(main, firstMain), // exact duplicate is discarded
            StimulusFinalizedPrescriptionRow(main, lastMain), // distinct collision overwrites the value
            StimulusFinalizedPrescriptionRow(main, lastMain)
        )
        val expectedLegacy = rows.asSequence().map { it.owner to it.prescription }.distinct().toList().toMap()
        val baseline = StimulusIncumbentPrescriptionBaseline.fromFinalizedRows(rows)

        assertEquals(expectedLegacy, baseline.prescriptions)
        assertEquals(listOf(main, accessory), baseline.prescriptions.keys.toList())
        assertEquals(lastMain, baseline.prescriptions.getValue(main))
        assertEquals(accessoryPrescription, baseline.prescriptions.getValue(accessory))
        assertEquals(2, baseline.prescriptions.size)
        assertNotEquals(main, accessory)
        assertEquals("main-last", baseline.prescriptions.getValue(main).text)
        assertEquals(listOf(1, 2), baseline.prescriptions.getValue(main).sets.map { it.setIndex })
        assertEquals(listOf(6, 7), baseline.prescriptions.getValue(main).sets.map { it.reps })
        assertEquals(listOf(77.5, 75.0), baseline.prescriptions.getValue(main).sets.map { it.weightKg })
        assertEquals(listOf(30, 45), baseline.prescriptions.getValue(main).sets.map { it.seconds })
        assertEquals(listOf(7.5, 8.0), baseline.prescriptions.getValue(main).sets.map { it.targetRpeMin })
        assertEquals(150, baseline.prescriptions.getValue(main).restSeconds)
        assertEquals("RECENT", baseline.prescriptions.getValue(main).weightSource)
    }

    @Test
    fun authorizationAndRealizationAreIdenticalForTypedAndLegacyBaselineMaps() {
        val owner = StimulusPrescriptionOwnerIdentity("baseline.squat", "PRIMARY")
        val current = prescription("current squat", 8, 70.0, 120, "HISTORY")
        val baseline = StimulusIncumbentPrescriptionBaseline.fromFinalizedRows(
            listOf(StimulusFinalizedPrescriptionRow(owner, current))
        )
        val legacy = listOf(owner to current).distinct().toList().toMap()
        val targetPlan = StimulusTargetPlan(
            listOf(StimulusQualityTarget(
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
                reasonCodes = emptyList(), evidence = emptyList()
            )), emptyList(), emptyList()
        )
        val candidate = StimulusSelectedCandidate(
            stableKey = owner.stableKey,
            coveredTargetIds = setOf("QUALITY:STRENGTH"),
            primaryTargetId = "QUALITY:STRENGTH",
            selectionReasons = emptyList(),
            currentPrescriptionCompatibility = "REALIZED_INCOMPATIBLE",
            targetSetsFromExistingPrescription = 2,
            selectionRole = owner.selectionRole,
            probePrescriptionCompatibility = SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE
        )
        val selection = StimulusCandidateSelectionPlan(
            selectedCandidates = listOf(candidate), traces = emptyList(),
            materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
        )
        val snapshot = PlanningHistorySnapshot(
            cutoff = java.time.LocalDate.of(2026, 9, 23), allConfirmedSets = emptyList(), exercises = emptyMap(),
            metadata = emptyMap(), badmintonObjectives = emptyMap(), profilePrimaryGoal = "MIXED",
            strengthTrainingYears = 1.0, badmintonTrainingYears = 0.0,
            preferences = PersonalizedPlanningPreferences(),
            canonicalStrengthSignals = mapOf(owner.stableKey to CanonicalStrengthSignal(100.0, observationCount = 2))
        )

        val authorizationEngine = StimulusPrescriptionAuthorizationEngine()
        val typedAuthorization = authorizationEngine.build(targetPlan, selection, snapshot, baseline.prescriptions)
        val legacyAuthorization = authorizationEngine.build(targetPlan, selection, snapshot, legacy)
        assertEquals(legacyAuthorization, typedAuthorization)

        val realizationEngine = StimulusPrescriptionRealizationPlanEngine()
        val typedRealization = realizationEngine.build(targetPlan, selection, snapshot, baseline.prescriptions)
        val legacyRealization = realizationEngine.build(targetPlan, selection, snapshot, legacy)
        assertEquals(legacyRealization, typedRealization)
        assertEquals(legacyRealization.resolutions, typedRealization.resolutions)
    }
}

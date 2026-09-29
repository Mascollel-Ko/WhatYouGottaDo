package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusRealizationPrescriptionInputsTest {
    private val directKey = "direct.exercise"
    private val sharedKey = "shared.exercise"
    private val newKey = "new.exercise"

    private fun item(
        key: String,
        role: String,
        reps: Int,
        weight: Double,
        label: String,
        weightSource: String = "TEST"
    ) = ProgramSkeletonItem(
        localId = label,
        weekNumber = 1,
        dayOfWeek = 1,
        orderIndex = 1,
        exerciseStableKey = key,
        exerciseName = label,
        category = "STRENGTH",
        restSeconds = 120,
        prescription = "$label prescription",
        setCount = 2,
        reps = reps,
        weightKg = weight,
        seconds = 0,
        selectionReason = label,
        weightSource = weightSource,
        stableKey = key,
        selectionRole = role,
        setPrescriptions = listOf(
            ProgramSetPrescription(1, reps, weight, 0),
            ProgramSetPrescription(2, reps, weight, 0)
        )
    )

    private fun planned(item: ProgramSkeletonItem) = PlannedPrescription(
        text = item.prescription,
        sets = item.setPrescriptions,
        restSeconds = item.restSeconds,
        weightSource = item.weightSource
    )

    private val controlItems = listOf(
        item(directKey, "MAIN", 5, 90.0, "direct main", "CONTROL"),
        item(sharedKey, "MAIN", 5, 85.0, "shared main", "CONTROL"),
        item(sharedKey, "ACCESSORY", 12, 30.0, "shared accessory", "CONTROL"),
        item(sharedKey, "ACCESSORY", 12, 30.0, "shared accessory", "CONTROL")
    )

    private val experimentalItems = listOf(
        item(newKey, "PRIMARY", 8, 50.0, "new first", "EXPERIMENTAL_FIRST"),
        item(newKey, "PRIMARY", 8, 55.0, "new final", "EXPERIMENTAL_FINAL"),
        item(sharedKey, "MAIN", 5, 110.0, "shared main experimental", "EXPERIMENTAL"),
        item(sharedKey, "ACCESSORY", 10, 40.0, "shared accessory experimental", "EXPERIMENTAL"),
        item("non-owner.exercise", "OTHER", 8, 25.0, "filtered", "EXPERIMENTAL")
    )

    private val incumbentSeed = StimulusIncumbentIdentitySeed.fromFinalizedOwners(
        controlItems.map { StimulusIncumbentIdentity(it.exerciseStableKey, it.selectionRole) }
    )
    private val prescriptionBaseline = StimulusIncumbentPrescriptionBaseline.fromFinalizedRows(
        controlItems.map {
            StimulusFinalizedPrescriptionRow(
                StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole),
                planned(it)
            )
        }
    )

    private fun target(quality: TrainableQuality) = StimulusQualityTarget(
        quality = quality,
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

    private fun candidate(key: String, role: String, targetId: String) = StimulusSelectedCandidate(
        stableKey = key,
        coveredTargetIds = setOf(targetId),
        primaryTargetId = targetId,
        selectionReasons = emptyList(),
        currentPrescriptionCompatibility = "REALIZED_INCOMPATIBLE",
        targetSetsFromExistingPrescription = 2,
        selectionRole = role,
        probePrescriptionCompatibility = SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE
    )

    private val selectionPlan = StimulusCandidateSelectionPlan(
        selectedCandidates = listOf(
            candidate(sharedKey, "MAIN", "QUALITY:STRENGTH"),
            candidate(newKey, "PRIMARY", "QUALITY:HYPERTROPHY")
        ),
        traces = listOf(
            StimulusCandidateSelectionTrace(
                targetId = "QUALITY:STRENGTH",
                strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
                priority = TargetPriority.PRIMARY,
                controlDirectCapabilityIdentities = listOf(directKey, sharedKey, directKey),
                selectionRequired = false,
                candidatePool = emptyList(),
                selectedStableKey = null,
                coveredByPreviouslySelectedStableKey = null
            )
        ),
        materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
    )

    private fun legacyOwnerKeys(): Set<StimulusPrescriptionOwnerIdentity> = buildSet {
        selectionPlan.selectedCandidates.forEach { candidate ->
            add(StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole))
        }
        selectionPlan.traces.flatMap { it.controlDirectCapabilityIdentities }.forEach { stableKey ->
            controlItems.filter { it.exerciseStableKey == stableKey }.forEach { item ->
                add(StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole))
            }
        }
    }

    private fun legacyCurrentPrescriptions(): Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription> {
        val ownerKeys = legacyOwnerKeys()
        return (experimentalItems + controlItems)
            .asSequence()
            .map { item -> StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole) to planned(item) }
            .filter { it.first in ownerKeys }
            .toList()
            .associateBy({ it.first }, { it.second })
    }

    @Test
    fun ownerAndMergedPrescriptionInputsMatchLegacyValuesAndIterationOrder() {
        val legacyOwners = legacyOwnerKeys()
        val legacyPrescriptions = legacyCurrentPrescriptions()
        val actual = buildStimulusRealizationPrescriptionInputs(
            selectionPlan = selectionPlan,
            incumbentSeed = incumbentSeed,
            prescriptionBaseline = prescriptionBaseline,
            experimentalItems = experimentalItems
        )

        assertEquals(legacyOwners, actual.ownerKeys)
        assertEquals(legacyOwners.toList(), actual.ownerKeys.toList())
        assertEquals(legacyPrescriptions, actual.currentPrescriptions)
        assertEquals(legacyPrescriptions.keys.toList(), actual.currentPrescriptions.keys.toList())
        assertEquals(
            listOf(
                StimulusPrescriptionOwnerIdentity(sharedKey, "MAIN"),
                StimulusPrescriptionOwnerIdentity(newKey, "PRIMARY"),
                StimulusPrescriptionOwnerIdentity(directKey, "MAIN"),
                StimulusPrescriptionOwnerIdentity(sharedKey, "ACCESSORY")
            ),
            actual.ownerKeys.toList()
        )
        assertEquals(
            listOf(
                StimulusPrescriptionOwnerIdentity(newKey, "PRIMARY"),
                StimulusPrescriptionOwnerIdentity(sharedKey, "MAIN"),
                StimulusPrescriptionOwnerIdentity(sharedKey, "ACCESSORY"),
                StimulusPrescriptionOwnerIdentity(directKey, "MAIN")
            ),
            actual.currentPrescriptions.keys.toList()
        )
        assertEquals(planned(controlItems[1]), actual.currentPrescriptions.getValue(StimulusPrescriptionOwnerIdentity(sharedKey, "MAIN")))
        assertEquals(planned(controlItems[2]), actual.currentPrescriptions.getValue(StimulusPrescriptionOwnerIdentity(sharedKey, "ACCESSORY")))
        assertEquals(planned(experimentalItems[1]), actual.currentPrescriptions.getValue(StimulusPrescriptionOwnerIdentity(newKey, "PRIMARY")))
        assertFalse(actual.currentPrescriptions.containsKey(StimulusPrescriptionOwnerIdentity("non-owner.exercise", "OTHER")))
        assertTrue(incumbentSeed.owners.contains(StimulusIncumbentIdentity(sharedKey, "MAIN")))
        assertTrue(incumbentSeed.owners.contains(StimulusIncumbentIdentity(sharedKey, "ACCESSORY")))
    }

    @Test
    fun completeRealizationPlansRemainEqualForSingleAndCombinedTargetPlans() {
        val snapshot = PlanningHistorySnapshot(
            cutoff = LocalDate.of(2026, 9, 23),
            allConfirmedSets = emptyList(),
            exercises = emptyMap(),
            metadata = emptyMap(),
            badmintonObjectives = emptyMap(),
            profilePrimaryGoal = "MIXED",
            strengthTrainingYears = 1.0,
            badmintonTrainingYears = 0.0,
            preferences = PersonalizedPlanningPreferences(),
            canonicalStrengthSignals = mapOf(
                directKey to CanonicalStrengthSignal(100.0, observationCount = 2),
                sharedKey to CanonicalStrengthSignal(100.0, observationCount = 2),
                newKey to CanonicalStrengthSignal(100.0, observationCount = 2)
            )
        )
        val targetPlans = listOf(
            StimulusTargetPlan(listOf(target(TrainableQuality.STRENGTH)), emptyList(), emptyList()),
            StimulusTargetPlan(listOf(target(TrainableQuality.HYPERTROPHY)), emptyList(), emptyList()),
            StimulusTargetPlan(
                listOf(target(TrainableQuality.STRENGTH), target(TrainableQuality.HYPERTROPHY)),
                emptyList(),
                emptyList()
            )
        )
        val actualInputs = buildStimulusRealizationPrescriptionInputs(
            selectionPlan = selectionPlan,
            incumbentSeed = incumbentSeed,
            prescriptionBaseline = prescriptionBaseline,
            experimentalItems = experimentalItems
        )
        val engine = StimulusPrescriptionRealizationPlanEngine()

        targetPlans.forEach { targetPlan ->
            assertEquals(
                engine.build(targetPlan, selectionPlan, snapshot, legacyCurrentPrescriptions()),
                engine.build(targetPlan, selectionPlan, snapshot, actualInputs.currentPrescriptions)
            )
        }

        val directSelection = selectionPlan.copy(
            selectedCandidates = emptyList(),
            traces = listOf(selectionPlan.traces.single().copy(controlDirectCapabilityIdentities = listOf(directKey)))
        )
        val directTarget = targetPlans.first()
        val directLegacy = legacyCurrentPrescriptions().filterKeys { it.stableKey == directKey }
        val directActual = actualInputs.currentPrescriptions.filterKeys { it.stableKey == directKey }
        val legacyResolution = engine.build(directTarget, directSelection, snapshot, directLegacy).resolutions.single()
        val actualResolution = engine.build(directTarget, directSelection, snapshot, directActual).resolutions.single()
        assertEquals(legacyResolution, actualResolution)
        assertEquals("CONTROL_EXISTING_DIRECT_IDENTITY", actualResolution.owner?.source)
    }
}

package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalStrengthExposureCapability
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

    private fun target(quality: TrainableQuality = TrainableQuality.STRENGTH) = StimulusQualityTarget(
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

    private fun selectionPlan(
        key: String = this.key,
        quality: TrainableQuality = TrainableQuality.STRENGTH,
        role: String = if (quality == TrainableQuality.STRENGTH) this.role else "CANONICAL_STIMULUS_QUALITY_${quality.name}"
    ) = StimulusCandidateSelectionPlan(
        selectedCandidates = listOf(StimulusSelectedCandidate(
            stableKey = key,
            coveredTargetIds = setOf("QUALITY:${quality.name}"),
            primaryTargetId = "QUALITY:${quality.name}",
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

    private fun snapshot(
        history: List<PlanningSetRecord>,
        reviewed: Boolean = false,
        canonicalStrengthSignals: Map<String, CanonicalStrengthSignal> = emptyMap()
    ) = PlanningHistorySnapshot(
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
        ),
        canonicalStrengthSignals = canonicalStrengthSignals,
        stimulusExposureLedger = if (reviewed) StimulusExposureLedger(
            facetProfilesByStableKey = emptyMap(),
            setObservations = history.mapIndexed { index, row ->
                val directQuality = when {
                    CanonicalStrengthExposureCapability.strengthPossible(row.stableKey) && row.reps in 1..6 -> TrainableQuality.STRENGTH
                    row.reps in 7..15 -> TrainableQuality.HYPERTROPHY
                    else -> null
                }
                val classification = RealizedStimulusClassifier.classify(RealizedStimulusInput(
                    stableKey = row.stableKey,
                    date = row.date,
                    activityKind = PlannedActivityKind.RESISTANCE,
                    reps = row.reps,
                    resolvedLoadKg = row.weightKg.takeIf { it > 0.0 },
                    rpe = row.rpe,
                    directQualities = setOfNotNull(directQuality),
                    reviewedIdentity = true,
                    reference1RmKg = canonicalStrengthSignals[row.stableKey]?.posteriorMedianKg
                ))
                StimulusSetObservation(
                    source = StimulusSourceRef(index.toLong() + 1L, "reviewed-${index + 1}", index.toLong() + 1L,
                        row.setIndex, "reviewed-${row.date}", row.date, row.stableKey),
                    activityKind = PlannedActivityKind.RESISTANCE,
                    reps = row.reps,
                    weightKg = row.weightKg,
                    seconds = row.seconds,
                    rpe = row.rpe,
                    realizedPrescriptionClass = classification.toLegacyClass(),
                    facetProfileKey = key,
                    classificationAuthority = if (classification.isUnclassified) StimulusClassificationAuthority.UNCLASSIFIED
                        else StimulusClassificationAuthority.REVIEWED_CANONICAL,
                    realizedStimulusClassification = classification
                )
            },
            courtObservations = emptyList(), cutoff = LocalDate.of(2026, 9, 30)
        ) else StimulusExposureLedger.EMPTY
    )

    @Test
    fun canonicalPrescriptionContextUsesSelectedOwnerAndActualHistoryOnly() {
        val actualHistory = listOf(
            PlanningSetRecord(LocalDate.of(2026, 9, 25), key, "Back Squat", "STRENGTH", 1, 8, 40.0, 0, 8.0),
            PlanningSetRecord(LocalDate.of(2026, 9, 25), key, "Back Squat", "STRENGTH", 2, 8, 40.0, 0, 8.0),
            PlanningSetRecord(LocalDate.of(2026, 9, 5), key, "Back Squat", "STRENGTH", 1, 5, 40.0, 3, 8.0),
            PlanningSetRecord(LocalDate.of(2026, 9, 5), key, "Back Squat", "STRENGTH", 2, 5, 40.0, 3, 8.0)
        )
        val context = buildCanonicalPrescriptionContext(
            StimulusTargetPlan(listOf(target()), emptyList(), emptyList()),
            selectionPlan(), snapshot(actualHistory, reviewed = true,
                canonicalStrengthSignals = mapOf(key to CanonicalStrengthSignal(50.0, observationCount = 2))),
            StrengthIntent.STRENGTH_PRIORITY
        )
        val prescription = context.prescriptions.getValue(owner)

        assertEquals(setOf(owner), context.prescriptions.keys)
        assertEquals(setOf(owner), context.historyBackedOwners)
        assertTrue(prescription.sets.isNotEmpty())
        assertTrue(prescription.sets.all { it.reps == 5 && it.weightKg == 40.0 && it.seconds == 3 })
        assertEquals("TARGET_COMPATIBLE_PERSONAL_STRENGTH_HISTORY", prescription.weightSource)
        assertTrue(context.historyBackedAuthorities.contains(
            StimulusPrescriptionAuthorityIdentity(key, role, TrainableQuality.STRENGTH)
        ))
    }

    @Test
    fun hypertrophyContextPrefersReviewedSameExerciseHistoryOverRecentIncompatibleSets() {
        val hRole = "CANONICAL_STIMULUS_QUALITY_HYPERTROPHY"
        val actualHistory = listOf(
            PlanningSetRecord(LocalDate.of(2026, 9, 25), key, "Back Squat", "STRENGTH", 1, 5, 40.0, 0, 8.0),
            PlanningSetRecord(LocalDate.of(2026, 9, 5), key, "Back Squat", "STRENGTH", 1, 10, 35.0, 2, 8.0)
        )
        val context = buildCanonicalPrescriptionContext(
            StimulusTargetPlan(listOf(target(TrainableQuality.HYPERTROPHY)), emptyList(), emptyList()),
            selectionPlan(quality = TrainableQuality.HYPERTROPHY, role = hRole),
            snapshot(actualHistory, reviewed = true), StrengthIntent.HYPERTROPHY_PRIORITY
        )
        val identity = StimulusPrescriptionOwnerIdentity(key, hRole)
        val authority = StimulusPrescriptionAuthorityIdentity(key, hRole, TrainableQuality.HYPERTROPHY)
        val prescription = context.prescriptionsByQuality.getValue(authority)

        assertTrue(prescription.sets.all { it.reps == 10 && it.weightKg == 35.0 && it.seconds == 2 && it.targetRpeMin == 7.0 })
        assertEquals("TARGET_COMPATIBLE_PERSONAL_HYPERTROPHY_HISTORY", prescription.weightSource)
        assertTrue(identity in context.historyBackedOwners)
        assertTrue(authority in context.historyBackedAuthorities)
    }

    @Test
    fun incompatibleOnlyHistoryDoesNotBecomeStrengthHistoryAuthorityWithoutCanonicalStrengthSupport() {
        val onlyHypertrophyHistory = listOf(
            PlanningSetRecord(LocalDate.of(2026, 9, 25), key, "Back Squat", "STRENGTH", 1, 10, 40.0, 0, 8.0)
        )
        val context = buildCanonicalPrescriptionContext(
            StimulusTargetPlan(listOf(target()), emptyList(), emptyList()),
            selectionPlan(), snapshot(onlyHypertrophyHistory, reviewed = true), StrengthIntent.STRENGTH_PRIORITY
        )
        val identity = StimulusPrescriptionAuthorityIdentity(key, role, TrainableQuality.STRENGTH)
        val authorization = StimulusPrescriptionAuthorizationEngine().build(
            StimulusTargetPlan(listOf(target()), emptyList(), emptyList()),
            selectionPlan(), snapshot(onlyHypertrophyHistory, reviewed = true), context
        ).authorizations.single()

        assertTrue(context.historyBackedOwners.isEmpty())
        assertTrue(identity !in context.historyBackedAuthorities)
        assertEquals(StimulusPrescriptionAuthorizationStatus.NO_EXECUTABLE_AUTHORIZATION, authorization.status)
        assertTrue(authorization.reasonCodes.any { it == "CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE" })
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
        val context = CanonicalPrescriptionContext(mapOf(owner to planned), setOf(owner), mapOf(
            StimulusPrescriptionAuthorityIdentity(key, role, TrainableQuality.STRENGTH) to planned
        ), setOf(StimulusPrescriptionAuthorityIdentity(key, role, TrainableQuality.STRENGTH)))
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
        assertEquals("AUTHORIZED", actual.currentPrescriptionsByQuality.values.single().weightSource)
        assertFalse(actual.currentPrescriptions.containsKey(StimulusPrescriptionOwnerIdentity("unselected", "OTHER")))
    }
}

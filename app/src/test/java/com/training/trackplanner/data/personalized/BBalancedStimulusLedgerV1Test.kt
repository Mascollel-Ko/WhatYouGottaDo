package com.training.trackplanner.data.personalized

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.core.CanonicalCoreProfile
import com.training.trackplanner.analysis.core.CoreClass
import com.training.trackplanner.analysis.core.CoreDirectTarget
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.ExerciseRoleRelationCatalog
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BBalancedStimulusLedgerV1Test {
    private val cutoff = LocalDate.of(2026, 10, 18)
    private val builder = BBalancedStimulusLedgerV1Builder()

    @Test
    fun oneBenchSetCountsOneDirectChestAndSeparateIndirectTricepsWithoutSetMultiplication() {
        val key = "bench_fixture"
        val profile = profile(key, withHypertrophy = true)
        val source = observation(key, 1, classification = realized(RealizedStimulusKind.HYPERTROPHY_LIKE))
        val ledger = build(
            listOf(source), listOf(profile),
            listOf(muscle(key, "CHEST", "PRIMARY"), muscle(key, "TRICEPS", "SECONDARY"))
        )
        val window = ledger.summary(StimulusExposureWindow.RECENT_7D)
        assertEquals(1, window.observedPhysicalSets)
        assertEquals(1, window.realizedHypertrophyPhysicalSets)
        assertEquals(mapOf("CHEST" to 1), window.directHypertrophySetsByMuscle)
        assertEquals(mapOf("TRICEPS" to 1), window.indirectMuscleParticipationOccurrences)
        assertEquals(0, window.realizedStrengthSets)
        assertEquals(0, window.observedDirectCorePhysicalSets)
        assertTrue(ledger.shadowOnly)
        assertFalse(ledger.prescriptionAuthority)
        assertFalse(ledger.selectionAuthority)
        assertFalse(ledger.schedulingAuthority)
    }

    @Test
    fun bBalancedConfirmedRealizationFeedsActualB5HistoryEvidenceWithoutGrantingExtraDose() {
        val key = "bench_fixture"
        val resolved = observation(key, 1, classification = realized(RealizedStimulusKind.HYPERTROPHY_LIKE))
        val unknown = observation(key, 2, classification = RealizedStimulusClassification.UNCLASSIFIED)
        val ledger = build(
            listOf(resolved, unknown), listOf(profile(key, withHypertrophy = true)),
            listOf(muscle(key, "CHEST", "PRIMARY"), muscle(key, "TRICEPS", "SECONDARY"))
        )
        assertEquals(setOf(key), ledger.realizedHistoryStableKeys(TrainableQuality.HYPERTROPHY))
        assertEquals(emptySet<String>(), ledger.realizedHistoryStableKeys(TrainableQuality.STRENGTH))
        assertEquals(emptySet<String>(), ledger.realizedHistoryStableKeys(TrainableQuality.POWER))
        assertEquals(2, ledger.summary(StimulusExposureWindow.RECENT_7D).observedPhysicalSets)
        assertEquals(1, ledger.summary(StimulusExposureWindow.RECENT_7D).realizedHypertrophyPhysicalSets)
        assertFalse(ledger.prescriptionAuthority)
        assertFalse(ledger.selectionAuthority)
    }

    @Test
    fun bBalancedUnclassifiedHistoryCannotClaimB5DirectHypertrophyEvidence() {
        val key = "press_fixture"
        val ledger = build(
            listOf(observation(key, 1, classification = RealizedStimulusClassification.UNCLASSIFIED)),
            listOf(profile(key, withHypertrophy = true)),
            listOf(muscle(key, "CHEST", "PRIMARY"))
        )
        assertEquals(emptySet<String>(), ledger.realizedHistoryStableKeys(TrainableQuality.HYPERTROPHY))
    }

    @Test
    fun reviewedSetWithMissingEffortAuthorityIsUnknownNotZeroOrEffectiveDose() {
        val key = "press_fixture"
        val ledger = build(
            listOf(observation(key, 1, classification = RealizedStimulusClassification.UNCLASSIFIED)),
            listOf(profile(key, withHypertrophy = true)),
            listOf(muscle(key, "CHEST", "PRIMARY"))
        )
        val row = ledger.sets.single()
        assertEquals(BStimulusEvidenceState.UNRESOLVED, row.hypertrophy)
        assertEquals(setOf("CHEST"), row.unresolvedHypertrophyMuscles)
        assertEquals(emptySet<String>(), row.directHypertrophyMuscles)
        assertEquals(0, ledger.summary(StimulusExposureWindow.RECENT_7D).realizedHypertrophyPhysicalSets)
        assertEquals(1, ledger.summary(StimulusExposureWindow.RECENT_7D).physicalSetsWithUnresolvedEvidence)
    }

    @Test
    fun onlyExactApprovedStrengthIdentityReceivesStrengthCredit() {
        val approved = "barbell_back_squat"
        val unsupported = "generic_heavy_row"
        val strength = realized(RealizedStimulusKind.STRENGTH_LIKE)
        val ledger = build(
            listOf(observation(approved, 1, classification = strength, reps = 5),
                observation(unsupported, 2, classification = strength, reps = 5)),
            listOf(profile(approved), profile(unsupported)),
            emptyList()
        )
        val window = ledger.summary(StimulusExposureWindow.RECENT_7D)
        assertEquals(1, window.realizedStrengthSets)
        assertEquals(mapOf("SQUAT" to 1), window.strengthSetsByAnchor)
        assertEquals(BStimulusEvidenceState.NOT_APPLICABLE, ledger.sets.single { it.source.stableKey == unsupported }.strength)
    }

    @Test
    fun multipleCoreTasksNeverBecomeMultiplePhysicalCoreSets() {
        val key = "ex_d5bdffe1"
        val direct = profile(key, core = CanonicalCoreProfile(key, CoreClass.DIRECT, CoreDirectTarget.BRACING))
        val overlay = BBalancedTaskCatalog.of(listOf(
            task("1", key, "DYNAMIC_TRUNK_STABILIZATION"),
            task("2", key, "ANTI_EXTENSION")
        ))
        val ledger = build(
            listOf(observation(key, 1, reps = 10, classification = RealizedStimulusClassification.UNCLASSIFIED)),
            listOf(direct), emptyList(), overlay
        )
        val row = ledger.sets.single()
        assertEquals(BStimulusEvidenceState.OBSERVED_DIRECT_CORE, row.core)
        assertEquals(setOf("ANTI_EXTENSION", "DYNAMIC_TRUNK_STABILIZATION"), row.reviewedCoreTasks)
        val window = ledger.summary(StimulusExposureWindow.RECENT_7D)
        assertEquals(1, window.observedPhysicalSets)
        assertEquals(1, window.observedDirectCorePhysicalSets)
        assertEquals(2, window.coreTaskObservations.size)
        assertEquals(1, window.coreTaskObservations.getValue("DYNAMIC_TRUNK_STABILIZATION"))
        assertEquals(1, window.coreTaskObservations.getValue("ANTI_EXTENSION"))
        assertEquals(0, window.realizedHypertrophyPhysicalSets)
    }

    @Test
    fun copenhagenReviewRequiredRemainsHiddenAndCannotBecomeDirectCore() {
        val key = "ex_a8385c4a"
        val hidden = profile(key, core = CanonicalCoreProfile(key, CoreClass.HIDDEN_HIGH, null))
        val overlay = BBalancedTaskCatalog.of(listOf(
            task("copenhagen", key, "ANTI_LATERAL_FLEXION", review = BBalancedTaskReview.REVIEW_REQUIRED)
        ))
        val ledger = build(
            listOf(observation(key, 1, reps = 0, seconds = 30)),
            listOf(hidden), listOf(muscle(key, "LATERAL_CORE", "SECONDARY")), overlay
        )
        val summary = ledger.summary(StimulusExposureWindow.RECENT_7D)
        assertEquals(0, summary.observedDirectCorePhysicalSets)
        assertEquals(emptyMap<String, Int>(), summary.coreTaskObservations)
        assertEquals(mapOf("LATERAL_CORE" to 1), summary.indirectMuscleParticipationOccurrences)
        assertEquals(1, summary.reviewRequiredPhysicalSets)
        assertEquals(1, summary.physicalSetsWithUnresolvedEvidence)
    }

    @Test
    fun sourceWindowsRespectSevenTwentyEightPriorAndFiftySixDayBoundaries() {
        val key = "ex_a44ae2ca"
        val profile = profile(key, core = CanonicalCoreProfile(key, CoreClass.DIRECT, CoreDirectTarget.BRACING))
        val input = listOf(0L, 6L, 7L, 27L, 28L, 55L, 56L, -1L).mapIndexed { index, age ->
            observation(key, (index + 1).toLong(), date = cutoff.minusDays(age), reps = 0, seconds = 35)
        }
        val ledger = build(input, listOf(profile), emptyList(), BBalancedTaskCatalog.of(listOf(task("plank", key, "ANTI_EXTENSION"))))
        assertEquals(6, ledger.sets.size)
        val windows = ledger.windows()
        assertEquals(2, windows.getValue(StimulusExposureWindow.RECENT_7D).observedPhysicalSets)
        assertEquals(4, windows.getValue(StimulusExposureWindow.CURRENT_28D).observedPhysicalSets)
        assertEquals(2, windows.getValue(StimulusExposureWindow.PRIOR_28D).observedPhysicalSets)
        assertEquals(6, windows.getValue(StimulusExposureWindow.CONTEXT_56D).observedPhysicalSets)
        assertEquals(6, windows.getValue(StimulusExposureWindow.CONTEXT_56D).observedDirectCorePhysicalSets)
        assertEquals(6, windows.getValue(StimulusExposureWindow.CONTEXT_56D).distinctTrainingDays)
    }

    @Test
    fun duplicatedPhysicalSourceIdentityCannotSilentlyDoubleCount() {
        val key = "ex_a44ae2ca"
        val set = observation(key, 1)
        val result = runCatching { build(listOf(set, set), listOf(profile(key)), emptyList()) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Duplicate source"))
    }

    @Test
    fun multiplePrimaryMusclesAreTargetProjectionsNotExtraPhysicalSets() {
        val key = "compound_fixture"
        val ledger = build(
            listOf(observation(key, 1, classification = realized(RealizedStimulusKind.HYPERTROPHY_LIKE))),
            listOf(profile(key, withHypertrophy = true)),
            listOf(muscle(key, "CHEST", "PRIMARY"), muscle(key, "SHOULDERS", "PRIMARY"))
        )
        val summary = ledger.summary(StimulusExposureWindow.RECENT_7D)
        assertEquals(1, summary.realizedHypertrophyPhysicalSets)
        assertEquals(mapOf("CHEST" to 1, "SHOULDERS" to 1), summary.directHypertrophySetsByMuscle)
    }

    @Test
    fun reviewedNonRealizationDoesNotTurnIntoUnresolvedOrEffectiveSet() {
        val key = "press_fixture"
        val ledger = build(
            listOf(observation(key, 1, classification = RealizedStimulusClassification.reviewedNonRealization())),
            listOf(profile(key, withHypertrophy = true)), listOf(muscle(key, "CHEST", "PRIMARY"))
        )
        assertEquals(BStimulusEvidenceState.REVIEWED_NON_REALIZATION, ledger.sets.single().hypertrophy)
        val summary = ledger.summary(StimulusExposureWindow.RECENT_7D)
        assertEquals(0, summary.realizedHypertrophyPhysicalSets)
        assertEquals(0, summary.physicalSetsWithUnresolvedEvidence)
    }

    @Test
    fun primarySecondaryCollisionFailsClosedWithoutDoubleAnatomicalCredit() {
        val key = "conflict_fixture"
        val ledger = build(
            listOf(observation(key, 1, classification = realized(RealizedStimulusKind.HYPERTROPHY_LIKE))),
            listOf(profile(key, withHypertrophy = true)),
            listOf(muscle(key, "CHEST", "PRIMARY"), muscle(key, "CHEST", "SECONDARY"))
        )
        val row = ledger.sets.single()
        assertEquals(BStimulusEvidenceState.UNRESOLVED, row.hypertrophy)
        assertTrue("MUSCLE_RELATION_ROLE_CONFLICT" in row.reasons)
        assertTrue(row.directHypertrophyMuscles.isEmpty())
        assertTrue(row.indirectMuscleParticipation.isEmpty())
    }

    @Test
    fun unknownCanonicalIdentityAndConflictCannotReceiveDirectCredit() {
        val key = "unreviewed_fixture"
        val source = observation(key, 1, classification = realized(RealizedStimulusKind.HYPERTROPHY_LIKE),
            authority = StimulusClassificationAuthority.UNCLASSIFIED)
        val ledger = build(listOf(source), listOf(profile(key, withHypertrophy = true)),
            listOf(muscle(key, "CHEST", "PRIMARY")))
        assertEquals(BStimulusEvidenceState.UNRESOLVED, ledger.sets.single().hypertrophy)
        assertTrue(ledger.sets.single().directHypertrophyMuscles.isEmpty())
        assertTrue(ledger.sets.single().indirectMuscleParticipation.isEmpty())
    }

    @Test
    fun taskCsvRejectsNewNumericDoseUnknownVersionAndDuplicateTasks() {
        val header = "relationId,exerciseStableKey,targetDomain,targetTask,relationMeaning,evidenceSource,reviewStatus,numericDoseAuthority,recordingUnit"
        val row = "BTASK_1,key,CORE,ANTI_EXTENSION,CORE_TASK_DETAIL,CANONICAL,REVIEWED_B_OVERLAY_V2,NONE,HOLD_SECONDS"
        assertEquals(1, BBalancedTaskCatalog.fromCsv("$header\n$row\n").relations.size)
        assertTrue(runCatching { BBalancedTaskCatalog.fromCsv("$header\n${row.replace(",NONE,", ",8_SETS,")}\n") }.isFailure)
        assertTrue(runCatching { BBalancedTaskCatalog.fromCsv("$header\n${row.replace("V2", "V3")}\n") }.isFailure)
        assertTrue(runCatching { BBalancedTaskCatalog.fromCsv("$header\n$row\n${row.replace("BTASK_1", "BTASK_2")}\n") }.isFailure)
    }

    @Test
    fun realCanonicalOverlayCoversAllThirtyOneDirectCoreAndKeepsCopenhagenPending() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = CanonicalExerciseMetadataRepository(context)
        val catalog = BBalancedTaskCatalog.fromAssets(context)
        assertEquals(38, catalog.relations.size)
        val direct = repository.coreCatalog().selectableProfiles()
            .filter { it.coreClass == CoreClass.DIRECT }.map { it.exerciseStableKey }.toSet()
        assertEquals(31, direct.size)
        assertEquals(direct, catalog.relations.filter {
            it.domain == BBalancedTaskDomain.CORE && it.review == BBalancedTaskReview.REVIEWED
        }.map { it.stableKey }.toSet())
        assertEquals(BBalancedTaskReview.REVIEW_REQUIRED,
            catalog.forStableKey("ex_a8385c4a").single().review)
        assertEquals(setOf("HIP_ADDUCTION"), catalog.forStableKey("cable_hip_adduction").map { it.task }.toSet())
        assertEquals(setOf("HIP_ABDUCTION"), catalog.forStableKey("ex_728da646").map { it.task }.toSet())
    }

    @Test
    fun boundedRealConfirmedHistoryUsesReviewedCanonicalAssetsAndDoesNotGrantPrescription() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = CanonicalExerciseMetadataRepository(context)
        val exercises = repository.exercises(includeHistory = true).associateBy(Exercise::stableKey)
        val metadata = repository.runtimeMetadataCatalog().all().associateBy(RuntimeExerciseMetadata::stableKey)
        val hip = "hip_adduction_machine"
        val plank = "ex_a44ae2ca"
        val records = listOf(
            WorkoutEntryWithSets(
                WorkoutEntry(id = 1, date = cutoff.toString(), exerciseStableKey = hip, exerciseName = hip,
                    category = "STRENGTH", sessionStableKey = "hip-session"),
                listOf(WorkoutSet(id = 11, entryId = 1, setIndex = 1, reps = 10, weightKg = 50.0,
                    rpe = 8.0, confirmed = true))
            ),
            WorkoutEntryWithSets(
                WorkoutEntry(id = 2, date = cutoff.toString(), exerciseStableKey = plank, exerciseName = plank,
                    category = "CORE", sessionStableKey = "core-session"),
                listOf(WorkoutSet(id = 22, entryId = 2, setIndex = 1, reps = 0, seconds = 40,
                    weightKg = 0.0, confirmed = true))
            )
        )
        val source = StimulusExposureLedgerBuilder().build(
            cutoff = cutoff, history = records, exercises = exercises, metadata = metadata,
            physicalQualityCatalog = repository.physicalQualityCatalog(),
            movementRelations = repository.movementRelations(), coreCatalog = repository.coreCatalog(),
            badmintonCatalog = repository.badmintonObjectiveCatalog(),
            exerciseRoleCatalog = ExerciseRoleRelationCatalog.of(
                repository.trainingRoleRelations(), repository.programSlotCapabilityRelations()
            ),
            reviewedCanonicalStableKeys = repository.identities().map { it.stableKey }.toSet()
        )
        val ledger = builder.build(source, repository.muscleRelations(), BBalancedTaskCatalog.fromAssets(context))
        val summary = ledger.summary(StimulusExposureWindow.RECENT_7D)
        assertEquals(2, summary.observedPhysicalSets)
        assertEquals(mapOf("HIP_ADDUCTION" to 1), summary.movementTaskObservations)
        assertEquals(mapOf("ANTI_EXTENSION" to 1), summary.coreTaskObservations)
        assertEquals(1, summary.observedDirectCorePhysicalSets)
        assertFalse(ledger.prescriptionAuthority)
        assertFalse(ledger.selectionAuthority)
        assertFalse(ledger.schedulingAuthority)
        // Hip hypertrophy can remain UNRESOLVED if real load/effort authority is unavailable.
        assertTrue(summary.realizedHypertrophyPhysicalSets in 0..1)
        assertTrue(ledger.sets.none { it.source.date.isAfter(cutoff) })
    }

    private fun build(
        observations: List<StimulusSetObservation>,
        profiles: List<CanonicalStimulusFacetProfile>,
        muscles: List<CanonicalMetadataRelation>,
        tasks: BBalancedTaskCatalog = BBalancedTaskCatalog.EMPTY
    ): BBalancedStimulusLedgerV1 = builder.build(
        StimulusExposureLedger(profiles.associateBy { it.stableKey }, observations, emptyList(), cutoff),
        muscles, tasks
    )

    private fun profile(
        key: String,
        withHypertrophy: Boolean = false,
        core: CanonicalCoreProfile? = null
    ) = CanonicalStimulusFacetProfile(
        stableKey = key,
        physicalQualities = if (withHypertrophy) listOf(ExercisePhysicalQualityRelation(
            "H-$key", key, TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY,
            PhysicalQualityRegion.CHEST, PhysicalQualityMode.GENERAL, true,
            "TEST", setOf("TEST"), "PASS", "TEST"
        )) else emptyList(),
        coreProfile = core,
        integrity = StimulusFacetIntegrity.CONSISTENT
    )

    private fun observation(
        key: String, id: Long, date: LocalDate = cutoff,
        classification: RealizedStimulusClassification = RealizedStimulusClassification.UNCLASSIFIED,
        reps: Int = 10, seconds: Int = 0,
        authority: StimulusClassificationAuthority = StimulusClassificationAuthority.REVIEWED_CANONICAL
    ) = StimulusSetObservation(
        source = StimulusSourceRef(id, "backup-$id", id, 1, "session-$id", date, key),
        activityKind = PlannedActivityKind.RESISTANCE,
        reps = reps, weightKg = 50.0, seconds = seconds, rpe = null,
        realizedPrescriptionClass = classification.toLegacyClass(),
        facetProfileKey = key, classificationAuthority = authority,
        realizedStimulusClassification = classification
    )

    private fun realized(kind: RealizedStimulusKind) = RealizedStimulusClassification(
        kind = kind,
        status = RealizedStimulusStatus.REALIZED,
        authority = RealizedStimulusAuthority.REVIEWED,
        reasonCodes = listOf("REVIEWED_TEST_EXPOSURE")
    )

    private fun muscle(key: String, code: String, role: String) = CanonicalMetadataRelation(
        domain = CanonicalRelationDomain.MUSCLE, relationKey = "$key:$code:$role",
        exerciseStableKey = key, relationType = role, relationValue = code,
        coefficient = if (role == "PRIMARY") 1.0 else 0.5,
        sourceStableKey = key, status = "PASS", provenance = "TEST"
    )

    private fun task(
        id: String, key: String, name: String,
        review: BBalancedTaskReview = BBalancedTaskReview.REVIEWED
    ) = BBalancedTaskRelation(id, key, BBalancedTaskDomain.CORE, name, review, "REPETITIONS")
}

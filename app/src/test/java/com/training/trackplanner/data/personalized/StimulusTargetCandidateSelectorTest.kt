package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class StimulusTargetCandidateSelectorTest {
    @Test
    fun directCandidatesBeatSupportiveCandidatesByEligibility() {
        val supportive = exercise("supportive")
        val direct = exercise("direct")
        val fixture = fixture(
            exercises = listOf(supportive, direct),
            relations = listOf(
                relation("supportive", StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY),
                relation("direct", StimulusCapabilityLevel.DIRECT_CAPABILITY)
            )
        )
        val plan = qualityPlan()
        val result = select(plan, fixture, emptyList())
        assertEquals(listOf("direct"), result.materialDemand.candidates.map { it.stableKey })
        assertFalse(result.traces.single().candidatePool.contains("supportive"))
    }

    @Test
    fun targetCompatibleHistoryRanksBeforeGenericHistoryWithoutWeightedScore() {
        val generic = exercise("generic")
        val familiar = exercise("familiar")
        val fixture = fixture(
            exercises = listOf(generic, familiar),
            relations = listOf(relation("generic"), relation("familiar")),
            history = listOf(
                PlanningSetRecord(LocalDate.of(2026, 9, 1), "generic", "generic", "STRENGTH", 1, 10, 0.0, 0, 8.0),
                PlanningSetRecord(LocalDate.of(2026, 9, 2), "familiar", "familiar", "STRENGTH", 1, 5, 0.0, 0, 8.0)
            )
        )
        val result = select(qualityPlan(), fixture, emptyList())
        assertEquals("familiar", result.selectedCandidates.single().stableKey)
        assertTrue(result.traces.single().reasonCodes.contains("SELECTION_IDENTITY_PRESENT"))
    }

    @Test
    fun repeatedRecentCompatibleDirectHistoryWinsContinuityRanking() {
        val recent = exercise("recent_squat")
        val fresh = exercise("fresh_squat")
        val cutoff = LocalDate.of(2026, 9, 20)
        val fixture = fixture(
            exercises = listOf(fresh, recent),
            relations = listOf(relation("fresh_squat"), relation("recent_squat")),
            history = listOf(
                PlanningSetRecord(cutoff.minusDays(7), "recent_squat", "recent squat", "STRENGTH", 1, 5, 80.0, 0, 8.0),
                PlanningSetRecord(cutoff.minusDays(14), "recent_squat", "recent squat", "STRENGTH", 1, 5, 80.0, 0, 8.0)
            )
        )

        val result = select(qualityPlan(), fixture, emptyList())
        assertEquals("recent_squat", result.selectedCandidates.single().stableKey)
        assertEquals(listOf("recent_squat"), result.traces.single().historyDirectCapabilityIdentities)
    }

    @Test
    fun historyOlderThanSixtyDaysDoesNotReceiveCurrentContinuityPriority() {
        val old = exercise("z_old_squat")
        val fresh = exercise("a_fresh_squat")
        val cutoff = LocalDate.of(2026, 9, 20)
        val fixture = fixture(
            exercises = listOf(old, fresh),
            relations = listOf(relation("z_old_squat"), relation("a_fresh_squat")),
            history = listOf(PlanningSetRecord(cutoff.minusDays(65), "z_old_squat", "old squat", "STRENGTH", 1, 5, 80.0, 0, 8.0))
        )

        val result = select(qualityPlan(), fixture, emptyList())
        assertEquals("a_fresh_squat", result.selectedCandidates.single().stableKey)
        assertTrue(result.traces.single().historyDirectCapabilityIdentities.isEmpty())
    }

    @Test
    fun recentButPrescriptionIncompatibleHistoryIsNotClassifiedAsStrengthCompatible() {
        val incompatible = exercise("a_incompatible_squat")
        val fresh = exercise("z_fresh_squat")
        val fixture = fixture(
            exercises = listOf(incompatible, fresh),
            relations = listOf(relation("a_incompatible_squat"), relation("z_fresh_squat")),
            history = listOf(PlanningSetRecord(LocalDate.of(2026, 9, 10), "a_incompatible_squat", "incompatible squat", "STRENGTH", 1, 10, 80.0, 0, 8.0))
        )

        val result = select(qualityPlan(), fixture, emptyList())
        assertEquals("a_incompatible_squat", result.selectedCandidates.single().stableKey)
        assertEquals(SelectionProbePrescriptionCompatibility.REALIZED_INCOMPATIBLE,
            result.selectedCandidates.single().probePrescriptionCompatibility)
    }

    @Test
    fun tissueRestrictionOverridesRecentContinuityAndNoHistorySelectionIsDeterministic() {
        val restricted = exercise("a_restricted_squat")
        val safe = exercise("z_safe_squat")
        val cutoff = LocalDate.of(2026, 9, 20)
        val base = fixture(
            exercises = listOf(restricted, safe),
            relations = listOf(relation("a_restricted_squat"), relation("z_safe_squat")),
            history = listOf(PlanningSetRecord(cutoff.minusDays(3), "a_restricted_squat", "restricted squat", "STRENGTH", 1, 5, 80.0, 0, 8.0))
        )
        val tissueRestricted = base.copy(snapshot = base.snapshot.copy(
            recoverySignals = PlanningRecoverySignals(tissueRestrictedStableKeys = setOf("a_restricted_squat"))
        ))
        val gated = select(qualityPlan(), tissueRestricted, emptyList())
        assertEquals("z_safe_squat", gated.selectedCandidates.single().stableKey)
        assertFalse(gated.traces.single().candidatePool.contains("a_restricted_squat"))

        val noHistory = fixture(listOf(safe, exercise("b_squat")), listOf(relation("z_safe_squat"), relation("b_squat")))
        assertEquals(select(qualityPlan(), noHistory, emptyList()), select(qualityPlan(), noHistory, emptyList()))
    }

    @Test
    fun controlIdentityCannotSuppressCanonicalSelectionForDoseGap() {
        val existing = exercise("existing")
        val replacement = exercise("replacement")
        val fixture = fixture(
            exercises = listOf(existing, replacement),
            relations = listOf(relation("existing"), relation("replacement"))
        )
        val result = select(qualityPlan(), fixture, listOf(existing))
        val trace = result.traces.single()
        assertTrue(trace.selectionRequired)
        assertTrue(trace.historyDirectCapabilityIdentities.isEmpty())
        assertEquals("existing", result.selectedCandidates.single().stableKey)
        assertTrue(trace.reasonCodes.contains("SELECTION_IDENTITY_PRESENT"))
    }

    @Test
    fun canonicalSelectorHasNoLegacySeedAndAlwaysBuildsFromTargetAndHistory() {
        val existing = exercise("existing")
        val fixture = fixture(listOf(existing), listOf(relation("existing")))
        val control = skeleton(fixture.request, listOf(
            item("existing", 1, role = "MAIN"),
            item("existing", 2, role = "MAIN", week = 2),
            item("existing", 3, role = "ACCESSORY")
        ))
        assertEquals(setOf("existing"), control.items.mapTo(linkedSetOf(), ProgramSkeletonItem::exerciseStableKey))
        val actual = StimulusTargetCandidateSelector().build(
            qualityPlan(), fixture.snapshot, fixture.state, fixture.request, fixture.catalog
        )
        assertEquals("existing", actual.selectedCandidates.single().stableKey)
        assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", actual.selectedCandidates.single().selectionRole)
        assertTrue(actual.traces.single().selectionRequired)
    }

    @Test
    fun selectorRunsAfterControlObjectIsDiscardedAndHasNoProgramObjectParameter() {
        val candidate = exercise("candidate")
        val fixture = fixture(listOf(candidate), listOf(relation("candidate")))
        var control: GeneratedProgramSkeleton? = skeleton(fixture.request, listOf(item("old", 1, role = "MAIN")))
        assertEquals(setOf("old"), requireNotNull(control).items.mapTo(linkedSetOf(), ProgramSkeletonItem::exerciseStableKey))
        control = null

        assertNull(control)
        val build = StimulusTargetCandidateSelector::class.java.methods.single { it.name == "build" }
        assertFalse(build.parameterTypes.any { it == GeneratedProgramSkeleton::class.java || it == ProgramSkeletonItem::class.java })
        val result = StimulusTargetCandidateSelector().build(
            qualityPlan(), fixture.snapshot, fixture.state, fixture.request, fixture.catalog
        )
        assertEquals(listOf("candidate"), result.selectedCandidates.map { it.stableKey })
        assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", result.selectedCandidates.single().selectionRole)
    }

    @Test
    fun malformedSeedRejectsBlankStableKeyAndDuplicateOwnerButAllowsBlankLegacyRole() {
        assertThrows(IllegalArgumentException::class.java) {
            StimulusIncumbentIdentity(" ", "MAIN")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StimulusIncumbentIdentitySeed(listOf(
                StimulusIncumbentIdentity("lift", "MAIN"),
                StimulusIncumbentIdentity("lift", "MAIN")
            ))
        }
        // ProgramSkeletonItem's existing role default is the empty string; role is preserved verbatim.
        assertEquals(listOf(StimulusIncumbentIdentity("legacy", "")),
            StimulusIncumbentIdentitySeed.fromControl(skeleton(request(), listOf(item("legacy", 1)))).owners)
    }

    @Test
    fun sameStableKeyWithDifferentSelectionRolesIsNeverCollapsed() {
        val seed = StimulusIncumbentIdentitySeed(listOf(
            StimulusIncumbentIdentity("shared", "MAIN"),
            StimulusIncumbentIdentity("shared", "ACCESSORY")
        ))
        assertEquals(2, seed.owners.size)
        assertEquals(setOf("shared"), seed.stableKeys)
    }

    @Test
    fun incumbentIdentityHasNoMetadataFieldsThatCouldConflictForOneOwner() {
        assertEquals(
            listOf("selectionRole", "stableKey"),
            StimulusIncumbentIdentity::class.java.declaredFields.map { it.name }
                .filterNot { it.startsWith("\$") }.sorted()
        )
    }

    @Test
    fun multiTargetDirectCandidateIsSelectedOnceAndReused() {
        val shared = exercise("shared")
        val fixture = fixture(
            exercises = listOf(shared),
            relations = listOf(
                relation("shared", StimulusCapabilityLevel.DIRECT_CAPABILITY, TrainableQuality.POWER),
                relation("shared", StimulusCapabilityLevel.DIRECT_CAPABILITY, TrainableQuality.RAPID_FORCE_PRODUCTION)
            )
        )
        val plan = StimulusTargetPlan(
            qualityTargets = listOf(
                target(TrainableQuality.POWER, TargetPriority.PRIMARY),
                target(TrainableQuality.RAPID_FORCE_PRODUCTION, TargetPriority.SECONDARY)
            ), taskTargets = emptyList(), unresolved = emptyList()
        )
        val result = select(plan, fixture, emptyList())
        assertEquals(listOf("shared"), result.selectedCandidates.map { it.stableKey })
        assertEquals(setOf("QUALITY:POWER", "QUALITY:RAPID_FORCE_PRODUCTION"), result.selectedCandidates.single().coveredTargetIds)
        assertTrue(result.traces[1].reasonCodes.contains("TARGET_COVERED_BY_ALREADY_SELECTED_IDENTITY"))
    }

    @Test
    fun noMinimumTargetCannotBorrowAnIdentitySelectedForAnotherQuality() {
        val shared = exercise("shared")
        val fixture = fixture(
            exercises = listOf(shared),
            relations = listOf(
                relation("shared", quality = TrainableQuality.HYPERTROPHY),
                relation("shared", quality = TrainableQuality.STRENGTH)
            )
        )
        val plan = StimulusTargetPlan(
            qualityTargets = listOf(
                target(TrainableQuality.HYPERTROPHY, TargetPriority.PRIMARY),
                target(TrainableQuality.STRENGTH, TargetPriority.SECONDARY,
                    StimulusDoseStrategy.NO_MINIMUM_TARGET, StimulusTargetNumericAuthority.NONE)
            ), taskTargets = emptyList(), unresolved = emptyList()
        )

        val result = select(plan, fixture, emptyList())

        assertEquals(setOf("QUALITY:HYPERTROPHY"), result.selectedCandidates.single().coveredTargetIds)
        assertFalse(result.traces.last().selectionRequired)
        assertNull(result.traces.last().selectedStableKey)
        assertTrue(result.traces.last().reasonCodes.contains("NO_MINIMUM_TARGET"))
    }

    @Test
    fun selectedIdentityRedundancyAvoidsUnnecessaryDuplicateGroup() {
        val first = exercise("first_power")
        val redundant = exercise("a_redundant_rfd")
        val independent = exercise("z_independent_rfd")
        val fixture = fixture(
            exercises = listOf(first, redundant, independent),
            relations = listOf(
                relation("first_power", quality = TrainableQuality.POWER),
                relation("a_redundant_rfd", quality = TrainableQuality.RAPID_FORCE_PRODUCTION),
                relation("z_independent_rfd", quality = TrainableQuality.RAPID_FORCE_PRODUCTION)
            )
        )
        val metadata = fixture.snapshot.metadata.toMutableMap()
        metadata["first_power"] = metadata.getValue("first_power").copy(redundancyGroup = "BARBELL_LOWER")
        metadata["a_redundant_rfd"] = metadata.getValue("a_redundant_rfd").copy(redundancyGroup = "BARBELL_LOWER")
        metadata["z_independent_rfd"] = metadata.getValue("z_independent_rfd").copy(redundancyGroup = "JUMP")
        val withRedundancy = fixture.copy(snapshot = fixture.snapshot.copy(metadata = metadata))
        val plan = StimulusTargetPlan(
            qualityTargets = listOf(
                target(TrainableQuality.POWER, TargetPriority.PRIMARY),
                target(TrainableQuality.RAPID_FORCE_PRODUCTION, TargetPriority.SECONDARY)
            ), taskTargets = emptyList(), unresolved = emptyList()
        )

        val selected = select(plan, withRedundancy, emptyList())
        assertEquals(listOf("first_power", "z_independent_rfd"), selected.selectedCandidates.map { it.stableKey })
    }

    @Test
    fun noMinimumReductionAndRedistributionNeverCreateNewIdentity() {
        val candidate = exercise("candidate")
        val fixture = fixture(listOf(candidate), listOf(relation("candidate")))
        val targets = listOf(
            target(TrainableQuality.STRENGTH, TargetPriority.PRIMARY, StimulusDoseStrategy.NO_MINIMUM_TARGET, StimulusTargetNumericAuthority.NONE),
            target(TrainableQuality.HYPERTROPHY, TargetPriority.SECONDARY, StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE),
            target(TrainableQuality.POWER, TargetPriority.BACKGROUND, StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY)
        )
        val result = select(StimulusTargetPlan(targets, emptyList(), emptyList()), fixture, emptyList())
        assertTrue(result.selectedCandidates.isEmpty())
        assertTrue(result.traces.any { it.reasonCodes.contains("NO_MINIMUM_TARGET") })
        assertTrue(result.traces.any { it.reasonCodes.contains("REDUCTION_DOES_NOT_AUTHORIZE_NEW_EXERCISE") })
        assertTrue(result.traces.any { it.reasonCodes.contains("DISTRIBUTION_AUTHORITY_DEFERRED") })
    }

    @Test
    fun genericCourtCannotSatisfyTaskIdentity() {
        val court = exercise("court", activityKind = "GENERIC_COURT_SESSION")
        val fixture = fixture(
            exercises = listOf(court),
            relations = emptyList(),
            directTasks = mapOf("court" to setOf("DECELERATION"))
        )
        val plan = StimulusTargetPlan(
            qualityTargets = emptyList(),
            taskTargets = listOf(StimulusTaskTarget("DECELERATION", StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
                TargetPriority.PRIMARY, StimulusTargetNumericAuthority.DIRECTION_ONLY, emptyList(), emptyList())),
            unresolved = emptyList()
        )
        val result = select(plan, fixture, emptyList())
        assertTrue(result.selectedCandidates.isEmpty())
        assertTrue(result.traces.single().candidatePool.isEmpty())
        assertTrue(result.traces.single().reasonCodes.contains("TARGET_REQUIRES_SELECTION_BUT_NO_MATERIALIZABLE_CANDIDATE"))
    }

    @Test
    fun b5UsesExistingPrescriptionSetCountAndLeavesStrengthGapForB6() {
        val candidate = exercise("novel")
        val fixture = fixture(listOf(candidate), listOf(relation("novel")))
        val result = select(qualityPlan().copy(
            qualityTargets = listOf(target(TrainableQuality.STRENGTH, TargetPriority.PRIMARY,
                StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, StimulusTargetNumericAuthority.DIRECTION_ONLY,
                weekly = StimulusTargetRange(4.0, 9.0, 12.0)))
        ), fixture, emptyList())
        val selected = result.selectedCandidates.single()
        assertEquals(2, selected.targetSetsFromExistingPrescription)
        assertEquals(2, result.materialDemand.candidates.single().targetSets)
        assertEquals("PRESCRIPTION_COMPATIBILITY_GAP_DEFERRED_TO_B6", selected.currentPrescriptionCompatibility)
        assertTrue(result.traces.single().reasonCodes.contains("B5_TARGET_SETS_FROM_EXISTING_PRESCRIPTION_NOT_TARGET_AUTHORITY"))
    }

    @Test
    fun comparisonReportsAddedAndRemovedIdentitiesWithoutWinner() {
        val request = request()
        val control = skeleton(request, listOf(item("old", 1)))
        val experimental = skeleton(request, listOf(item("new", 1)))
        val plan = qualityPlan()
        val selection = StimulusCandidateSelectionPlan(emptyList(), emptyList(), MaterialDemand(emptyList(), emptyMap(), emptyMap()))
        val comparison = StimulusSelectionProgramComparisonEngine().compare(control, experimental, plan, selection, null, null)
        assertEquals(setOf("new"), comparison.addedStableKeys)
        assertEquals(setOf("old"), comparison.removedStableKeys)
        assertTrue(comparison.winner == null)
        assertTrue(comparison.differences.isNotEmpty())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun select(plan: StimulusTargetPlan, fixture: Fixture, controlItems: List<Exercise>): StimulusCandidateSelectionPlan =
        StimulusTargetCandidateSelector().build(plan, fixture.snapshot, fixture.state, fixture.request, fixture.catalog)

    private fun qualityPlan(): StimulusTargetPlan = StimulusTargetPlan(
        qualityTargets = listOf(target(TrainableQuality.STRENGTH, TargetPriority.PRIMARY)),
        taskTargets = emptyList(), unresolved = emptyList()
    )

    private fun target(
        quality: TrainableQuality,
        priority: TargetPriority,
        strategy: StimulusDoseStrategy = StimulusDoseStrategy.HOLD_PERSONAL_BASELINE,
        authority: StimulusTargetNumericAuthority = StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
        weekly: StimulusTargetRange = StimulusTargetRange(4.0, 6.0, 9.0)
    ) = StimulusQualityTarget(quality, strategy, priority, authority, SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS,
        PlanningConfidence.HIGH, weekly, StimulusTargetRange(1.0, 2.0, 3.0), null, null, null, emptyList(), emptyList(), true, true)

    private fun relation(key: String, level: StimulusCapabilityLevel = StimulusCapabilityLevel.DIRECT_CAPABILITY,
        quality: TrainableQuality = TrainableQuality.STRENGTH) = ExercisePhysicalQualityRelation(
        relationId = "$key-${quality.name}", exerciseStableKey = key, qualityId = quality, relationLevel = level,
        regionQualifier = PhysicalQualityRegion.SYSTEMIC, modeQualifier = PhysicalQualityMode.GENERAL,
        prescriptionDependent = true, provenance = "TEST", evidenceRelationKeys = emptySet(), reviewStatus = "APPROVED", notes = ""
    )

    private data class Fixture(val snapshot: PlanningHistorySnapshot, val state: AthletePlanningState,
        val catalog: CanonicalExercisePhysicalQualityCatalog, val request: ProgramSkeletonRequest)

    private fun fixture(exercises: List<Exercise>, relations: List<ExercisePhysicalQualityRelation>,
        history: List<PlanningSetRecord> = emptyList(), directTasks: Map<String, Set<String>> = emptyMap()): Fixture {
        val effectiveHistory = history.ifEmpty {
            listOf(PlanningSetRecord(LocalDate.of(2026, 6, 1), "history-sentinel", "history-sentinel", "TEST", 1, 1, 0.0, 0, 8.0))
        }
        val metadata = exercises.associate { exercise ->
            exercise.stableKey to RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
                planningEligibility = "PROGRAM_SELECTABLE", sourceConfidenceLevel = "HIGH"
            )
        }
        val snapshot = PlanningHistorySnapshot(
            cutoff = LocalDate.of(2026, 9, 20), allConfirmedSets = effectiveHistory, exercises = exercises.associateBy(Exercise::stableKey),
            metadata = metadata, badmintonObjectives = emptyMap(), profilePrimaryGoal = "STRENGTH_GAIN",
            strengthTrainingYears = 1.0, badmintonTrainingYears = 0.0,
            preferences = PersonalizedPlanningPreferences(StrengthIntent.STRENGTH_PRIORITY, BadmintonPlanningIntent.DISABLED, FreeWeightWillingness.WILLING),
            badmintonDirectObjectives = directTasks
        )
        val state = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers())
        return Fixture(snapshot, state, CanonicalExercisePhysicalQualityCatalog.of(relations), request())
    }

    private fun exercise(key: String, activityKind: String = "RESISTANCE") = Exercise(
        stableKey = key, name = key, category = "STRENGTH", activityKind = activityKind, equipment = "BODYWEIGHT"
    )

    private fun request() = ProgramSkeletonRequest("test", ProgramGoal.STRENGTH, 3, 60, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 2)

    private fun item(key: String, order: Int, role: String = "", week: Int = 1) = ProgramSkeletonItem(
        localId = "$key-$order-$week-$role", weekNumber = week, dayOfWeek = 1, orderIndex = order, exerciseStableKey = key,
        exerciseName = key, category = "STRENGTH", restSeconds = 90, prescription = "8 reps", setCount = 2,
        reps = 8, weightKg = 0.0, seconds = 0, selectionReason = "test", weightSource = "TEST", selectionRole = role
    )

    private fun skeleton(request: ProgramSkeletonRequest, items: List<ProgramSkeletonItem>) = GeneratedProgramSkeleton(
        suggestedName = request.name, durationDays = request.durationWeeks * 7, request = request,
        periodizationType = request.periodizationType, weekPlans = listOf(ProgramWeekPlan(1, "TEST", 1.0, 1.0, 2, 8.0, 2, 0, false)),
        items = items
    )
}

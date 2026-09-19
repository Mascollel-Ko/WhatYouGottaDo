package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RegionalAuthorityTest {
    @Test
    fun requirementUsesBasePriorityAndMapsUpperPullWithoutMergingHistory() {
        val resolver = RegionalStrengthRequirementResolver()
        val result = resolver.resolve(NeedRelevance.HIGH, listOf(
            representation("LOWER_KNEE", RepresentationPriority.HIGH),
            representation("POSTERIOR_CHAIN", RepresentationPriority.MODERATE),
            representation("UPPER_PULL", RepresentationPriority.HIGH)
        ))
        assertEquals(NeedRelevance.HIGH, result[MovementCoverage.LOWER_KNEE])
        assertEquals(NeedRelevance.MODERATE, result[MovementCoverage.POSTERIOR_CHAIN])
        assertEquals(NeedRelevance.HIGH, result[MovementCoverage.HORIZONTAL_PULL])
        assertEquals(NeedRelevance.HIGH, result[MovementCoverage.VERTICAL_PULL])
        assertTrue(MovementCoverage.VERTICAL_PUSH !in result)
    }

    @Test
    fun mixedSetPrescriptionsAreClassifiedPerSet() {
        val key = "mixed-squat"
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH", planningEligibility = "PROGRAM_SELECTABLE"
        )
        val snapshot = PlanningHistorySnapshot(
            LocalDate.of(2026, 9, 19), emptyList(), emptyMap(), mapOf(key to metadata), emptyMap(),
            "STRENGTH_GAIN", 1.0, 0.0, PersonalizedPlanningPreferences()
        )
        val relation = { id: String, quality: TrainableQuality -> ExercisePhysicalQualityRelation(
            id, key, quality, StimulusCapabilityLevel.DIRECT_CAPABILITY, PhysicalQualityRegion.LOWER,
            PhysicalQualityMode.SQUAT, true, "TEST", setOf("TEST"), "PASS", ""
        ) }
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(
            relation("strength", TrainableQuality.STRENGTH), relation("hypertrophy", TrainableQuality.HYPERTROPHY)
        ))
        val item = ProgramSkeletonItem(
            localId = "mixed", weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key,
            exerciseName = key, category = "TEST", restSeconds = 90, prescription = "mixed", setCount = 4,
            reps = 4, weightKg = 100.0, seconds = 0, selectionReason = "TEST", weightSource = "TEST",
            setPrescriptions = listOf(
                ProgramSetPrescription(1, 4, 100.0, 0), ProgramSetPrescription(2, 8, 80.0, 0),
                ProgramSetPrescription(3, 8, 80.0, 0), ProgramSetPrescription(4, 8, 80.0, 0)
            )
        )
        val skeleton = GeneratedProgramSkeleton(
            "mixed", 7, ProgramSkeletonRequest(
                "mixed", com.training.trackplanner.data.ProgramGoal.STRENGTH, 1, 60,
                emptySet(), "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 1
            ),
            ProgramPeriodizationType.AUTO, emptyList(), listOf(item)
        )
        val labels = ProgramEmphasisProjector().project(skeleton, snapshot, catalog)
        assertEquals(1, labels.first { it.quality == TrainableQuality.STRENGTH }.plannedUnits)
        assertEquals(3, labels.first { it.quality == TrainableQuality.HYPERTROPHY }.plannedUnits)
    }

    @Test
    fun hypertrophyTargetCannotReuseStrengthLikeHistory() {
        val key = "lower-knee-history"
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH", planningEligibility = "PROGRAM_SELECTABLE"
        )
        val snapshot = PlanningHistorySnapshot(
            LocalDate.of(2026, 9, 19),
            listOf(PlanningSetRecord(LocalDate.of(2026, 9, 18), key, key, "RESISTANCE", 1, 5, 100.0, 0, 8.0)),
            mapOf(key to Exercise(key, key, "RESISTANCE", activityKind = "RESISTANCE")),
            mapOf(key to metadata), emptyMap(), "HYPERTROPHY", 1.0, 0.0, PersonalizedPlanningPreferences()
        )
        val target = RegionalStimulusTarget(
            MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY, RegionalTargetAction.ADD_SUPPORT,
            RegionalNumericAuthority.PRIOR_TOLERATED_HYPERTROPHY, weeklyDoseTarget = 3.0
        )
        val resolved = RegionalTargetPrescriptionResolver().resolve(
            target, PlannedExercise(key, "REGIONAL", "test", 1, targetSets = 3), snapshot
        ).prescription
        assertTrue(resolved != null)
        assertTrue(resolved!!.sets.all { provisionalRealizedStimulusClass(it.reps) == RealizedStimulusClass.HYPERTROPHY_LIKE })
        assertTrue(resolved.sets.none { it.reps == 5 })
    }

    @Test
    fun finalProjectorReportsOnlyTargetCompatibleSets() {
        val key = "mixed-squat-final"
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH", planningEligibility = "PROGRAM_SELECTABLE"
        )
        val snapshot = PlanningHistorySnapshot(
            LocalDate.of(2026, 9, 19), emptyList(), emptyMap(), mapOf(key to metadata), emptyMap(),
            "STRENGTH_GAIN", 1.0, 0.0, PersonalizedPlanningPreferences()
        )
        val relation = ExercisePhysicalQualityRelation(
            "hypertrophy", key, TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY,
            PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, true, "TEST", setOf("TEST"), "PASS", ""
        )
        val item = ProgramSkeletonItem(
            localId = "mixed-final", weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key,
            exerciseName = key, category = "TEST", restSeconds = 90, prescription = "mixed", setCount = 4,
            reps = 4, weightKg = 100.0, seconds = 0, selectionReason = "TEST", weightSource = "TEST",
            setPrescriptions = listOf(
                ProgramSetPrescription(1, 4, 100.0, 0), ProgramSetPrescription(2, 8, 80.0, 0),
                ProgramSetPrescription(3, 8, 80.0, 0), ProgramSetPrescription(4, 8, 80.0, 0)
            )
        )
        val request = ProgramSkeletonRequest(
            "mixed-final", com.training.trackplanner.data.ProgramGoal.STRENGTH, 1, 60,
            emptySet(), "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 1
        )
        val plan = GeneratedProgramSkeleton("mixed-final", 7, request, ProgramPeriodizationType.AUTO, emptyList(), listOf(item))
        val projection = FinalRegionalStimulusProjector().project(
            target = RegionalStimulusTarget(
                MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY, RegionalTargetAction.ADD_SUPPORT,
                RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND, weeklyDoseTarget = 4.0
            ), finalPlan = plan, snapshot = snapshot,
            catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(relation)), selectedStableKey = key,
            creditedUnits = 0, residualUnits = 4, authorizedUnits = 4
        )
        assertEquals(3, projection.targetCompatibleMaterializedUnits)
        assertEquals(1, projection.shortfall)

        val survivingPlan = plan.copy(items = listOf(item.copy(
            setCount = 2, reps = 8, weightKg = 80.0,
            setPrescriptions = item.setPrescriptions.drop(1).take(2)
        )))
        val shortfall = FinalRegionalStimulusProjector().project(
            target = RegionalStimulusTarget(
                MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY, RegionalTargetAction.ADD_SUPPORT,
                RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND, weeklyDoseTarget = 4.0
            ), finalPlan = survivingPlan, snapshot = snapshot,
            catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(relation)), selectedStableKey = key,
            creditedUnits = 0, residualUnits = 4, authorizedUnits = 4
        )
        assertEquals(2, shortfall.targetCompatibleMaterializedUnits)
        assertEquals(2, shortfall.shortfall)
    }

    @Test
    fun authorizedRegionalPrescriptionIsVisibleToSchedulingBeforeFinalOutput() {
        val snapshot = PostGenerationFixture.snapshot()
        val state = PostGenerationFixture.state(snapshot)
        val item = PostGenerationFixture.source("press", 3).copy(role = "REGIONAL_TARGET_LOWER_KNEE_HYPERTROPHY")
        val authorized = PlannedPrescription(
            "target-compatible 10-rep prescription",
            List(3) { ProgramSetPrescription(it + 1, 10, 0.0, 0) },
            90,
            "TARGET_COMPATIBLE_TEST"
        )
        val demand = AuthorizedSchedulingDemand("regional_press", item, authorized, continuity = false)
        val allocation = SplitAwareContinuityAllocation(PersonalizedPrescriptionPlanner()).allocateAuthorized(
            snapshot, state, listOf(demand), 3, 60,
            PostGenerationFixture.plan(listOf(PostGenerationFixture.row("press", 1))).request
        )
        assertEquals(authorized, allocation.trace.authorized.single().prescription)
        assertTrue(allocation.days.values.flatten().all { atom -> atom.timed.prescription.sets.all { it.reps == 10 } })
    }

    @Test
    fun regionalFinalizerIsAuditOnlyAndCannotMutatePostReflowPrescription() {
        val key = "mixed-final"
        val item = ProgramSkeletonItem(
            localId = "post-reflow", weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key,
            exerciseName = key, category = "TEST", restSeconds = 90, prescription = "ordinary five-rep",
            setCount = 3, reps = 5, weightKg = 100.0, seconds = 0, selectionReason = "TEST", weightSource = "HISTORY",
            selectionRole = "REGIONAL_ROLE",
            setPrescriptions = List(3) { ProgramSetPrescription(it + 1, 5, 100.0, 0) }
        )
        val plan = GeneratedProgramSkeleton(
            "post-reflow", 7,
            ProgramSkeletonRequest("post-reflow", com.training.trackplanner.data.ProgramGoal.STRENGTH, 1, 60,
                emptySet(), "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 1),
            ProgramPeriodizationType.AUTO, emptyList(), listOf(item)
        )
        val result = RegionalTargetAwareFinalizer().apply(
            plan, PostGenerationFixture.snapshot(),
            mapOf(key to RegionalStimulusTarget(MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY,
                RegionalTargetAction.ADD_SUPPORT, RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND, 3.0)),
            mapOf("$key|REGIONAL_ROLE" to RegionalStimulusTarget(MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY,
                RegionalTargetAction.ADD_SUPPORT, RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND, 3.0))
        )
        assertSame(plan, result)
        assertTrue(result.items.single().setPrescriptions.all { it.reps == 5 })
    }

    @Test
    fun sameRegionUnrelatedQualitySurvivesTypedOwnershipForHoldOrNoChange() {
        val base = PostGenerationFixture.snapshot()
        val strengthKey = "lower-strength"
        val hyperKey = "lower-hyper"
        val strengthExercise = Exercise(strengthKey, strengthKey, "RESISTANCE", equipment = "BODYWEIGHT")
        val hyperExercise = Exercise(hyperKey, hyperKey, "RESISTANCE", equipment = "BODYWEIGHT")
        val lowerMeta = base.metadata.getValue("squat")
        val snapshot = base.copy(
            allConfirmedSets = base.allConfirmedSets + PlanningSetRecord(base.cutoff.minusDays(2), strengthKey, strengthKey, "RESISTANCE", 1, 5, 100.0, 0, 8.0),
            exercises = base.exercises + (strengthKey to strengthExercise) + (hyperKey to hyperExercise),
            metadata = base.metadata + (strengthKey to lowerMeta.copy(stableKey = strengthKey, exerciseName = strengthKey)) +
                (hyperKey to lowerMeta.copy(stableKey = hyperKey, exerciseName = hyperKey))
        )
        val candidates = MaterialDemand(listOf(
            PlannedExercise(strengthKey, "OTHER_OBJECTIVE", "unrelated strength", 80, targetSets = 2),
            PlannedExercise(hyperKey, "REGIONAL_DUPLICATE", "owned hyper", 80, targetSets = 2)
        ), emptyMap(), emptyMap())
        val relations = listOf(
            ExercisePhysicalQualityRelation("strength", strengthKey, TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY,
                PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, true, "TEST", setOf("TEST"), "PASS", ""),
            ExercisePhysicalQualityRelation("hyper", hyperKey, TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY,
                PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, true, "TEST", setOf("TEST"), "PASS", "")
        )
        val filtered = RegionalMaterialDemandOwnershipFilter.filter(
            candidates, snapshot, PostGenerationFixture.state(snapshot),
            setOf(RegionalOwnershipKey(MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY)),
            catalog = CanonicalExercisePhysicalQualityCatalog.of(relations)
        )
        assertTrue(filtered.candidates.any { it.stableKey == strengthKey })
        assertFalse(filtered.candidates.any { it.stableKey == hyperKey })
    }

    private fun representation(key: String, priority: RepresentationPriority) = MovementExposureRepresentation(
        movementCoverage = key, basePriority = priority, currentExposure28d = 1.0, priorExposure28d = 1.0,
        currentActiveBins = 4, currentShare = .5, priorShare = .5, peerReference = null,
        peerRepresentationRatio = null, personalRetentionRatio = 1.0, representationState = RepresentationState.NO_CLEAR_DEFICIT_SIGNAL,
        evidenceConfidence = PlanningConfidence.HIGH, reasonCodes = emptyList()
    )
}

package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
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

    private fun representation(key: String, priority: RepresentationPriority) = MovementExposureRepresentation(
        movementCoverage = key, basePriority = priority, currentExposure28d = 1.0, priorExposure28d = 1.0,
        currentActiveBins = 4, currentShare = .5, priorShare = .5, peerReference = null,
        peerRepresentationRatio = null, personalRetentionRatio = 1.0, representationState = RepresentationState.NO_CLEAR_DEFICIT_SIGNAL,
        evidenceConfidence = PlanningConfidence.HIGH, reasonCodes = emptyList()
    )
}

package com.training.trackplanner.data

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.features.ExerciseAnalysisMapper
import com.training.trackplanner.analysis.features.MetadataReadinessReporter
import com.training.trackplanner.analysis.features.ReadinessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MetadataSanityCheckerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun seedExercisesHaveValidFatigueMetadata() {
        val exercises = legacyAnalysisExercises()
        val report = MetadataSanityChecker.checkAll(exercises)

        assertTrue(exercises.size >= 200)
        assertEquals(0, report.errorCount)
        assertEquals(emptyList<String>(), report.needsReviewExerciseNames)
        assertTrue(exercises.all { exercise -> exercise.requiredFieldValues().all { value -> value.isNotBlank() } })
        assertTrue(exercises.all { exercise ->
            exercise.weightValues().all { weight -> weight in 0.0..1.0 }
        })
        exercises.forEach { exercise ->
            if (exercise.movementCategory.hasToken("REACTIVE")) {
                assertTrue(exercise.neuralSpeedWeight > 0.0)
            }
            if (exercise.fatigueCategories.hasToken("DECELERATION")) {
                assertTrue(exercise.decelerationWeight > 0.0)
            }
            if (exercise.fatigueCategories.hasToken("ELASTIC_SSC")) {
                assertTrue(exercise.elasticSscWeight > 0.0)
            }
            if (exercise.fatigueCategories.hasToken("ROTATION_POWER")) {
                assertTrue(exercise.rotationPowerWeight > 0.0)
            }
            if (exercise.fatigueCategories.hasToken("ANTI_ROTATION")) {
                assertTrue(exercise.antiRotationWeight > 0.0)
            }
            if (exercise.badmintonTransferRoles.hasAnyCourtRole()) {
                assertTrue(exercise.adaptiveBaselineGroups.hasToken("BADMINTON_COURT"))
            }
            if (exercise.badmintonTransferStrength == "DIRECT") {
                assertTrue(exercise.badmintonTransferRoles.hasRealToken())
            }
            if (exercise.badmintonTransferStrength in setOf("DIRECT", "SUPPORTIVE")) {
                assertTrue(exercise.analysisEligibility.hasToken("BADMINTON_TRANSFER"))
            }
            if (exercise.courtMovementTypes.hasToken("REACTION_RANDOM")) {
                assertTrue(exercise.neuralSpeedWeight > 0.0)
            }
            if (exercise.courtMovementTypes.hasToken("DECELERATION")) {
                assertTrue(exercise.decelerationWeight > 0.0)
            }
            if (exercise.courtMovementTypes.hasToken("JUMP_LANDING")) {
                assertTrue(exercise.elasticSscWeight > 0.0 || exercise.decelerationWeight > 0.0)
            }
            if (exercise.progressMetricType == "ESTIMATED_1RM") {
                assertTrue(exercise.estimated1RmEligible)
            }
            if (exercise.progressMetricType == "VOLUME_LOAD") {
                assertTrue(exercise.volumeLoadEligible)
            }
            if (exercise.progressMetricType == "NOT_PROGRESS_TARGET") {
                assertFalse(exercise.analysisEligibility.hasToken("STRENGTH_PROGRESS"))
            }
            assertTrue(exercise.analysisEligibility.hasRealToken())
            if (exercise.movementCategory == MovementCategory.PREHAB.name ||
                exercise.movementPattern == MovementPattern.PREHAB.name
            ) {
                assertTrue(exercise.systemicLoadWeight < 0.5)
                assertFalse(exercise.analysisEligibility.hasToken("STRENGTH_PROGRESS"))
            }
            if (exercise.movementCategory == MovementCategory.TEST.name) {
                assertTrue(exercise.analysisEligibility.hasToken("TEST_ONLY"))
                assertFalse(exercise.analysisEligibility.hasToken("STRENGTH_PROGRESS"))
            }
            if (exercise.compoundType == "ISOLATION") {
                assertTrue(exercise.systemicLoadWeight < 0.75)
            }
        }
    }

    @Test
    fun sanityCheckerReportsNeedsReviewRows() {
        val exercise = Exercise(
            name = "Review fixture",
            category = "TEST",
            stableKey = "review_fixture",
            movementPattern = MovementPattern.ISOLATION.name,
            movementCategory = MovementCategory.HYPERTROPHY.name,
            equipment = "DUMBBELL",
            compoundType = CompoundType.ISOLATION.name,
            forceType = FatigueForceType.BRACE.name,
            plane = Plane.SAGITTAL.name,
            laterality = FatigueLaterality.BILATERAL.name,
            axialLoadLevel = AxialLoadLevel.NONE.name,
            fatigueCategories = FatigueCategory.SYSTEMIC.name,
            adaptiveBaselineGroups = AdaptiveBaselineGroup.SYSTEMIC.name,
            recoveryDecayProfile = RecoveryDecayProfile.SHORT.name,
            systemicLoadWeight = 0.75,
            neuralHeavyWeight = 0.0,
            neuralSpeedWeight = 0.0,
            localLoadWeight = 0.75,
            metadataConfidence = MetadataConfidence.HIGH.name
        )

        val result = MetadataSanityChecker.check(exercise)

        assertTrue(result.needsReview)
        assertFalse(result.hasErrors)
    }

    @Test
    fun readinessReporterMarksSeedCatalogReady() {
        val report = MetadataReadinessReporter.generate(legacyAnalysisExercises())

        assertEquals(224, report.summary.totalExerciseCount)
        assertEquals(224, report.summary.fatigueReadyCounts[ReadinessStatus.YES])
        assertEquals(224, report.summary.progressReadyCounts[ReadinessStatus.YES])
        assertEquals(224, report.summary.badmintonReadyCounts[ReadinessStatus.YES])
        assertEquals(224, report.summary.balanceReadyCounts[ReadinessStatus.YES])
        assertEquals(emptyList<String>(), report.summary.needsReviewExerciseNames)
        assertTrue(report.mappingLayerExists)
    }

    @Test
    fun exerciseAnalysisMapperCreatesFeatureVectorWithoutNameParsing() {
        val exercise = legacyAnalysisExercises()
            .first { candidate -> candidate.estimated1RmEligible }
        val renamedExercise = exercise.copy(name = "Renamed without classification words")

        val originalFeatures = ExerciseAnalysisMapper.fromExercise(exercise)
        val renamedFeatures = ExerciseAnalysisMapper.fromExercise(renamedExercise)

        assertEquals(originalFeatures.movementPattern, renamedFeatures.movementPattern)
        assertEquals(originalFeatures.progressMetricType, renamedFeatures.progressMetricType)
        assertEquals(originalFeatures.badmintonTransferStrength, renamedFeatures.badmintonTransferStrength)
        assertEquals(originalFeatures.analysisEligibility, renamedFeatures.analysisEligibility)
        assertNotEquals(originalFeatures.exerciseName, renamedFeatures.exerciseName)
    }

    @Test
    fun exerciseAnalysisMapperDoesNotReconstructSemanticsFromExerciseRow() {
        val exercise = legacyAnalysisExercises()
            .first { candidate -> candidate.estimated1RmEligible }
        val runtimeDefault = RuntimeExerciseMetadataDefaults.forIdentity(exercise.stableKey, exercise.name)

        val features = ExerciseAnalysisMapper.fromExercise(exercise, runtimeDefault)

        assertEquals("NOT_APPLICABLE", features.progressMetricType)
        assertEquals("NOT_APPLICABLE", features.strengthProgressionGroup)
        assertFalse(features.estimated1RmEligible)
        assertFalse(features.volumeLoadEligible)
        assertTrue(features.analysisEligibility.isEmpty())
    }

    @Test
    fun exerciseAnalysisMapperUsesRuntimeMetadataOverridesForAnalysisTokens() {
        val exercise = Exercise(
            name = "사용자 수정 운동",
            category = "근력",
            stableKey = "barbell_deadlift",
            movementPattern = "HINGE",
            movementCategory = "STRENGTH",
            badmintonTransferStrength = "GENERAL"
        )
        val override = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            movementFamily = "FOOTWORK",
            movementSubtype = "SKILL_DRILL",
            primaryStressProfile = "COURT_SPORT_MOVEMENT_STRESS",
            secondaryStressTags = MetadataTokenField.parse("DECELERATION|ELASTIC_SSC"),
            tendonStressTags = MetadataTokenField.parse("ACHILLES_TENDON_STRESS"),
            ligamentJointStabilityStressTags = MetadataTokenField.parse("KNEE_VALGUS_CONTROL_STRESS"),
            jointImpactStressTags = MetadataTokenField.parse("JUMP_LANDING_IMPACT_STRESS"),
            cognitiveStressTags = MetadataTokenField.parse("REACTION_LOAD"),
            sportContextTags = MetadataTokenField.parse("BADMINTON_FOOTWORK"),
            badmintonTransferLevel = "SUPPORTIVE",
            badmintonTransferType = MetadataTokenField.parse("FOOTWORK|ACCELERATION"),
            badmintonPhysicalQualities = MetadataTokenField.parse("FIRST_STEP|LATERAL_MOVE")
        )

        val features = ExerciseAnalysisMapper.fromExercise(exercise, override)

        assertEquals("FOOTWORK", features.movementPattern)
        assertEquals("SKILL_DRILL", features.movementCategory)
        assertEquals("SUPPORTIVE", features.badmintonTransferStrength)
        assertTrue("FOOTWORK" in features.canonicalBadmintonTransferTypes)
        assertTrue("ACCELERATION" in features.canonicalBadmintonTransferTypes)
        assertTrue("FIRST_STEP" in features.badmintonPhysicalQualities)
        assertTrue("DECELERATION" in features.secondaryStressTags)
        assertTrue("ELASTIC_SSC" in features.secondaryStressTags)
        assertTrue("REACTION_LOAD" in features.cognitiveStressTags)
        assertTrue("ACHILLES_TENDON_STRESS" in features.tendonStressTags)
    }

    @Test
    fun exerciseAnalysisMapperDistinguishesPlannedAndCompletedSets() {
        val exercise = legacyAnalysisExercises()
            .first { candidate -> candidate.estimated1RmEligible }
        val entry = WorkoutEntry(
            id = 10,
            date = "2026-06-15",
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            category = exercise.category
        )
        val plannedSet = WorkoutSet(
            id = 1,
            entryId = entry.id,
            setIndex = 1,
            reps = 5,
            weightKg = 100.0,
            confirmed = false
        )
        val completedSet = plannedSet.copy(
            id = 2,
            setIndex = 2,
            confirmed = true,
            rpe = 8.0
        )

        val plannedFeatures = ExerciseAnalysisMapper.fromRecord(exercise, entry, listOf(plannedSet))
        val completedFeatures = ExerciseAnalysisMapper.fromRecord(exercise, entry, listOf(plannedSet, completedSet))

        assertTrue(plannedFeatures.isPlannedOnly)
        assertFalse(plannedFeatures.isCompleted)
        assertEquals(0, plannedFeatures.completedSets)
        assertTrue(completedFeatures.isCompleted)
        assertFalse(completedFeatures.isPlannedOnly)
        assertEquals(1, completedFeatures.completedSets)
        assertEquals(500.0, completedFeatures.totalVolumeLoad ?: 0.0, 0.001)
        assertNotNull(completedFeatures.estimated1Rm)
    }

    private fun legacyAnalysisExercises(): List<Exercise> {
        val legacyKeys = context.assets.open("metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv")
            .bufferedReader(Charsets.UTF_8)
            .use { reader -> RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(reader.readText()) }
            .mapTo(mutableSetOf(), RuntimeExerciseMetadata::stableKey)
        return CanonicalExerciseMetadataRepository(context)
            .exercises(includeHistory = true)
            .filter { exercise -> exercise.stableKey in legacyKeys }
    }

    private fun Exercise.weightValues(): List<Double> =
        listOf(
            systemicLoadWeight,
            neuralHeavyWeight,
            neuralSpeedWeight,
            localLoadWeight,
            decelerationWeight,
            elasticSscWeight,
            rotationPowerWeight,
            antiRotationWeight,
            overheadSwingWeight,
            gripLoadWeight
        )

    private fun Exercise.requiredFieldValues(): List<String> =
        listOf(
            movementPattern,
            movementCategory,
            primaryMuscles,
            equipment,
            compoundType,
            forceType,
            plane,
            laterality,
            axialLoadLevel,
            badmintonTransferRoles,
            fatigueCategories,
            adaptiveBaselineGroups,
            recoveryDecayProfile,
            progressMetricType,
            strengthProgressionGroup,
            hypertrophyVolumeGroup,
            mainLiftGroup,
            accessoryContributionGroup,
            badmintonTransferStrength,
            courtMovementTypes,
            badmintonSkillTargets,
            stabilityDemandLevel,
            mobilityDemandLevel,
            analysisEligibility,
            metadataConfidence
        )

    private fun String.hasToken(token: String): Boolean =
        split(',', '|', '/', ';')
            .map { value -> value.trim() }
            .any { value -> value == token }

    private fun String.hasAnyCourtRole(): Boolean =
        split(',', '|', '/', ';')
            .map { value -> value.trim() }
            .any { value ->
                value in setOf(
                    "FOOTWORK",
                    "REACTION",
                    "DECELERATION",
                    "LUNGE_REACH",
                    "JUMP_LANDING"
                )
            }

    private fun String.hasRealToken(): Boolean =
        split(',', '|', '/', ';')
            .map { value -> value.trim() }
            .any { value -> value.isNotEmpty() && value != "NONE" }
}

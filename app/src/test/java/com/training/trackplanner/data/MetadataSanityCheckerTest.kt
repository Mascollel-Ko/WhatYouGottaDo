package com.training.trackplanner.data

import com.training.trackplanner.analysis.features.ExerciseAnalysisMapper
import com.training.trackplanner.analysis.features.MetadataReadinessReporter
import com.training.trackplanner.analysis.features.ReadinessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MetadataSanityCheckerTest {
    @Test
    fun seedExercisesHaveValidFatigueMetadata() {
        val exercises = SeedData.exercisesFromParsedRows(seedRows())
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
            if (exercise.trainingRole == "PREHAB") {
                assertTrue(exercise.systemicLoadWeight < 0.5)
                assertFalse(exercise.analysisEligibility.hasToken("STRENGTH_PROGRESS"))
            }
            if (exercise.trainingRole == "TEST") {
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
            trainingRole = FatigueTrainingRole.ACCESSORY.name,
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
        val report = MetadataReadinessReporter.generate(SeedData.exercisesFromParsedRows(seedRows()))

        assertEquals(239, report.summary.totalExerciseCount)
        assertEquals(239, report.summary.fatigueReadyCounts[ReadinessStatus.YES])
        assertEquals(239, report.summary.progressReadyCounts[ReadinessStatus.YES])
        assertEquals(239, report.summary.badmintonReadyCounts[ReadinessStatus.YES])
        assertEquals(239, report.summary.balanceReadyCounts[ReadinessStatus.YES])
        assertEquals(emptyList<String>(), report.summary.needsReviewExerciseNames)
        assertTrue(report.mappingLayerExists)
    }

    @Test
    fun exerciseAnalysisMapperCreatesFeatureVectorWithoutNameParsing() {
        val exercise = SeedData.exercisesFromParsedRows(seedRows())
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
    fun exerciseAnalysisMapperFallsBackFromRuntimeDefaultMetadataToExerciseRow() {
        val exercise = SeedData.exercisesFromParsedRows(seedRows())
            .first { candidate -> candidate.estimated1RmEligible }
        val runtimeDefault = RuntimeExerciseMetadataDefaults.forIdentity(exercise.stableKey, exercise.name)

        val features = ExerciseAnalysisMapper.fromExercise(exercise, runtimeDefault)

        assertEquals("ESTIMATED_1RM", features.progressMetricType)
        assertEquals(exercise.strengthProgressionGroup, features.strengthProgressionGroup)
        assertTrue(features.estimated1RmEligible)
        assertTrue(features.volumeLoadEligible)
        assertTrue("STRENGTH_PROGRESS" in features.analysisEligibility)
        assertTrue("HYPERTROPHY_VOLUME" in features.analysisEligibility)
    }

    @Test
    fun exerciseAnalysisMapperDistinguishesPlannedAndCompletedSets() {
        val exercise = SeedData.exercisesFromParsedRows(seedRows())
            .first { candidate -> candidate.estimated1RmEligible }
        val entry = WorkoutEntry(
            id = 10,
            date = "2026-06-15",
            exerciseId = exercise.id,
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

    private fun seedRows(): List<Map<String, String>> {
        val file = listOf(
            File("src/main/assets/training_settings_seed.csv"),
            File("app/src/main/assets/training_settings_seed.csv")
        ).first { candidate -> candidate.exists() }
        val parsedRows = file.readLines(Charsets.UTF_8)
            .filter { line -> line.isNotBlank() }
            .map(::parseCsvLine)
        val header = parsedRows.first()
        return parsedRows.drop(1).map { values ->
            header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        values += current.toString()
        return values
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
            trainingRole,
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

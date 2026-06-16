package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PerformanceDropDetectorHotfixTest {
    private val today = LocalDate.parse("2026-06-15")
    private val exercise = strengthExercise()

    @Test
    fun singleSmallEstimatedOneRepMaxDropDoesNotBecomeElevated() {
        val result = PerformanceDropDetector().detect(
            entriesWithSets = listOf(
                record(date = today.minusDays(7), reps = 6, weight = 100.0, rpe = 7.0),
                record(date = today, reps = 6, weight = 94.0, rpe = 7.0)
            ),
            exerciseMap = mapOf(exercise.id to exercise),
            today = today
        )

        assertFalse(result.estimated1RmDrop)
        assertTrue(result.level.ordinal < FatigueLevel.ELEVATED.ordinal)
    }

    @Test
    fun multiplePerformanceSignalsCanBecomeElevated() {
        val result = PerformanceDropDetector().detect(
            entriesWithSets = listOf(
                record(date = today.minusDays(7), reps = 10, weight = 100.0, rpe = 7.0),
                record(date = today, reps = 6, weight = 100.0, rpe = 9.0),
                record(date = today.minusDays(9), reps = 8, weight = 95.0, rpe = 7.0),
                record(date = today.minusDays(8), reps = 8, weight = 95.0, rpe = 7.0)
            ),
            exerciseMap = mapOf(exercise.id to exercise),
            today = today
        )

        assertTrue(result.sameLoadRpeIncrease)
        assertTrue(result.sameLoadRepsDrop)
        assertTrue(result.estimated1RmDrop)
        assertTrue(result.level.ordinal >= FatigueLevel.ELEVATED.ordinal)
        assertTrue(result.reasons.isNotEmpty())
    }

    private fun record(date: LocalDate, reps: Int, weight: Double, rpe: Double): WorkoutEntryWithSets {
        val entry = WorkoutEntry(
            id = date.toEpochDay(),
            date = date.toString(),
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            category = exercise.category,
            rpe = rpe
        )
        return WorkoutEntryWithSets(
            entry = entry,
            sets = listOf(
                WorkoutSet(
                    id = entry.id,
                    entryId = entry.id,
                    setIndex = 1,
                    reps = reps,
                    weightKg = weight,
                    confirmed = true,
                    rpe = rpe
                )
            )
        )
    }

    private fun strengthExercise(): Exercise =
        Exercise(
            id = 3001,
            name = "Strength fixture",
            category = "strength",
            stableKey = "strength_fixture",
            movementPattern = "SQUAT",
            movementCategory = "STRENGTH",
            primaryMuscles = "QUADS",
            secondaryMuscles = "GLUTES",
            equipment = "BARBELL",
            compoundType = "COMPOUND",
            forceType = "SQUAT",
            plane = "SAGITTAL",
            laterality = "BILATERAL",
            axialLoadLevel = "HIGH",
            trainingRole = "MAIN_STRENGTH",
            fatigueCategories = "SYSTEMIC|NEURAL_HEAVY|LOCAL_MUSCLE",
            adaptiveBaselineGroups = "SYSTEMIC|HEAVY_LOWER|SQUAT_PATTERN",
            recoveryDecayProfile = "LONG",
            estimated1RmEligible = true,
            volumeLoadEligible = true,
            analysisEligibility = "FATIGUE|STRENGTH_PROGRESS",
            metadataConfidence = "HIGH"
        )
}

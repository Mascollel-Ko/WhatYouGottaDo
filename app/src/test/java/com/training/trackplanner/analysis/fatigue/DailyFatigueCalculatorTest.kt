package com.training.trackplanner.analysis.fatigue

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseMetadataAdapter
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyFatigueCalculatorTest {
    @Test
    fun factorsUseConfiguredRpeAndAxisLevels() {
        assertEquals(1.00, FatigueRecordFactors.rpeFactor(5.0), 0.0001)
        assertEquals(1.25, FatigueRecordFactors.rpeFactor(8.0), 0.0001)
        assertEquals(1.50, FatigueRecordFactors.rpeFactor(10.0), 0.0001)
        assertEquals(0.25, FatigueRecordFactors.axisLevelMultiplier("LOW"), 0.0001)
        assertEquals(1.00, FatigueRecordFactors.axisLevelMultiplier("VERY_HIGH"), 0.0001)
    }

    @Test
    fun calculatesSixSeparateAxesAndAllowsLocalHighSystemicLow() {
        val date = LocalDate.of(2026, 6, 19)
        val exercise = Exercise(
            id = 1,
            name = "테스트 컬",
            category = "근력운동",
            stableKey = "test_curl"
        )
        val metadata = ExerciseMetadataAdapter.fromFields(
            mapOf(
                "stableKey" to "test_curl",
                "exerciseName" to "테스트 컬",
                "currentActivityKind" to "EXERCISE",
                "currentPlanningEligibility" to "PROGRAM_SELECTABLE",
                "movementFamily" to "ELBOW_FLEXION_BICEPS_CURL_VARIANTS",
                "movementSubtype" to "DUMBBELL_CURL",
                "programSlot" to "BICEPS_ACCESSORY",
                "redundancyGroup" to "ELBOW_FLEXION_CURL",
                "progressMetricType" to "LOAD_REPS",
                "strengthProgressionGroup" to "ELBOW_FLEXION_CURL",
                "analysisEligibility" to "FATIGUE|HYPERTROPHY_VOLUME",
                "primaryStressProfile" to "LOCAL_MUSCLE",
                "secondaryStressTags" to "ISOLATION",
                "tendonStressTags" to "ELBOW_FLEXOR_TENDON",
                "ligamentJointStabilityStressTags" to "NONE",
                "jointImpactStressTags" to "NONE",
                "cognitiveStressTags" to "NONE",
                "sportContextTags" to "NONE",
                "recoveryDecayProfile" to "SHORT",
                "stressMagnitudeHint" to "LOW",
                "badmintonTransferLevel" to "GENERAL",
                "neuromuscularStressLevel" to "LOW",
                "systemicMuscularStressLevel" to "LOW",
                "localMuscularStressLevel" to "VERY_HIGH",
                "jointTendonImpactStressLevel" to "HIGH",
                "movementFocusDemandLevel" to "LOW",
                "recoveryDurationClass" to "SHORT"
            )
        )
        val entry = WorkoutEntry(id = 1, date = date.toString(), exerciseId = 1, exerciseName = exercise.name, category = exercise.category)
        val record = WorkoutEntryWithSets(
            entry,
            listOf(
                WorkoutSet(entryId = 1, setIndex = 1, reps = 12, weightKg = 10.0, confirmed = true, rpe = 8.0),
                WorkoutSet(entryId = 1, setIndex = 2, reps = 12, weightKg = 10.0, confirmed = true, rpe = 8.0),
                WorkoutSet(entryId = 1, setIndex = 3, reps = 12, weightKg = 10.0, confirmed = true, rpe = 8.0)
            )
        )

        val result = DailyFatigueCalculator(RuntimeExerciseMetadataCatalog.of(listOf(metadata))).calculate(
            targetDate = date,
            exercises = listOf(exercise),
            entriesWithSets = listOf(record),
            initialProfile = null
        )

        val contribution = result.recordContributions.single()
        assertTrue(contribution.axes.neuromuscular > 0.0)
        assertTrue(contribution.axes.systemicMuscular > 0.0)
        assertTrue(contribution.axes.localMuscular > contribution.axes.systemicMuscular)
        assertTrue(contribution.axes.jointTendonImpact > 0.0)
        assertTrue(contribution.axes.movementFocus > 0.0)
        assertTrue(contribution.axes.recoveryPressure > 0.0)
        assertEquals("MEDIUM", contribution.jointRecoveryDurationClass)
    }

    @Test
    fun recoveryPressureUsesHalfMaxAndHalfMean() {
        val pressure = RecoveryPressureCalculator.calculate(listOf(10.0, 20.0, 30.0, 40.0, 50.0), 1.25)

        assertEquals(50.0, pressure, 0.0001)
    }
}

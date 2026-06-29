package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.WorkoutEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisExerciseDisplayNameResolverTest {
    private val fallbackName = "운동" + "113"

    @Test
    fun entryExerciseNameWinsOverFallbackExerciseName() {
        val exercise = exercise(name = fallbackName, stableKey = "missing_key")
        val entry = entry(exercise, exerciseName = "스쿼트")

        val resolved = AnalysisExerciseDisplayNameResolver.resolve(
            entry = entry,
            exercise = exercise,
            catalog = RuntimeExerciseMetadataCatalog.EMPTY
        )

        assertEquals("스쿼트", resolved)
    }

    @Test
    fun canonicalStableKeyNameWinsWhenEntryAndExerciseAreFallback() {
        val exercise = exercise(name = fallbackName, stableKey = "barbell_back_squat")
        val entry = entry(exercise, exerciseName = "Unknown exercise")
        val catalog = RuntimeExerciseMetadataCatalog.of(
            listOf(RuntimeExerciseMetadataDefaults.forIdentity("barbell_back_squat", "스쿼트"))
        )

        val resolved = AnalysisExerciseDisplayNameResolver.resolve(entry, exercise, catalog)

        assertEquals("스쿼트", resolved)
    }

    @Test
    fun customExerciseNameIsPreserved() {
        val exercise = exercise(name = "내 커스텀 하체운동", stableKey = "custom_123", isCustom = true)

        val resolved = AnalysisExerciseDisplayNameResolver.resolve(
            entry = null,
            exercise = exercise,
            catalog = RuntimeExerciseMetadataCatalog.EMPTY
        )

        assertEquals("내 커스텀 하체운동", resolved)
    }

    private fun exercise(name: String, stableKey: String, isCustom: Boolean = false): Exercise =
        Exercise(
            id = 113,
            name = name,
            category = "strength",
            stableKey = stableKey,
            isCustom = isCustom
        )

    private fun entry(exercise: Exercise, exerciseName: String): WorkoutEntry =
        WorkoutEntry(
            id = 1,
            date = "2026-06-15",
            exerciseId = exercise.id,
            exerciseName = exerciseName,
            category = exercise.category
        )
}

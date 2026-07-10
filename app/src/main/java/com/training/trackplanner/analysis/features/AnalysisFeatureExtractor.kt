package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet

object AnalysisFeatureExtractor {
    fun fromExercise(
        exercise: Exercise,
        runtimeMetadata: RuntimeExerciseMetadata? = null
    ): AnalysisExerciseFeatures =
        ExerciseAnalysisMapper.fromExercise(exercise, runtimeMetadata)

    fun fromRecord(
        exercise: Exercise,
        entry: WorkoutEntry?,
        sets: List<WorkoutSet>,
        runtimeMetadata: RuntimeExerciseMetadata? = null,
        bodyWeightKg: Double? = null
    ): AnalysisExerciseFeatures =
        ExerciseAnalysisMapper.fromRecord(exercise, entry, sets, runtimeMetadata, bodyWeightKg)
}

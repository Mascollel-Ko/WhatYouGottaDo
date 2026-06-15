package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet

object AnalysisFeatureExtractor {
    fun fromExercise(exercise: Exercise): AnalysisExerciseFeatures =
        ExerciseAnalysisMapper.fromExercise(exercise)

    fun fromRecord(
        exercise: Exercise,
        entry: WorkoutEntry?,
        sets: List<WorkoutSet>
    ): AnalysisExerciseFeatures =
        ExerciseAnalysisMapper.fromRecord(exercise, entry, sets)
}

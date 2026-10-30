package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.CanonicalMuscleLookup
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet

object AnalysisFeatureExtractor {
    fun fromExercise(
        exercise: Exercise,
        runtimeMetadata: RuntimeExerciseMetadata? = null,
        canonicalBadmintonAuthority: Boolean = false,
        canonicalMuscles: CanonicalMuscleLookup? = null
    ): AnalysisExerciseFeatures =
        ExerciseAnalysisMapper.fromExercise(
            exercise, runtimeMetadata, canonicalBadmintonAuthority, canonicalMuscles
        )

    fun fromRecord(
        exercise: Exercise,
        entry: WorkoutEntry?,
        sets: List<WorkoutSet>,
        runtimeMetadata: RuntimeExerciseMetadata? = null,
        bodyWeightKg: Double? = null,
        canonicalBadmintonAuthority: Boolean = false,
        canonicalMuscles: CanonicalMuscleLookup? = null
    ): AnalysisExerciseFeatures =
        ExerciseAnalysisMapper.fromRecord(
            exercise,
            entry,
            sets,
            runtimeMetadata,
            bodyWeightKg,
            canonicalBadmintonAuthority,
            canonicalMuscles
        )
}

package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.WorkoutSet

object BodyweightEffectiveLoadCalculator {
    fun bodyWeightFor(
        date: String,
        dailyMetrics: List<DailyMetric>,
        initialProfile: InitialUserProfile?
    ): Double? =
        dailyMetrics
            .filter { metric -> metric.date <= date }
            .maxByOrNull(DailyMetric::date)
            ?.bodyWeightKg
            ?: initialProfile?.bodyWeightKg

    fun volumeLoad(exercise: Exercise, set: WorkoutSet, bodyWeightKg: Double?): Double =
        effectiveVolumeLoadOrNull(
            stableKey = exercise.stableKey,
            reps = set.reps,
            weightKg = set.weightKg,
            bodyWeightKg = bodyWeightKg
        ) ?: rawVolumeLoad(set.reps, set.weightKg)

    fun effectiveVolumeLoadOrNull(
        stableKey: String,
        reps: Int,
        weightKg: Double,
        bodyWeightKg: Double?
    ): Double? {
        if (reps <= 0 || bodyWeightKg == null) return null
        val profile = BodyweightLoadProfileAuthority.resolve(stableKey) ?: return null
        val load = when (profile.policy) {
            BodyweightLoadPolicy.BODYWEIGHT_PLUS_ADDED -> bodyWeightKg + weightKg
            BodyweightLoadPolicy.BODYWEIGHT_MINUS_ASSISTANCE ->
                (bodyWeightKg - weightKg).coerceAtLeast(0.0)
            BodyweightLoadPolicy.BODYWEIGHT_FACTOR_PLUS_ADDED_FACTOR ->
                bodyWeightKg * profile.bodyweightFactor + weightKg * profile.addedWeightFactor
        }
        return load * reps
    }

    private fun rawVolumeLoad(reps: Int, weightKg: Double): Double =
        if (reps > 0 && weightKg > 0.0) reps * weightKg else 0.0
}

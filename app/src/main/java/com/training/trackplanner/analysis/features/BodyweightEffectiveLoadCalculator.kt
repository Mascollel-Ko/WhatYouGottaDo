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
            displayName = exercise.name,
            familyId = exercise.familyId,
            familyName = exercise.familyName,
            movementPattern = exercise.movementPattern,
            movementCategory = exercise.movementCategory,
            equipment = exercise.equipment.ifBlank { exercise.equipmentTags },
            mode = exercise.mode,
            category = exercise.category,
            reps = set.reps,
            weightKg = set.weightKg,
            bodyWeightKg = bodyWeightKg
        ) ?: rawVolumeLoad(set.reps, set.weightKg)

    fun effectiveVolumeLoadOrNull(
        stableKey: String,
        displayName: String,
        familyId: String = "",
        familyName: String = "",
        movementPattern: String = "",
        movementCategory: String = "",
        equipment: String = "",
        mode: String = "",
        category: String = "",
        reps: Int,
        weightKg: Double,
        bodyWeightKg: Double?
    ): Double? {
        if (reps <= 0 || bodyWeightKg == null) return null
        val profile = profileFor(
            stableKey = stableKey,
            displayName = displayName,
            familyId = familyId,
            familyName = familyName,
            movementPattern = movementPattern,
            movementCategory = movementCategory,
            equipment = equipment,
            mode = mode,
            category = category
        ) ?: return null
        val load = when (profile.policy) {
            BodyweightVolumePolicy.BODYWEIGHT_PLUS_ADDED ->
                bodyWeightKg + weightKg
            BodyweightVolumePolicy.BODYWEIGHT_MINUS_ASSIST ->
                (bodyWeightKg - weightKg).coerceAtLeast(0.0)
            BodyweightVolumePolicy.BODYWEIGHT_FACTOR_PLUS_ADDED_FACTOR ->
                bodyWeightKg * profile.bodyweightFactor + weightKg * profile.addedWeightFactor
        }
        return load * reps
    }

    private fun rawVolumeLoad(reps: Int, weightKg: Double): Double =
        if (reps > 0 && weightKg > 0.0) reps * weightKg else 0.0

    private fun profileFor(
        stableKey: String,
        displayName: String,
        familyId: String,
        familyName: String,
        movementPattern: String,
        movementCategory: String,
        equipment: String,
        mode: String,
        category: String
    ): BodyweightVolumeProfile? {
        val text = listOf(
            stableKey,
            displayName,
            familyId,
            familyName,
            movementPattern,
            movementCategory,
            equipment,
            mode,
            category
        ).joinToString(" ").lowercase()
        return when {
            text.hasAny("assisted_pull", "assisted pull", "보조 풀", "어시스트") ->
                BodyweightVolumeProfile(BodyweightVolumePolicy.BODYWEIGHT_MINUS_ASSIST)
            text.hasAny("pull_up", "chin_up", "pull-up", "chin-up", "풀업", "친업", "턱걸이") &&
                !text.hasAny("pulldown", "pull_down", "풀다운") ->
                BodyweightVolumeProfile(BodyweightVolumePolicy.BODYWEIGHT_PLUS_ADDED)
            text.hasAny("dip", "딥스") ->
                BodyweightVolumeProfile(BodyweightVolumePolicy.BODYWEIGHT_PLUS_ADDED)
            text.hasAny("inverted_row", "inverted row", "인버티드 로우") ->
                BodyweightVolumeProfile(
                    policy = BodyweightVolumePolicy.BODYWEIGHT_FACTOR_PLUS_ADDED_FACTOR,
                    bodyweightFactor = 0.60,
                    addedWeightFactor = 1.0
                )
            text.hasAny("push_up", "push-up", "푸시업", "푸쉬업") ->
                pushUpProfile(text)
            else -> null
        }
    }

    private fun pushUpProfile(text: String): BodyweightVolumeProfile =
        BodyweightVolumeProfile(
            policy = BodyweightVolumePolicy.BODYWEIGHT_FACTOR_PLUS_ADDED_FACTOR,
            bodyweightFactor = when {
                text.hasAny("decline", "디클라인") -> 0.80
                text.hasAny("pike", "파이크") -> 0.70
                text.hasAny("incline", "인클라인") -> 0.55
                else -> 0.65
            },
            addedWeightFactor = 0.70
        )

    private fun String.hasAny(vararg tokens: String): Boolean =
        tokens.any { token -> contains(token, ignoreCase = true) }

    private enum class BodyweightVolumePolicy {
        BODYWEIGHT_PLUS_ADDED,
        BODYWEIGHT_MINUS_ASSIST,
        BODYWEIGHT_FACTOR_PLUS_ADDED_FACTOR
    }

    private data class BodyweightVolumeProfile(
        val policy: BodyweightVolumePolicy,
        val bodyweightFactor: Double = 1.0,
        val addedWeightFactor: Double = 1.0
    )
}

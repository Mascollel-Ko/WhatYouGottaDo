package com.training.trackplanner.analysis.proxyperformance

import com.training.trackplanner.analysis.features.ExerciseAnalysisMapper
import com.training.trackplanner.analysis.lab.MuscleLoadInputBuilder
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.WorkoutEntry

internal object ProxyPerformanceLoadingBuilder {
    fun build(
        exercise: Exercise,
        entry: WorkoutEntry,
        runtimeMetadata: RuntimeExerciseMetadata?
    ): ProxyPerformanceLoading {
        val directTarget = directTarget(exercise, entry)
        val features = ExerciseAnalysisMapper.fromExercise(exercise, runtimeMetadata)
        val metadataText = listOf(
            features.movementFamily,
            features.movementSubtype,
            features.programSlot,
            features.redundancyGroup,
            features.movementPattern,
            features.movementCategory,
            features.strengthProgressionGroup,
            features.mainLiftGroup,
            features.accessoryContributionGroup,
            features.trainingRole,
            exercise.familyId,
            exercise.familyName
        ).joinToString("|").uppercase()
        val raw = when {
            directTarget == MajorLiftTarget.BENCH_PRESS -> loading(
                ProxyLatentFactor.PRESS_SHARED to 0.90,
                ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 1.00,
                ProxyLatentFactor.BENCH_SPECIFIC to 1.00
            )
            directTarget == MajorLiftTarget.SQUAT -> loading(
                ProxyLatentFactor.KNEE_EXTENSION to 1.00,
                ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.75,
                ProxyLatentFactor.TRUNK_BRACING to 0.65,
                ProxyLatentFactor.SQUAT_SPECIFIC to 1.00
            )
            directTarget == MajorLiftTarget.DEADLIFT -> loading(
                ProxyLatentFactor.KNEE_EXTENSION to 0.45,
                ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 1.00,
                ProxyLatentFactor.TRUNK_BRACING to 0.85,
                ProxyLatentFactor.DEADLIFT_SPECIFIC to 1.00
            )
            runtimeMetadata == null && exercise.isCustom -> emptyMap()
            "BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS" in metadataText -> benchVariation(metadataText)
            "DIP_COMPOUND_PUSH_VARIANTS" in metadataText || "DIP_COMPOUND_PUSH" in metadataText -> loading(
                ProxyLatentFactor.PRESS_SHARED to 0.65,
                ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 0.25,
                ProxyLatentFactor.BENCH_SPECIFIC to 0.05
            )
            "OVERHEAD_PRESS_VARIANTS" in metadataText || "OVERHEAD_PRESS" in metadataText -> loading(
                ProxyLatentFactor.PRESS_SHARED to 0.55,
                ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 0.15
            )
            "LANDMINE" in metadataText && "PRESS" in metadataText -> loading(
                ProxyLatentFactor.PRESS_SHARED to 0.45,
                ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 0.10
            )
            "LUNGE_SPLIT_SQUAT_VARIANTS" in metadataText -> loading(
                ProxyLatentFactor.KNEE_EXTENSION to 0.85,
                ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.55,
                ProxyLatentFactor.TRUNK_BRACING to 0.35,
                ProxyLatentFactor.SQUAT_SPECIFIC to 0.20
            )
            "LEG_PRESS_KNEE_DOMINANT_MACHINE_VARIANTS" in metadataText -> loading(
                ProxyLatentFactor.KNEE_EXTENSION to 1.00,
                ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.45,
                ProxyLatentFactor.TRUNK_BRACING to 0.10,
                ProxyLatentFactor.SQUAT_SPECIFIC to 0.05
            )
            "SQUAT_VARIANTS" in metadataText -> squatVariation(metadataText)
            "DEADLIFT_HINGE_VARIANTS" in metadataText -> hingeVariation(metadataText)
            "HIP_THRUST_GLUTE_BRIDGE_VARIANTS" in metadataText -> loading(
                ProxyLatentFactor.KNEE_EXTENSION to 0.15,
                ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.85,
                ProxyLatentFactor.TRUNK_BRACING to 0.20,
                ProxyLatentFactor.DEADLIFT_SPECIFIC to 0.05
            )
            else -> emptyMap()
        }
        val confidence = metadataConfidence(exercise, runtimeMetadata, directTarget)
        val factors = raw.mapValues { (_, value) -> (value * confidence).coerceIn(0.0, 1.0) }
            .filterValues { value -> value > 0.0 }
        return ProxyPerformanceLoading(
            factors = factors,
            reasons = factors.keys.map(ProxyLatentFactor::name),
            metadataConfidence = confidence
        )
    }

    fun directTarget(exercise: Exercise?, entry: WorkoutEntry): MajorLiftTarget? = when {
        MuscleLoadInputBuilder.isMainBenchPress(exercise, entry) -> MajorLiftTarget.BENCH_PRESS
        MuscleLoadInputBuilder.isMainSquat(exercise, entry) -> MajorLiftTarget.SQUAT
        MuscleLoadInputBuilder.isMainDeadlift(exercise, entry) -> MajorLiftTarget.DEADLIFT
        else -> null
    }

    fun targetLoading(target: MajorLiftTarget, variant: ProxyModelVariant): Map<ProxyLatentFactor, Double> {
        val full = when (target) {
            MajorLiftTarget.BENCH_PRESS -> loading(
                ProxyLatentFactor.PRESS_SHARED to 0.90,
                ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 1.00,
                ProxyLatentFactor.BENCH_SPECIFIC to 1.00
            )
            MajorLiftTarget.SQUAT -> loading(
                ProxyLatentFactor.KNEE_EXTENSION to 1.00,
                ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.75,
                ProxyLatentFactor.TRUNK_BRACING to 0.65,
                ProxyLatentFactor.SQUAT_SPECIFIC to 1.00
            )
            MajorLiftTarget.DEADLIFT -> loading(
                ProxyLatentFactor.KNEE_EXTENSION to 0.45,
                ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 1.00,
                ProxyLatentFactor.TRUNK_BRACING to 0.85,
                ProxyLatentFactor.DEADLIFT_SPECIFIC to 1.00
            )
        }
        val active = ProxyPerformanceStateModel.config(target, variant).factors.toSet()
        return full.filterKeys(active::contains)
    }

    fun targetAffinity(loading: ProxyPerformanceLoading, target: MajorLiftTarget): Double {
        val targetLoading = targetLoading(target, ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC)
        val numerator = loading.factors.entries.sumOf { (factor, value) -> value * targetLoading.getOrDefault(factor, 0.0) }
        val denominator = targetLoading.values.sumOf { value -> value * value }
        return if (denominator > 0.0) (numerator / denominator).coerceIn(0.0, 1.0) else 0.0
    }

    private fun benchVariation(text: String): Map<ProxyLatentFactor, Double> = when {
        "CLOSE_GRIP" in text -> loading(
            ProxyLatentFactor.PRESS_SHARED to 0.90,
            ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 0.90,
            ProxyLatentFactor.BENCH_SPECIFIC to 0.65
        )
        "DUMBBELL" in text && "INCLINE" in text -> loading(
            ProxyLatentFactor.PRESS_SHARED to 0.85,
            ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 0.65,
            ProxyLatentFactor.BENCH_SPECIFIC to 0.25
        )
        "DUMBBELL" in text -> loading(
            ProxyLatentFactor.PRESS_SHARED to 0.85,
            ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 0.90,
            ProxyLatentFactor.BENCH_SPECIFIC to 0.25
        )
        "MACHINE" in text -> loading(
            ProxyLatentFactor.PRESS_SHARED to 0.80,
            ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 0.75,
            ProxyLatentFactor.BENCH_SPECIFIC to 0.10
        )
        "INCLINE" in text -> loading(
            ProxyLatentFactor.PRESS_SHARED to 0.85,
            ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 0.65,
            ProxyLatentFactor.BENCH_SPECIFIC to 0.25
        )
        else -> loading(
            ProxyLatentFactor.PRESS_SHARED to 0.80,
            ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 0.75,
            ProxyLatentFactor.BENCH_SPECIFIC to 0.15
        )
    }

    private fun squatVariation(text: String): Map<ProxyLatentFactor, Double> = when {
        "FRONT_SQUAT" in text -> loading(
            ProxyLatentFactor.KNEE_EXTENSION to 0.95,
            ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.60,
            ProxyLatentFactor.TRUNK_BRACING to 0.80,
            ProxyLatentFactor.SQUAT_SPECIFIC to 0.55
        )
        "BODYWEIGHT" in text -> loading(
            ProxyLatentFactor.KNEE_EXTENSION to 0.35,
            ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.30,
            ProxyLatentFactor.TRUNK_BRACING to 0.20
        )
        "MACHINE" in text || "HACK_SQUAT" in text -> loading(
            ProxyLatentFactor.KNEE_EXTENSION to 0.85,
            ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.65,
            ProxyLatentFactor.TRUNK_BRACING to 0.35,
            ProxyLatentFactor.SQUAT_SPECIFIC to 0.25
        )
        else -> loading(
            ProxyLatentFactor.KNEE_EXTENSION to 0.75,
            ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.55,
            ProxyLatentFactor.TRUNK_BRACING to 0.35,
            ProxyLatentFactor.SQUAT_SPECIFIC to 0.20
        )
    }

    private fun hingeVariation(text: String): Map<ProxyLatentFactor, Double> = when {
        "ROMANIAN" in text || "_RDL" in text || "RDL_" in text -> loading(
            ProxyLatentFactor.KNEE_EXTENSION to 0.15,
            ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 1.00,
            ProxyLatentFactor.TRUNK_BRACING to 0.70,
            ProxyLatentFactor.DEADLIFT_SPECIFIC to 0.40
        )
        "GOOD_MORNING" in text -> loading(
            ProxyLatentFactor.KNEE_EXTENSION to 0.10,
            ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.90,
            ProxyLatentFactor.TRUNK_BRACING to 0.80,
            ProxyLatentFactor.DEADLIFT_SPECIFIC to 0.15
        )
        else -> loading(
            ProxyLatentFactor.KNEE_EXTENSION to 0.25,
            ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.85,
            ProxyLatentFactor.TRUNK_BRACING to 0.60,
            ProxyLatentFactor.DEADLIFT_SPECIFIC to 0.25
        )
    }

    private fun metadataConfidence(
        exercise: Exercise,
        runtimeMetadata: RuntimeExerciseMetadata?,
        directTarget: MajorLiftTarget?
    ): Double {
        if (directTarget != null) return 1.0
        if (runtimeMetadata == null) return if (exercise.isCustom) 0.0 else 0.35
        val token = listOf(
            runtimeMetadata.sourceConfidenceLevel,
            runtimeMetadata.transferConfidence,
            exercise.metadataConfidence
        ).joinToString("|").uppercase()
        return when {
            "VERY_HIGH" in token || "HIGH" in token -> 1.0
            "MODERATE" in token || "MEDIUM" in token -> 0.80
            "LOW" in token -> 0.55
            else -> 0.65
        }
    }

    private fun loading(vararg values: Pair<ProxyLatentFactor, Double>): Map<ProxyLatentFactor, Double> =
        mapOf(*values)
}

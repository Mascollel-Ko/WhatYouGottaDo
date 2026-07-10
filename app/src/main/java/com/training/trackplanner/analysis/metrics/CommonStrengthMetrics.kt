package com.training.trackplanner.analysis.metrics

import com.training.trackplanner.analysis.features.BodyweightEffectiveLoadCalculator
import com.training.trackplanner.analysis.core.AnalysisEntry
import com.training.trackplanner.analysis.core.AnalysisExerciseMetadata
import com.training.trackplanner.analysis.core.AnalysisInputSnapshot
import com.training.trackplanner.analysis.features.DurationHoldLoadCalculator

data class CommonStrengthMetricsResult(
    val totalVolumeLoad: Double,
    val volumeLoadByExercise: Map<String, Double>,
    val volumeLoadByMovementPattern: Map<String, Double>,
    val volumeLoadByMuscleGroup: Map<String, Double>,
    val setCountByMovementPattern: Map<String, Int>,
    val hardSetCandidateCount: Int,
    val estimatedIntensityZoneCandidate: String?,
    val axialLoadScoreCandidate: Double?,
    val unilateralSetCount: Int,
    val upperLowerRatioCandidate: Double?,
    val pushPullRatioCandidate: Double?,
    val squatHingeRatioCandidate: Double?
)

object CommonStrengthMetrics {
    fun calculate(input: AnalysisInputSnapshot): CommonStrengthMetricsResult {
        val entries = input.completedEntriesUntilToday
        val totalVolumeLoad = entries.sumOf { entry -> entry.volumeLoad(input) }
        val volumeByExercise = entries
            .groupBy { it.exerciseName }
            .mapValues { (_, groupedEntries) -> groupedEntries.sumOf { it.volumeLoad(input) } }
        val volumeByMovementPattern = entries.sumVolumeByMetadata(
            input = input,
            tokenSelector = { movementPattern }
        )
        val volumeByMuscleGroup = entries.sumVolumeByMetadataTokens(
            input = input,
            tokenSelector = { primaryMuscles }
        )
        val setCountByMovementPattern = entries.sumSetsByMetadata(
            input = input,
            tokenSelector = { movementPattern }
        )
        val hardSetCandidateCount = entries.sumOf { entry ->
            entry.sets.count { set -> (set.rpe ?: 0.0) >= HARD_SET_RPE_CANDIDATE }
        }
        val unilateralSetCount = entries.sumOf { entry ->
            val laterality = input.exerciseMetadataMap[entry.exerciseId]?.laterality.orUnknown()
            if (laterality.startsWith("UNILATERAL") || laterality == "CONTRALATERAL") {
                entry.sets.size
            } else {
                0
            }
        }

        return CommonStrengthMetricsResult(
            totalVolumeLoad = totalVolumeLoad,
            volumeLoadByExercise = volumeByExercise,
            volumeLoadByMovementPattern = volumeByMovementPattern,
            volumeLoadByMuscleGroup = volumeByMuscleGroup,
            setCountByMovementPattern = setCountByMovementPattern,
            hardSetCandidateCount = hardSetCandidateCount,
            estimatedIntensityZoneCandidate = null,
            axialLoadScoreCandidate = null,
            unilateralSetCount = unilateralSetCount,
            upperLowerRatioCandidate = ratioFromBodyRegions(entries, input, "UPPER", "LOWER"),
            pushPullRatioCandidate = ratioFromMovementPrefixes(entries, input, "PUSH", "PULL"),
            squatHingeRatioCandidate = ratioFromMovementTokens(
                entries,
                input,
                numerator = "KNEE_DOMINANT_LOWER",
                denominator = "HINGE_LOWER"
            )
        )
    }

    private fun List<AnalysisEntry>.sumVolumeByMetadata(
        input: AnalysisInputSnapshot,
        tokenSelector: AnalysisExerciseMetadata.() -> String
    ): Map<String, Double> =
        groupBy { entry ->
            input.exerciseMetadataMap[entry.exerciseId]?.tokenSelector().orUnknown()
        }.mapValues { (_, entries) -> entries.sumOf { it.volumeLoad(input) } }

    private fun List<AnalysisEntry>.sumVolumeByMetadataTokens(
        input: AnalysisInputSnapshot,
        tokenSelector: AnalysisExerciseMetadata.() -> String
    ): Map<String, Double> {
        val result = linkedMapOf<String, Double>()
        forEach { entry ->
            val tokens = input.exerciseMetadataMap[entry.exerciseId]
                ?.tokenSelector()
                .tokensOrUnknown()
            tokens.forEach { token ->
                result[token] = (result[token] ?: 0.0) + entry.volumeLoad(input)
            }
        }
        return result
    }

    private fun List<AnalysisEntry>.sumSetsByMetadata(
        input: AnalysisInputSnapshot,
        tokenSelector: AnalysisExerciseMetadata.() -> String
    ): Map<String, Int> =
        groupBy { entry ->
            input.exerciseMetadataMap[entry.exerciseId]?.tokenSelector().orUnknown()
        }.mapValues { (_, entries) -> entries.sumOf { it.sets.size } }

    private fun ratioFromBodyRegions(
        entries: List<AnalysisEntry>,
        input: AnalysisInputSnapshot,
        numerator: String,
        denominator: String
    ): Double? {
        val setsByRegion = entries.sumSetsByMetadata(input) { bodyRegion }
        val numeratorCount = setsByRegion[numerator] ?: 0
        val denominatorCount = setsByRegion[denominator] ?: 0
        return ratioOrNull(numeratorCount, denominatorCount)
    }

    private fun ratioFromMovementPrefixes(
        entries: List<AnalysisEntry>,
        input: AnalysisInputSnapshot,
        numerator: String,
        denominator: String
    ): Double? {
        val setsByPattern = entries.sumSetsByMetadata(input) { movementPattern }
        val numeratorCount = setsByPattern
            .filterKeys { key -> key == numerator || key.startsWith("${numerator}_") || key.endsWith("_$numerator") || key.contains("_${numerator}_") }
            .values
            .sum()
        val denominatorCount = setsByPattern
            .filterKeys { key -> key == denominator || key.startsWith("${denominator}_") || key.endsWith("_$denominator") || key.contains("_${denominator}_") }
            .values
            .sum()
        return ratioOrNull(numeratorCount, denominatorCount)
    }

    private fun ratioFromMovementTokens(
        entries: List<AnalysisEntry>,
        input: AnalysisInputSnapshot,
        numerator: String,
        denominator: String
    ): Double? {
        val setsByPattern = entries.sumSetsByMetadata(input) { movementPattern }
        return ratioOrNull(setsByPattern[numerator] ?: 0, setsByPattern[denominator] ?: 0)
    }

    private fun AnalysisEntry.volumeLoad(input: AnalysisInputSnapshot): Double {
        val metadata = input.exerciseMetadataMap[exerciseId]
        val bodyWeightKg = input.conditionRecordsUntilToday
            .filter { record -> record.date <= date }
            .maxByOrNull { record -> record.date }
            ?.bodyWeightKg
        return sets.sumOf { set ->
            val corrected = metadata?.let { item ->
                DurationHoldLoadCalculator.holdLoadOrNull(
                    stableKey = item.stableKey,
                    displayName = exerciseName,
                    movementPattern = item.movementPattern,
                    movementCategory = item.movementCategory,
                    equipment = item.equipment.ifBlank { item.equipmentTags },
                    category = item.category,
                    seconds = set.seconds,
                    rpe = set.rpe ?: rpe
                ) ?: BodyweightEffectiveLoadCalculator.effectiveVolumeLoadOrNull(
                    stableKey = item.stableKey,
                    displayName = exerciseName,
                    movementPattern = item.movementPattern,
                    movementCategory = item.movementCategory,
                    equipment = item.equipment.ifBlank { item.equipmentTags },
                    category = item.category,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    bodyWeightKg = bodyWeightKg
                )
            }
            corrected ?: if (set.reps > 0 && set.weightKg > 0.0) set.reps * set.weightKg else 0.0
        }
    }

    private fun String?.orUnknown(): String =
        this?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN

    private fun String?.tokensOrUnknown(): List<String> {
        val tokens = this.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return tokens.ifEmpty { listOf(UNKNOWN) }
    }

    private fun ratioOrNull(numerator: Int, denominator: Int): Double? =
        if (denominator > 0) numerator.toDouble() / denominator else null

    private const val HARD_SET_RPE_CANDIDATE = 7.0
    private const val UNKNOWN = "UNKNOWN"
}

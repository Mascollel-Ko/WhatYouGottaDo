package com.training.trackplanner.analysis.metrics

import com.training.trackplanner.analysis.core.AnalysisEntry
import com.training.trackplanner.analysis.core.AnalysisExerciseMetadata
import com.training.trackplanner.analysis.core.AnalysisInputSnapshot

data class CommonTaxonomyMetricsResult(
    val movementPatternDistribution: Map<String, Int>,
    val movementCategoryDistribution: Map<String, Int>,
    val lateralityDistribution: Map<String, Int>,
    val planeDistribution: Map<String, Int>,
    val primaryMuscleDistribution: Map<String, Int>,
    val secondaryMuscleDistribution: Map<String, Int>,
    val trainingRoleDistribution: Map<String, Int>,
    val sportTransferDistribution: Map<String, Int>,
    val stabilityRoleDistribution: Map<String, Int>,
    val loadProfileDistribution: Map<String, Int>
)

object CommonTaxonomyMetrics {
    fun calculate(input: AnalysisInputSnapshot): CommonTaxonomyMetricsResult {
        val entries = input.completedEntriesUntilToday
        return CommonTaxonomyMetricsResult(
            movementPatternDistribution = entries.distribution(input) { movementPattern },
            movementCategoryDistribution = entries.distribution(input) { movementCategory },
            lateralityDistribution = entries.distribution(input) { laterality },
            planeDistribution = entries.distribution(input) { plane },
            primaryMuscleDistribution = entries.tokenDistribution(input) { primaryMuscles },
            secondaryMuscleDistribution = entries.tokenDistribution(input) { secondaryMuscles },
            trainingRoleDistribution = entries.distribution(input) { trainingRole },
            sportTransferDistribution = entries.sportTransferDistribution(input),
            stabilityRoleDistribution = entries.tokenDistribution(input) { stabilityRoles },
            loadProfileDistribution = entries.distribution(input) { loadProfile }
        )
    }

    private fun List<AnalysisEntry>.distribution(
        input: AnalysisInputSnapshot,
        selector: AnalysisExerciseMetadata.() -> String
    ): Map<String, Int> =
        groupBy { entry ->
            input.exerciseMetadataMap[entry.exerciseId]?.selector().orUnknown()
        }.mapValues { (_, entries) -> entries.sumOf { it.sets.size } }

    private fun List<AnalysisEntry>.tokenDistribution(
        input: AnalysisInputSnapshot,
        selector: AnalysisExerciseMetadata.() -> String
    ): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        forEach { entry ->
            val tokens = input.exerciseMetadataMap[entry.exerciseId]
                ?.selector()
                .tokensOrUnknown()
            tokens.forEach { token ->
                result[token] = (result[token] ?: 0) + entry.sets.size
            }
        }
        return result
    }

    private fun List<AnalysisEntry>.sportTransferDistribution(
        input: AnalysisInputSnapshot
    ): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        forEach { entry ->
            val metadata = input.exerciseMetadataMap[entry.exerciseId]
            val tokens = listOf(
                metadata?.sportTransferDirect,
                metadata?.sportTransferSupportive
            ).flatMap { it.tokensOrEmpty() }
                .ifEmpty { listOf(UNKNOWN) }
            tokens.forEach { token ->
                result[token] = (result[token] ?: 0) + entry.sets.size
            }
        }
        return result
    }

    private fun String?.orUnknown(): String =
        this?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN

    private fun String?.tokensOrUnknown(): List<String> =
        tokensOrEmpty().ifEmpty { listOf(UNKNOWN) }

    private fun String?.tokensOrEmpty(): List<String> =
        this.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private const val UNKNOWN = "UNKNOWN"
}

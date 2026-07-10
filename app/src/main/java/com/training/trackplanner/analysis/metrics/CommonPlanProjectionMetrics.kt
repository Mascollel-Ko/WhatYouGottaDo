package com.training.trackplanner.analysis.metrics

import com.training.trackplanner.analysis.core.AnalysisEntry
import com.training.trackplanner.analysis.core.AnalysisExerciseMetadata
import com.training.trackplanner.analysis.core.AnalysisInputSnapshot
import com.training.trackplanner.analysis.features.BodyweightEffectiveLoadCalculator
import java.time.LocalDate

data class CommonPlanProjectionMetricsResult(
    val plannedSessionsNext7: Int,
    val plannedTrainingDaysNext7: Int,
    val plannedRestDaysNext7: Int,
    val plannedLoadCandidateNext7: Double?,
    val plannedVolumeLoadCandidateNext7: Double?,
    val plannedAxialLoadCandidateNext7: Double?,
    val plannedMovementPatternDistributionNext7: Map<String, Int>,
    val plannedSportTransferDistributionNext7: Map<String, Int>,
    val planComparedToRecentLoadCandidate: Double?
)

object CommonPlanProjectionMetrics {
    fun calculate(
        input: AnalysisInputSnapshot,
        loadMetrics: CommonLoadMetricsResult
    ): CommonPlanProjectionMetricsResult {
        val plannedNext7 = input.plannedEntriesFromTomorrow
            .filter { entry -> input.windows.future7Days.contains(entry.date) }
        val plannedLoad = plannedNext7.sumOf { entry -> entry.loadCandidate(input) }
            .takeIf { plannedNext7.isNotEmpty() }
        val plannedVolumeLoad = plannedNext7.sumOf { entry -> entry.volumeLoad(input) }
            .takeIf { plannedNext7.isNotEmpty() }
        val plannedDays = plannedNext7.map(AnalysisEntry::date).toSet()

        return CommonPlanProjectionMetricsResult(
            plannedSessionsNext7 = plannedNext7.size,
            plannedTrainingDaysNext7 = plannedDays.size,
            plannedRestDaysNext7 = restDaysInFuture7(input, plannedDays),
            plannedLoadCandidateNext7 = plannedLoad,
            plannedVolumeLoadCandidateNext7 = plannedVolumeLoad,
            plannedAxialLoadCandidateNext7 = plannedAxialLoadCandidate(plannedNext7, input),
            plannedMovementPatternDistributionNext7 = plannedNext7.distribution(input) {
                movementPattern
            },
            plannedSportTransferDistributionNext7 = plannedNext7.sportTransferDistribution(input),
            planComparedToRecentLoadCandidate = if (
                plannedLoad != null && loadMetrics.weeklyLoad7 > 0.0
            ) {
                plannedLoad / loadMetrics.weeklyLoad7
            } else {
                null
            }
        )
    }

    private fun restDaysInFuture7(
        input: AnalysisInputSnapshot,
        plannedDays: Set<LocalDate>
    ): Int =
        input.windows.future7Days
            .dates()
            .count { date -> date !in plannedDays }

    private fun plannedAxialLoadCandidate(
        entries: List<AnalysisEntry>,
        input: AnalysisInputSnapshot
    ): Double? {
        if (entries.isEmpty()) return null
        val weightedSets = entries.sumOf { entry ->
            val axialLoadLevel = input.exerciseMetadataMap[entry.exerciseId]?.axialLoadLevel.orEmpty()
            when (axialLoadLevel) {
                "HIGH" -> entry.sets.size * 2.0
                "MODERATE" -> entry.sets.size * 1.0
                "LOW" -> entry.sets.size * 0.5
                else -> 0.0
            }
        }
        return weightedSets
    }

    private fun List<AnalysisEntry>.distribution(
        input: AnalysisInputSnapshot,
        selector: AnalysisExerciseMetadata.() -> String
    ): Map<String, Int> =
        groupBy { entry ->
            input.exerciseMetadataMap[entry.exerciseId]?.selector().orUnknown()
        }.mapValues { (_, entries) -> entries.sumOf { it.sets.size } }

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

    private fun AnalysisEntry.loadCandidate(input: AnalysisInputSnapshot): Double =
        sets.sumOf { set ->
            val volumeLoad = set.volumeLoad(input, this)
            val timeLoad = if (set.seconds > 0) set.seconds / 60.0 else 0.0
            volumeLoad + timeLoad
        }

    private fun AnalysisEntry.volumeLoad(input: AnalysisInputSnapshot): Double =
        sets.sumOf { set -> set.volumeLoad(input, this) }

    private fun com.training.trackplanner.analysis.core.AnalysisSet.volumeLoad(
        input: AnalysisInputSnapshot,
        entry: AnalysisEntry
    ): Double {
        val metadata = input.exerciseMetadataMap[entry.exerciseId]
        val bodyWeightKg = input.conditionRecordsUntilToday
            .filter { record -> record.date <= entry.date }
            .maxByOrNull { record -> record.date }
            ?.bodyWeightKg
        return metadata?.let { item ->
            BodyweightEffectiveLoadCalculator.effectiveVolumeLoadOrNull(
                stableKey = item.stableKey,
                displayName = entry.exerciseName,
                movementPattern = item.movementPattern,
                movementCategory = item.movementCategory,
                equipment = item.equipment.ifBlank { item.equipmentTags },
                category = item.category,
                reps = reps,
                weightKg = weightKg,
                bodyWeightKg = bodyWeightKg
            )
        } ?: if (reps > 0 && weightKg > 0.0) reps * weightKg else 0.0
    }

    private fun String?.orUnknown(): String =
        this?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN

    private fun String?.tokensOrEmpty(): List<String> =
        this.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private const val UNKNOWN = "UNKNOWN"
}

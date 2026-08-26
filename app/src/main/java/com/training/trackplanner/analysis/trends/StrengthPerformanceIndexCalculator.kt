package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.features.AnalysisFeatureExtractor
import com.training.trackplanner.analysis.features.AnalysisExerciseFeatures
import com.training.trackplanner.analysis.features.BodyweightEffectiveLoadCalculator
import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

data class CanonicalStrengthPosteriorWeeklyIntensity(
    val canonicalExerciseStableKeys: Set<String>,
    val valuesByWeek: Map<LocalDate, Map<String, Double>>
) {
    companion object {
        val EMPTY = CanonicalStrengthPosteriorWeeklyIntensity(emptySet(), emptyMap())
    }
}

class StrengthPerformanceIndexCalculator(
    private val runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
) {
    fun calculate(
        weeks: List<WeeklyTrainingData>,
        exerciseMap: Map<String, Exercise>,
        allDailyMetrics: List<DailyMetric>,
        canonicalPosteriorIntensity: CanonicalStrengthPosteriorWeeklyIntensity =
            CanonicalStrengthPosteriorWeeklyIntensity.EMPTY
    ): List<StrengthWeekIndex> {
        val rawIntensityByWeek = weeks.map { week ->
            val performedExerciseStableKeys = week.entries
                .filter { record -> record.sets.any { set -> set.confirmed } }
                .mapTo(mutableSetOf()) { record -> record.entry.exerciseStableKey }
            week.entries.maxEpleyIntensityByExercise(
                exerciseMap,
                canonicalPosteriorIntensity.canonicalExerciseStableKeys
            ) + canonicalPosteriorIntensity.valuesByWeek[week.weekStart]
                .orEmpty()
                .filterKeys { stableKey -> stableKey in performedExerciseStableKeys }
        }
        val rawVolumeByWeek = weeks.map { week ->
            week.entries.weeklyStrengthVolumeRaw(exerciseMap, allDailyMetrics)
        }
        val effectiveSetsByWeek = weeks.map { week ->
            week.entries.weeklyEffectiveSets(exerciseMap)
        }
        val efficiencyRawByWeek = weeks.map { week ->
            week.entries.efficiencyRaw(exerciseMap)
        }

        return weeks.mapIndexed { index, week ->
            val intensityScores = rawIntensityByWeek[index].mapNotNull { (exerciseStableKey, intensityRaw) ->
                val exercise = exerciseMap[exerciseStableKey] ?: return@mapNotNull null
                val features = AnalysisFeatureExtractor.fromExercise(exercise, runtimeMetadataCatalog.resolve(exercise))
                val history = rawIntensityByWeek.map { values -> values[exerciseStableKey] }
                val (baseline, confidence) = TrendMath.baselineFor(history, index)
                val score = if (baseline == null) 100.0 else TrendMath.higherIsBetterScore(intensityRaw, baseline)
                WeightedScore(
                    score = score,
                    weight = PerformanceTrendConstants.exerciseStrengthWeight(
                        features.movementPattern,
                        features.movementCategory,
                        features.compoundType
                    ),
                    confidence = confidence
                )
            }
            val intensityIndex = TrendMath.weightedMean(
                intensityScores.map { item -> item.score },
                intensityScores.map { item -> item.weight }
            )

            val (volumeBaseline, volumeConfidence) = TrendMath.baselineFor(rawVolumeByWeek, index)
            val volumeScore = TrendMath.higherIsBetterScore(rawVolumeByWeek[index], volumeBaseline)
            val effectiveSetDoubles = effectiveSetsByWeek.map { value -> value.toDouble() }
            val (effectiveSetBaseline, effectiveSetConfidence) =
                TrendMath.baselineFor(effectiveSetDoubles, index)
            val effectiveSetScore = TrendMath.higherIsBetterScore(
                effectiveSetsByWeek[index].toDouble(),
                effectiveSetBaseline
            )
            val volumeIndex = TrendMath.weightedMean(
                values = listOf(volumeScore, effectiveSetScore),
                weights = listOf(
                    PerformanceTrendConstants.STRENGTH_VOLUME_VOLUME_SCORE_WEIGHT,
                    PerformanceTrendConstants.STRENGTH_VOLUME_EFFECTIVE_SET_WEIGHT
                )
            )

            val (efficiencyBaseline, efficiencyConfidence) = TrendMath.baselineFor(efficiencyRawByWeek, index)
            val efficiencyScore = if (efficiencyRawByWeek[index] == null || efficiencyBaseline == null) {
                100.0
            } else {
                TrendMath.higherIsBetterScore(efficiencyRawByWeek[index], efficiencyBaseline)
            }
            val sameLoadEfficiency = sameLoadEfficiencyScore(weeks, exerciseMap, index)
            val efficiencyIndex = if (efficiencyRawByWeek[index] == null) {
                100.0
            } else {
                TrendMath.weightedMean(
                    values = listOf(efficiencyScore, sameLoadEfficiency),
                    weights = listOf(
                        PerformanceTrendConstants.STRENGTH_EFFICIENCY_SCORE_WEIGHT,
                        PerformanceTrendConstants.STRENGTH_EFFICIENCY_SAME_LOAD_WEIGHT
                    )
                )
            }

            val performanceIndex = TrendMath.clamp(
                TrendMath.weightedMean(
                    values = listOf(intensityIndex, volumeIndex, efficiencyIndex),
                    weights = listOf(
                        PerformanceTrendConstants.STRENGTH_PERFORMANCE_INTENSITY_WEIGHT,
                        PerformanceTrendConstants.STRENGTH_PERFORMANCE_VOLUME_WEIGHT,
                        PerformanceTrendConstants.STRENGTH_PERFORMANCE_EFFICIENCY_WEIGHT
                    )
                ),
                PerformanceTrendConstants.STANDARD_MIN,
                PerformanceTrendConstants.STANDARD_MAX
            )

            StrengthWeekIndex(
                weekStart = week.weekStart,
                intensityIndex = intensityIndex,
                volumeIndex = volumeIndex,
                efficiencyIndex = efficiencyIndex,
                performanceIndex = performanceIndex,
                confidence = TrendMath.combineConfidence(
                    listOf(
                        intensityScores.minOfOrNull { item -> item.confidence } ?: AnalysisConfidence.LOW,
                        volumeConfidence,
                        effectiveSetConfidence,
                        efficiencyConfidence
                    )
                ),
                rawVolume = rawVolumeByWeek[index] ?: 0.0,
                effectiveSets = effectiveSetsByWeek[index],
                exerciseScores = intensityScoresByExercise(rawIntensityByWeek, exerciseMap, index),
                patternVolumes = week.entries.patternVolumes(exerciseMap, allDailyMetrics)
            )
        }
    }

    private fun List<WorkoutEntryWithSets>.maxEpleyIntensityByExercise(
        exerciseMap: Map<String, Exercise>,
        posteriorOnlyExerciseStableKeys: Set<String>
    ): Map<String, Double> =
        mapNotNull { record ->
            if (record.entry.exerciseStableKey in posteriorOnlyExerciseStableKeys) return@mapNotNull null
            val exercise = exerciseMap[record.entry.exerciseStableKey] ?: return@mapNotNull null
            val features = AnalysisFeatureExtractor.fromRecord(
                exercise,
                record.entry,
                record.sets,
                runtimeMetadataCatalog.resolve(exercise)
            )
            if (!features.isStrengthProgressEligible()) return@mapNotNull null
            val maxE1rm = record.sets
                .filter { set -> set.confirmed && set.weightKg > 0.0 && set.reps in 1..12 }
                .maxOfOrNull { set -> set.weightKg * (1.0 + set.reps / 30.0) }
            maxE1rm?.let { value -> record.entry.exerciseStableKey to value }
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.maxOrNull() ?: 0.0 }

    private fun List<WorkoutEntryWithSets>.weeklyStrengthVolumeRaw(
        exerciseMap: Map<String, Exercise>,
        allDailyMetrics: List<DailyMetric>
    ): Double =
        sumOf { record ->
            val exercise = exerciseMap[record.entry.exerciseStableKey] ?: return@sumOf 0.0
            val features = AnalysisFeatureExtractor.fromRecord(
                exercise,
                record.entry,
                record.sets,
                runtimeMetadataCatalog.resolve(exercise)
            )
            if (!features.isStrengthLike()) return@sumOf 0.0
            val weight = PerformanceTrendConstants.volumeEligibilityWeight(
                features.movementPattern,
                features.movementCategory,
                features.compoundType
            )
            val bodyWeight = allDailyMetrics
                .filter { metric -> metric.date <= record.entry.date }
                .mapNotNull { metric -> metric.bodyWeightKg }
                .lastOrNull()
            record.sets
                .filter { set -> set.confirmed }
                .sumOf { set -> BodyweightEffectiveLoadCalculator.volumeLoad(exercise, set, bodyWeight) * weight }
        }

    private fun List<WorkoutEntryWithSets>.weeklyEffectiveSets(
        exerciseMap: Map<String, Exercise>
    ): Int =
        sumOf { record ->
            val exercise = exerciseMap[record.entry.exerciseStableKey] ?: return@sumOf 0
            val features = AnalysisFeatureExtractor.fromRecord(
                exercise,
                record.entry,
                record.sets,
                runtimeMetadataCatalog.resolve(exercise)
            )
            if (!features.isStrengthLike()) return@sumOf 0
            record.sets.count { set ->
                set.confirmed &&
                    (set.rpe == null || set.rpe >= 7.0) &&
                    !features.hasLowProgressPurpose()
            }
        }

    private fun List<WorkoutEntryWithSets>.efficiencyRaw(
        exerciseMap: Map<String, Exercise>
    ): Double? {
        val hardSets = flatMap { record ->
            val exercise = exerciseMap[record.entry.exerciseStableKey] ?: return@flatMap emptyList<SetWork>()
            val features = AnalysisFeatureExtractor.fromRecord(
                exercise,
                record.entry,
                record.sets,
                runtimeMetadataCatalog.resolve(exercise)
            )
            if (!features.isStrengthLike()) return@flatMap emptyList()
            record.sets
                .filter { set -> set.confirmed && (set.rpe ?: record.entry.rpe) != null }
                .map { set ->
                    SetWork(
                        work = set.weightKg * set.reps,
                        rpe = set.rpe ?: record.entry.rpe ?: 8.0
                    )
                }
        }
        if (hardSets.isEmpty()) return null
        val work = hardSets.sumOf { item -> item.work }
        val avgRpe = hardSets.map { item -> item.rpe }.average()
        return TrendMath.safeDivide(work, avgRpe, fallback = 100.0)
    }

    private fun sameLoadEfficiencyScore(
        weeks: List<WeeklyTrainingData>,
        exerciseMap: Map<String, Exercise>,
        index: Int
    ): Double? {
        if (index <= 0) return null
        val currentSets = weeks[index].entries.comparableRpeSets(exerciseMap)
        val previousSets = weeks.take(index).flatMap { week -> week.entries.comparableRpeSets(exerciseMap) }
        val deltas = currentSets.mapNotNull { current ->
            val previous = previousSets.lastOrNull { candidate ->
                candidate.exerciseStableKey == current.exerciseStableKey &&
                    kotlin.math.abs(candidate.weightKg - current.weightKg) <= 1.0 &&
                    kotlin.math.abs(candidate.reps - current.reps) <= 1
            }
            previous?.let { candidate -> candidate.rpe - current.rpe }
        }
        if (deltas.isEmpty()) return null
        return TrendMath.clamp(100.0 + 5.0 * deltas.average(), 80.0, 120.0)
    }

    private fun List<WorkoutEntryWithSets>.comparableRpeSets(
        exerciseMap: Map<String, Exercise>
    ): List<ComparableRpeSet> =
        flatMap { record ->
            val exercise = exerciseMap[record.entry.exerciseStableKey] ?: return@flatMap emptyList()
            val features = AnalysisFeatureExtractor.fromRecord(
                exercise,
                record.entry,
                record.sets,
                runtimeMetadataCatalog.resolve(exercise)
            )
            if (!features.isStrengthLike()) return@flatMap emptyList()
            record.sets.mapNotNull { set ->
                val rpe = set.rpe ?: record.entry.rpe ?: return@mapNotNull null
                if (!set.confirmed || set.weightKg <= 0.0 || set.reps <= 0) return@mapNotNull null
                ComparableRpeSet(
                    exerciseStableKey = record.entry.exerciseStableKey,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    rpe = rpe
                )
            }
        }

    private fun intensityScoresByExercise(
        rawIntensityByWeek: List<Map<String, Double>>,
        exerciseMap: Map<String, Exercise>,
        index: Int
    ): Map<String, Double> =
        rawIntensityByWeek[index].mapValues { (exerciseStableKey, value) ->
            val baseline = TrendMath.baselineFor(rawIntensityByWeek.map { week -> week[exerciseStableKey] }, index).first
            TrendMath.higherIsBetterScore(value, baseline)
        }.filterKeys { exerciseStableKey -> exerciseStableKey in exerciseMap.keys }

    private fun List<WorkoutEntryWithSets>.patternVolumes(
        exerciseMap: Map<String, Exercise>,
        allDailyMetrics: List<DailyMetric>
    ): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        forEach { record ->
            val exercise = exerciseMap[record.entry.exerciseStableKey] ?: return@forEach
            val features = AnalysisFeatureExtractor.fromRecord(
                exercise,
                record.entry,
                record.sets,
                runtimeMetadataCatalog.resolve(exercise)
            )
            if (!features.isStrengthLike()) return@forEach
            val bodyWeight = allDailyMetrics
                .filter { metric -> metric.date <= record.entry.date }
                .mapNotNull { metric -> metric.bodyWeightKg }
                .lastOrNull()
            val volume = record.sets
                .filter { set -> set.confirmed }
                .sumOf { set -> BodyweightEffectiveLoadCalculator.volumeLoad(exercise, set, bodyWeight) }
            val key = when {
                "SQUAT_PATTERN" in features.balanceContributionTags -> "SQUAT_PATTERN"
                "HINGE" in features.balanceContributionTags -> "HINGE"
                "UPPER_PUSH" in features.balanceContributionTags -> "UPPER_PUSH"
                "UPPER_PULL" in features.balanceContributionTags -> "UPPER_PULL"
                features.movementPattern == "LUNGE" -> "LUNGE"
                features.movementPattern == "CARRY" -> "CARRY"
                features.movementPattern == "ROTATION" -> "ROTATION"
                features.movementPattern == "ANTI_ROTATION" -> "ANTI_ROTATION"
                else -> features.movementPattern.ifBlank { "UNKNOWN" }
            }
            totals[key] = (totals[key] ?: 0.0) + volume
        }
        return totals
    }

    private fun AnalysisExerciseFeatures.isStrengthProgressEligible(): Boolean =
        estimated1RmEligible &&
            progressMetricType == "ESTIMATED_1RM" &&
            !hasLowProgressPurpose() &&
            "EXCLUDED_FROM_ANALYSIS" !in analysisEligibility

    private fun AnalysisExerciseFeatures.isStrengthLike(): Boolean =
        analysisEligibility.any { value ->
            value in setOf("STRENGTH_PROGRESS", "HYPERTROPHY_VOLUME")
        } && !hasLowProgressPurpose()

    private fun AnalysisExerciseFeatures.hasLowProgressPurpose(): Boolean =
        PerformanceTrendConstants.isLowProgressPurpose(movementPattern, movementCategory)

    private data class WeightedScore(
        val score: Double,
        val weight: Double,
        val confidence: AnalysisConfidence
    )

    private data class SetWork(
        val work: Double,
        val rpe: Double
    )

    private data class ComparableRpeSet(
        val exerciseStableKey: String,
        val weightKg: Double,
        val reps: Int,
        val rpe: Double
    )

}

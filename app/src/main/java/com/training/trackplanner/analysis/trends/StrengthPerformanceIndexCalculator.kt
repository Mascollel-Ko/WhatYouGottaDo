package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.features.AnalysisFeatureExtractor
import com.training.trackplanner.analysis.features.AnalysisExerciseFeatures
import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet

class StrengthPerformanceIndexCalculator(
    private val runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
) {
    fun calculate(
        weeks: List<WeeklyTrainingData>,
        exerciseMap: Map<Long, Exercise>,
        allDailyMetrics: List<DailyMetric>
    ): List<StrengthWeekIndex> {
        val rawIntensityByWeek = weeks.map { week ->
            week.entries.maxIntensityByExercise(exerciseMap)
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
            val intensityScores = rawIntensityByWeek[index].mapNotNull { (exerciseId, intensityRaw) ->
                val exercise = exerciseMap[exerciseId] ?: return@mapNotNull null
                val features = AnalysisFeatureExtractor.fromExercise(exercise, runtimeMetadataCatalog.resolve(exercise))
                val history = rawIntensityByWeek.map { values -> values[exerciseId] }
                val (baseline, confidence) = TrendMath.baselineFor(history, index)
                val score = if (baseline == null) 100.0 else TrendMath.higherIsBetterScore(intensityRaw, baseline)
                WeightedScore(
                    score = score,
                    weight = PerformanceTrendConstants.exerciseStrengthWeight(
                        features.trainingRole,
                        features.movementCategory
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

    private fun List<WorkoutEntryWithSets>.maxIntensityByExercise(
        exerciseMap: Map<Long, Exercise>
    ): Map<Long, Double> =
        mapNotNull { record ->
            val exercise = exerciseMap[record.entry.exerciseId] ?: return@mapNotNull null
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
            maxE1rm?.let { value -> record.entry.exerciseId to value }
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.maxOrNull() ?: 0.0 }

    private fun List<WorkoutEntryWithSets>.weeklyStrengthVolumeRaw(
        exerciseMap: Map<Long, Exercise>,
        allDailyMetrics: List<DailyMetric>
    ): Double =
        sumOf { record ->
            val exercise = exerciseMap[record.entry.exerciseId] ?: return@sumOf 0.0
            val features = AnalysisFeatureExtractor.fromRecord(
                exercise,
                record.entry,
                record.sets,
                runtimeMetadataCatalog.resolve(exercise)
            )
            if (!features.isStrengthLike()) return@sumOf 0.0
            val weight = PerformanceTrendConstants.volumeEligibilityWeight(
                features.trainingRole,
                features.movementCategory
            )
            val bodyWeight = allDailyMetrics
                .filter { metric -> metric.date <= record.entry.date }
                .mapNotNull { metric -> metric.bodyWeightKg }
                .lastOrNull()
            record.sets
                .filter { set -> set.confirmed }
                .sumOf { set -> set.setVolumeLoad(bodyWeight) * weight }
        }

    private fun List<WorkoutEntryWithSets>.weeklyEffectiveSets(
        exerciseMap: Map<Long, Exercise>
    ): Int =
        sumOf { record ->
            val exercise = exerciseMap[record.entry.exerciseId] ?: return@sumOf 0
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
                    features.trainingRole !in excludedStrengthRoles
            }
        }

    private fun List<WorkoutEntryWithSets>.efficiencyRaw(
        exerciseMap: Map<Long, Exercise>
    ): Double? {
        val hardSets = flatMap { record ->
            val exercise = exerciseMap[record.entry.exerciseId] ?: return@flatMap emptyList<SetWork>()
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
        exerciseMap: Map<Long, Exercise>,
        index: Int
    ): Double? {
        if (index <= 0) return null
        val currentSets = weeks[index].entries.comparableRpeSets(exerciseMap)
        val previousSets = weeks.take(index).flatMap { week -> week.entries.comparableRpeSets(exerciseMap) }
        val deltas = currentSets.mapNotNull { current ->
            val previous = previousSets.lastOrNull { candidate ->
                candidate.exerciseId == current.exerciseId &&
                    kotlin.math.abs(candidate.weightKg - current.weightKg) <= 1.0 &&
                    kotlin.math.abs(candidate.reps - current.reps) <= 1
            }
            previous?.let { candidate -> candidate.rpe - current.rpe }
        }
        if (deltas.isEmpty()) return null
        return TrendMath.clamp(100.0 + 5.0 * deltas.average(), 80.0, 120.0)
    }

    private fun List<WorkoutEntryWithSets>.comparableRpeSets(
        exerciseMap: Map<Long, Exercise>
    ): List<ComparableRpeSet> =
        flatMap { record ->
            val exercise = exerciseMap[record.entry.exerciseId] ?: return@flatMap emptyList()
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
                    exerciseId = record.entry.exerciseId,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    rpe = rpe
                )
            }
        }

    private fun intensityScoresByExercise(
        rawIntensityByWeek: List<Map<Long, Double>>,
        exerciseMap: Map<Long, Exercise>,
        index: Int
    ): Map<Long, Double> =
        rawIntensityByWeek[index].mapValues { (exerciseId, value) ->
            val baseline = TrendMath.baselineFor(rawIntensityByWeek.map { week -> week[exerciseId] }, index).first
            TrendMath.higherIsBetterScore(value, baseline)
        }.filterKeys { exerciseId -> exerciseId in exerciseMap.keys }

    private fun List<WorkoutEntryWithSets>.patternVolumes(
        exerciseMap: Map<Long, Exercise>,
        allDailyMetrics: List<DailyMetric>
    ): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        forEach { record ->
            val exercise = exerciseMap[record.entry.exerciseId] ?: return@forEach
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
            val volume = record.sets.filter { set -> set.confirmed }.sumOf { set -> set.setVolumeLoad(bodyWeight) }
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

    private fun WorkoutSet.setVolumeLoad(bodyWeightKg: Double?): Double {
        if (weightKg > 0.0 && reps > 0) return weightKg * reps
        if (reps > 0) {
            val proxy = bodyWeightKg?.let { weight -> weight * PerformanceTrendConstants.BODYWEIGHT_LOAD_FACTOR }
                ?: PerformanceTrendConstants.DEFAULT_BODYWEIGHT_PROXY
            return proxy * reps
        }
        return 0.0
    }

    private fun AnalysisExerciseFeatures.isStrengthProgressEligible(): Boolean =
        estimated1RmEligible &&
            progressMetricType == "ESTIMATED_1RM" &&
            trainingRole !in excludedStrengthRoles &&
            "EXCLUDED_FROM_ANALYSIS" !in analysisEligibility

    private fun AnalysisExerciseFeatures.isStrengthLike(): Boolean =
        analysisEligibility.any { value ->
            value in setOf("STRENGTH_PROGRESS", "HYPERTROPHY_VOLUME")
        } && trainingRole !in excludedStrengthRoles

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
        val exerciseId: Long,
        val weightKg: Double,
        val reps: Int,
        val rpe: Double
    )

    private companion object {
        val excludedStrengthRoles = setOf("PREHAB", "MOBILITY", "RECOVERY")
    }
}

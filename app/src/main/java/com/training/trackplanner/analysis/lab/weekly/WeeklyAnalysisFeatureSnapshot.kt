package com.training.trackplanner.analysis.lab.weekly

import com.training.trackplanner.analysis.features.BodyweightEffectiveLoadCalculator
import com.training.trackplanner.analysis.features.DurationHoldLoadCalculator
import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.pipeline.AnalysisSourceKey
import com.training.trackplanner.analysis.lab.pipeline.strictFingerprint
import com.training.trackplanner.analysis.lab.pipeline.toAnalysisFeatureKey
import com.training.trackplanner.analysis.lab.pipeline.toAnalysisSourceKey
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal enum class WeeklyCellState {
    OBSERVED,
    STRUCTURAL_ZERO,
    NOT_APPLICABLE,
    MISSING
}

internal enum class AnalysisWeekState {
    CLOSED,
    OPEN
}

internal enum class AnalysisFeatureFamily {
    TRAINING_FLOW,
    ANATOMY_LOAD,
    EXPOSURE_INDICATOR,
    CONDITIONAL_RPE,
    RECOVERY_CHECK_IN,
    PERFORMANCE,
    PERSISTENT_PERFORMANCE,
    CUMULATIVE_OR_UNKNOWN
}

internal data class WeeklyFeatureCell(
    val featureKey: AnalysisFeatureKey,
    val weekStart: LocalDate,
    val state: WeeklyCellState,
    val value: Double?,
    val provenance: String
) {
    init {
        when (state) {
            WeeklyCellState.OBSERVED -> require(value != null && value.isFinite())
            WeeklyCellState.STRUCTURAL_ZERO -> require(value == 0.0)
            WeeklyCellState.NOT_APPLICABLE,
            WeeklyCellState.MISSING -> require(value == null)
        }
        require(provenance.isNotBlank())
    }
}

internal data class AnalysisFeatureDescriptor(
    val featureKey: AnalysisFeatureKey,
    val sourceKey: AnalysisSourceKey,
    val displayName: String,
    val family: AnalysisFeatureFamily
)

internal data class AnalysisFeatureAvailability(
    val activeWeeks: Int,
    val observedWeeks: Int,
    val structuralZeroWeeks: Int,
    val notApplicableWeeks: Int,
    val missingWeeks: Int,
    val firstAvailableWeek: LocalDate?,
    val lastAvailableWeek: LocalDate?,
    val distinctFiniteValues: Int
) {
    val hasData: Boolean
        get() = activeWeeks > 0

    val hasVariation: Boolean
        get() = distinctFiniteValues >= 2
}

internal data class WeeklyExerciseAggregate(
    val weekStart: LocalDate,
    val exerciseStableKey: String,
    val on: Boolean,
    val baseDose: Double,
    val confirmedSetCount: Int,
    val exposureDayCount: Int,
    val totalReps: Int,
    val durationSeconds: Int,
    val rpeEligible: Boolean,
    val rpeObserved: Boolean,
    val meanRpe: Double?,
    val averageEffectiveLoadKg: Double?
)

internal class WeeklyAnalysisFeatureSnapshot private constructor(
    weeks: List<LocalDate>,
    weekStateByStart: Map<LocalDate, AnalysisWeekState>,
    descriptors: Map<AnalysisFeatureKey, AnalysisFeatureDescriptor>,
    cellsByFeature: Map<AnalysisFeatureKey, List<WeeklyFeatureCell>>,
    exerciseAggregates: List<WeeklyExerciseAggregate>,
    val sourceRevision: Long,
    val metadataRevision: String,
    calculatorVersionSet: Set<String>,
    val fingerprint: String
) {
    val weeks: List<LocalDate> = weeks.toList()
    val weekStateByStart: Map<LocalDate, AnalysisWeekState> = weekStateByStart.toMap()
    val descriptors: Map<AnalysisFeatureKey, AnalysisFeatureDescriptor> = descriptors.toMap()
    val cellsByFeature: Map<AnalysisFeatureKey, List<WeeklyFeatureCell>> = cellsByFeature.mapValues { it.value.toList() }
    val exerciseAggregates: List<WeeklyExerciseAggregate> = exerciseAggregates.toList()
    val calculatorVersionSet: Set<String> = calculatorVersionSet.toSet()
    val closedWeeks: List<LocalDate> = weeks.filter { weekStateByStart[it] == AnalysisWeekState.CLOSED }
    val featureAvailabilityIndex: Map<AnalysisFeatureKey, AnalysisFeatureAvailability> =
        cellsByFeature.mapValues { (_, cells) ->
            val closedCells = cells.filter { weekStateByStart[it.weekStart] == AnalysisWeekState.CLOSED }
            val available = closedCells.filter { it.state in setOf(WeeklyCellState.OBSERVED, WeeklyCellState.STRUCTURAL_ZERO) }
            AnalysisFeatureAvailability(
                activeWeeks = available.size,
                observedWeeks = closedCells.count { it.state == WeeklyCellState.OBSERVED },
                structuralZeroWeeks = closedCells.count { it.state == WeeklyCellState.STRUCTURAL_ZERO },
                notApplicableWeeks = closedCells.count { it.state == WeeklyCellState.NOT_APPLICABLE },
                missingWeeks = closedCells.count { it.state == WeeklyCellState.MISSING },
                firstAvailableWeek = available.firstOrNull()?.weekStart,
                lastAvailableWeek = available.lastOrNull()?.weekStart,
                distinctFiniteValues = available.mapNotNull(WeeklyFeatureCell::value).map(Double::toRawBits).distinct().size
            )
        }

    init {
        require(weeks.isNotEmpty() && weeks == weeks.distinct().sorted())
        require(weekStateByStart.keys == weeks.toSet())
        require(descriptors.keys == cellsByFeature.keys)
        require(cellsByFeature.values.all { cells -> cells.map(WeeklyFeatureCell::weekStart) == weeks })
        require(cellsByFeature.all { (key, cells) -> cells.all { it.featureKey == key } })
    }

    fun cell(featureKey: AnalysisFeatureKey, weekStart: LocalDate): WeeklyFeatureCell? =
        cellsByFeature[featureKey]?.firstOrNull { it.weekStart == weekStart }

    companion object {
        fun createValidated(
            weeks: List<LocalDate>,
            weekStateByStart: Map<LocalDate, AnalysisWeekState>,
            descriptors: Map<AnalysisFeatureKey, AnalysisFeatureDescriptor>,
            cellsByFeature: Map<AnalysisFeatureKey, List<WeeklyFeatureCell>>,
            exerciseAggregates: List<WeeklyExerciseAggregate>,
            sourceRevision: Long,
            metadataRevision: String,
            calculatorVersionSet: Set<String>
        ): WeeklyAnalysisFeatureSnapshot {
            require(sourceRevision >= 0L)
            require(metadataRevision.isNotBlank())
            require(calculatorVersionSet.isNotEmpty() && calculatorVersionSet.none(String::isBlank))
            val fingerprint = strictFingerprint(
                listOf(
                    sourceRevision,
                    metadataRevision,
                    WEEKLY_SNAPSHOT_VERSION,
                    calculatorVersionSet.sorted().joinToString(",")
                ) +
                    weeks +
                    weekStateByStart.toSortedMap().map { "${it.key}:${it.value}" } +
                    descriptors.toSortedMap().values.map { "${it.featureKey}:${it.sourceKey}:${it.family}" } +
                    cellsByFeature.toSortedMap().flatMap { (_, cells) -> cells.map { "${it.weekStart}:${it.state}:${it.value}:${it.provenance}" } }
            )
            return WeeklyAnalysisFeatureSnapshot(
                weeks,
                weekStateByStart,
                descriptors,
                cellsByFeature,
                exerciseAggregates,
                sourceRevision,
                metadataRevision,
                calculatorVersionSet,
                fingerprint
            )
        }
    }
}

internal object WeeklyAnalysisFeatureSnapshotBuilder {
    fun build(
        today: LocalDate,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>,
        entriesWithSets: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        dailyMetrics: List<DailyMetric>,
        initialProfile: InitialUserProfile?,
        muscleRelations: List<CanonicalMetadataRelation>,
        sourceRevision: Long,
        metadataRevision: String = "test-metadata-revision",
        calculatorVersionSet: Set<String> = DEFAULT_CALCULATOR_VERSION_SET
    ): WeeklyAnalysisFeatureSnapshot {
        val currentWeek = weekStart(today)
        val datedEntries = entriesWithSets.mapNotNull { record ->
            runCatching { LocalDate.parse(record.entry.date) }.getOrNull()
                ?.takeIf { !it.isAfter(today) }
                ?.let { it to record }
        }
        val earliest = buildList {
            addAll(metricSeries.values.flatten().map(TrendDataPoint::weekStart))
            addAll(datedEntries.map { it.first })
            addAll(dailyMetrics.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.filterNot { it.isAfter(today) })
        }.minOrNull()?.let(::weekStart) ?: currentWeek
        val weeks = generateSequence(earliest) { previous ->
            previous.plusWeeks(1).takeIf { !it.isAfter(currentWeek) }
        }.toList()
        val weekStates = weeks.associateWith { if (it == currentWeek) AnalysisWeekState.OPEN else AnalysisWeekState.CLOSED }
        val descriptors = linkedMapOf<AnalysisFeatureKey, AnalysisFeatureDescriptor>()
        val cells = linkedMapOf<AnalysisFeatureKey, List<WeeklyFeatureCell>>()

        metricSeries.toSortedMap(compareBy(TrendMetricId::name)).forEach { (metric, points) ->
            val featureKey = metric.toAnalysisFeatureKey()
            descriptors[featureKey] = AnalysisFeatureDescriptor(
                featureKey,
                metric.toAnalysisSourceKey(),
                metric.name,
                metricFamily(metric)
            )
            val byWeek = points.associateBy { weekStart(it.weekStart) }
            cells[featureKey] = weeks.map { week ->
                val value = byWeek[week]?.value
                when {
                    value != null && value.isFinite() -> WeeklyFeatureCell(featureKey, week, WeeklyCellState.OBSERVED, value, "canonical metric series")
                    metric.zeroWhenAbsent() -> WeeklyFeatureCell(featureKey, week, WeeklyCellState.STRUCTURAL_ZERO, 0.0, "reviewed no-exposure week")
                    else -> WeeklyFeatureCell(featureKey, week, WeeklyCellState.MISSING, null, "no authoritative observation")
                }
            }
        }

        val exerciseByKey = exercises.associateBy(Exercise::stableKey)
        val aggregates = exerciseAggregates(
            weeks,
            datedEntries,
            exerciseByKey,
            dailyMetrics,
            initialProfile
        )
        aggregates.groupBy(WeeklyExerciseAggregate::exerciseStableKey).toSortedMap().forEach { (stableKey, rows) ->
            val sourceKey = AnalysisSourceKey.exercise(stableKey)
            val exerciseName = exerciseByKey[stableKey]?.name?.takeIf(String::isNotBlank) ?: stableKey
            addExerciseFeature(descriptors, cells, weeks, rows, sourceKey, stableKey, exerciseName, "on", "ON", AnalysisFeatureFamily.EXPOSURE_INDICATOR) { if (it.on) 1.0 else 0.0 }
            addExerciseFeature(descriptors, cells, weeks, rows, sourceKey, stableKey, exerciseName, "base_dose", "base dose", AnalysisFeatureFamily.TRAINING_FLOW, WeeklyExerciseAggregate::baseDose)
            addExerciseFeature(descriptors, cells, weeks, rows, sourceKey, stableKey, exerciseName, "confirmed_sets", "confirmed sets", AnalysisFeatureFamily.TRAINING_FLOW) { it.confirmedSetCount.toDouble() }
            addExerciseFeature(descriptors, cells, weeks, rows, sourceKey, stableKey, exerciseName, "exposure_days", "exposure days", AnalysisFeatureFamily.TRAINING_FLOW) { it.exposureDayCount.toDouble() }
            addExerciseFeature(descriptors, cells, weeks, rows, sourceKey, stableKey, exerciseName, "total_reps", "total reps", AnalysisFeatureFamily.TRAINING_FLOW) { it.totalReps.toDouble() }
            addExerciseFeature(descriptors, cells, weeks, rows, sourceKey, stableKey, exerciseName, "duration_seconds", "duration seconds", AnalysisFeatureFamily.TRAINING_FLOW) { it.durationSeconds.toDouble() }
            addConditionalRpeFeature(descriptors, cells, weeks, rows, sourceKey, stableKey, exerciseName)
            addAverageLoadFeature(descriptors, cells, weeks, rows, sourceKey, stableKey, exerciseName)
        }

        addAnatomyFeatures(descriptors, cells, weeks, aggregates, muscleRelations)
        return WeeklyAnalysisFeatureSnapshot.createValidated(
            weeks,
            weekStates,
            descriptors,
            cells,
            aggregates,
            sourceRevision,
            metadataRevision,
            calculatorVersionSet
        )
    }

    private fun exerciseAggregates(
        weeks: List<LocalDate>,
        datedEntries: List<Pair<LocalDate, WorkoutEntryWithSets>>,
        exerciseByKey: Map<String, Exercise>,
        dailyMetrics: List<DailyMetric>,
        initialProfile: InitialUserProfile?
    ): List<WeeklyExerciseAggregate> {
        val activeKeys = datedEntries.map { it.second.entry.exerciseStableKey }.filter(String::isNotBlank).distinct().sorted()
        return activeKeys.flatMap { stableKey ->
            weeks.map { week ->
                val records = datedEntries.filter { (date, record) ->
                    record.entry.exerciseStableKey == stableKey && weekStart(date) == week
                }
                val confirmed = records.flatMap { (date, record) ->
                    record.sets.filter { it.confirmed }.map { Triple(date, record, it) }
                }
                val exercise = exerciseByKey[stableKey]
                val rpeValues = confirmed.mapNotNull { (_, record, set) -> set.rpe ?: record.entry.rpe }
                val repSets = confirmed.filter { it.third.reps > 0 }
                val totalReps = repSets.sumOf { it.third.reps }
                val effectiveVolume = if (exercise == null) 0.0 else repSets.sumOf { (date, _, set) ->
                    BodyweightEffectiveLoadCalculator.volumeLoad(
                        exercise,
                        set,
                        BodyweightEffectiveLoadCalculator.bodyWeightFor(date.toString(), dailyMetrics, initialProfile)
                    )
                }
                val durationDose = if (exercise == null) 0.0 else confirmed.sumOf { (_, _, set) ->
                    val policy = DurationHoldLoadCalculator.policyFor(exercise.stableKey)
                    if (set.seconds > 0 && policy != null) set.seconds * policy.coefficient else 0.0
                }
                WeeklyExerciseAggregate(
                    weekStart = week,
                    exerciseStableKey = stableKey,
                    on = confirmed.isNotEmpty(),
                    baseDose = effectiveVolume + durationDose,
                    confirmedSetCount = confirmed.size,
                    exposureDayCount = confirmed.map { it.first }.distinct().size,
                    totalReps = totalReps,
                    durationSeconds = confirmed.sumOf { it.third.seconds.coerceAtLeast(0) },
                    rpeEligible = confirmed.isNotEmpty(),
                    rpeObserved = rpeValues.isNotEmpty(),
                    meanRpe = rpeValues.takeIf { it.isNotEmpty() }?.average(),
                    averageEffectiveLoadKg = if (totalReps > 0 && effectiveVolume > 0.0) effectiveVolume / totalReps else null
                )
            }
        }
    }

    private fun addExerciseFeature(
        descriptors: MutableMap<AnalysisFeatureKey, AnalysisFeatureDescriptor>,
        cells: MutableMap<AnalysisFeatureKey, List<WeeklyFeatureCell>>,
        weeks: List<LocalDate>,
        rows: List<WeeklyExerciseAggregate>,
        sourceKey: AnalysisSourceKey,
        stableKey: String,
        exerciseName: String,
        feature: String,
        label: String,
        family: AnalysisFeatureFamily,
        value: (WeeklyExerciseAggregate) -> Double
    ) {
        val key = AnalysisFeatureKey.exercise(stableKey, feature)
        descriptors[key] = AnalysisFeatureDescriptor(key, sourceKey, "$exerciseName $label", family)
        val byWeek = rows.associateBy(WeeklyExerciseAggregate::weekStart)
        cells[key] = weeks.map { week ->
            WeeklyFeatureCell(key, week, WeeklyCellState.OBSERVED, value(byWeek.getValue(week)), "confirmed exercise aggregate")
        }
    }

    private fun addConditionalRpeFeature(
        descriptors: MutableMap<AnalysisFeatureKey, AnalysisFeatureDescriptor>,
        cells: MutableMap<AnalysisFeatureKey, List<WeeklyFeatureCell>>,
        weeks: List<LocalDate>,
        rows: List<WeeklyExerciseAggregate>,
        sourceKey: AnalysisSourceKey,
        stableKey: String,
        exerciseName: String
    ) {
        val key = AnalysisFeatureKey.exercise(stableKey, "mean_rpe")
        descriptors[key] = AnalysisFeatureDescriptor(key, sourceKey, "$exerciseName mean RPE", AnalysisFeatureFamily.CONDITIONAL_RPE)
        val byWeek = rows.associateBy(WeeklyExerciseAggregate::weekStart)
        cells[key] = weeks.map { week ->
            val row = byWeek.getValue(week)
            when {
                !row.rpeEligible -> WeeklyFeatureCell(key, week, WeeklyCellState.NOT_APPLICABLE, null, "no exercise exposure")
                row.rpeObserved -> WeeklyFeatureCell(key, week, WeeklyCellState.OBSERVED, row.meanRpe, "set RPE with entry fallback")
                else -> WeeklyFeatureCell(key, week, WeeklyCellState.MISSING, null, "exposure exists but RPE is missing")
            }
        }
    }

    private fun addAverageLoadFeature(
        descriptors: MutableMap<AnalysisFeatureKey, AnalysisFeatureDescriptor>,
        cells: MutableMap<AnalysisFeatureKey, List<WeeklyFeatureCell>>,
        weeks: List<LocalDate>,
        rows: List<WeeklyExerciseAggregate>,
        sourceKey: AnalysisSourceKey,
        stableKey: String,
        exerciseName: String
    ) {
        val key = AnalysisFeatureKey.exercise(stableKey, "average_effective_load")
        descriptors[key] = AnalysisFeatureDescriptor(key, sourceKey, "$exerciseName average effective load", AnalysisFeatureFamily.TRAINING_FLOW)
        val byWeek = rows.associateBy(WeeklyExerciseAggregate::weekStart)
        cells[key] = weeks.map { week ->
            val value = byWeek.getValue(week).averageEffectiveLoadKg
            if (value == null) WeeklyFeatureCell(key, week, WeeklyCellState.NOT_APPLICABLE, null, "no eligible rep-based exposure")
            else WeeklyFeatureCell(key, week, WeeklyCellState.OBSERVED, value, "canonical effective load per repetition")
        }
    }

    private fun addAnatomyFeatures(
        descriptors: MutableMap<AnalysisFeatureKey, AnalysisFeatureDescriptor>,
        cells: MutableMap<AnalysisFeatureKey, List<WeeklyFeatureCell>>,
        weeks: List<LocalDate>,
        aggregates: List<WeeklyExerciseAggregate>,
        muscleRelations: List<CanonicalMetadataRelation>
    ) {
        val doseByExerciseAndWeek = aggregates.associateBy({ it.exerciseStableKey to it.weekStart }, WeeklyExerciseAggregate::baseDose)
        muscleRelations.filter { it.coefficient != null }.groupBy(CanonicalMetadataRelation::relationValue)
            .toSortedMap().forEach { (anatomyKey, relations) ->
                val key = AnalysisFeatureKey.anatomy(anatomyKey)
                descriptors[key] = AnalysisFeatureDescriptor(
                    key,
                    AnalysisSourceKey.anatomy(anatomyKey),
                    anatomyKey,
                    AnalysisFeatureFamily.ANATOMY_LOAD
                )
                cells[key] = weeks.map { week ->
                    val dose = relations.sumOf { relation ->
                        doseByExerciseAndWeek[relation.exerciseStableKey to week].orZero() * requireNotNull(relation.coefficient)
                    }
                    WeeklyFeatureCell(key, week, WeeklyCellState.OBSERVED, dose, "canonical muscle relation weighted dose")
                }
            }
    }

    private fun metricFamily(metric: TrendMetricId): AnalysisFeatureFamily = when (metric) {
        TrendMetricId.BENCH_PRESS_E1RM,
        TrendMetricId.SQUAT_E1RM,
        TrendMetricId.DEADLIFT_E1RM -> AnalysisFeatureFamily.PERSISTENT_PERFORMANCE
        TrendMetricId.SMASH_SPEED_TOP3_AVG,
        TrendMetricId.SMASH_SPEED_BEST,
        TrendMetricId.SMASH_SPEED_AVG,
        TrendMetricId.STRENGTH_PERFORMANCE -> AnalysisFeatureFamily.PERFORMANCE
        TrendMetricId.SLEEP_HOURS,
        TrendMetricId.OVERALL_FATIGUE_CHECKIN,
        TrendMetricId.LOWER_BODY_FATIGUE_CHECKIN,
        TrendMetricId.JOINT_TENDON_DISCOMFORT_CHECKIN,
        TrendMetricId.FOCUS_MOTIVATION_CHECKIN,
        TrendMetricId.RECOVERY_CHECKIN_COMPOSITE -> AnalysisFeatureFamily.RECOVERY_CHECK_IN
        else -> if (metric.name.startsWith("MUSCLE_")) AnalysisFeatureFamily.ANATOMY_LOAD else AnalysisFeatureFamily.TRAINING_FLOW
    }

    private fun TrendMetricId.zeroWhenAbsent(): Boolean =
        this in ZERO_WHEN_ABSENT || name.startsWith("MUSCLE_") || name.startsWith("CORE_")

    private fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun Double?.orZero(): Double = this ?: 0.0

    private val ZERO_WHEN_ABSENT = setOf(
        TrendMetricId.STRENGTH_VOLUME,
        TrendMetricId.BADMINTON_PRACTICE_LOAD,
        TrendMetricId.SMASH_ATTEMPT_COUNT,
        TrendMetricId.STRENGTH_VOLUME_ONLY
    )

    val DEFAULT_CALCULATOR_VERSION_SET = setOf(
        "bodyweight-effective-load-v1",
        "duration-hold-load-v1",
        "canonical-muscle-relation-v1"
    )
}

internal const val WEEKLY_SNAPSHOT_VERSION = "strict-weekly-feature-snapshot-v1"

package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.features.BodyweightEffectiveLoadCalculator
import com.training.trackplanner.analysis.features.DurationHoldLoadCalculator
import com.training.trackplanner.analysis.features.DurationHoldPolicy
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object StrengthAndMuscleMetricSeriesBuilder {
    fun build(
        entriesWithSets: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
        dailyMetrics: List<DailyMetric> = emptyList()
    ): Map<TrendMetricId, List<TrendDataPoint>> {
        val exercisesById = exercises.associateBy { it.id }
        val benchE1rmByDate = mutableMapOf<LocalDate, Double>()
        val squatE1rmByDate = mutableMapOf<LocalDate, Double>()
        val deadliftE1rmByDate = mutableMapOf<LocalDate, Double>()
        val dailyLoads = MuscleBucket.values().associateWith { mutableMapOf<LocalDate, Double>() }
        val datesWithConfirmedSets = mutableSetOf<LocalDate>()

        entriesWithSets.forEach { record ->
            val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull() ?: return@forEach
            val exercise = exercisesById[record.entry.exerciseId]
            val runtimeMetadata = exercise?.let(runtimeMetadataCatalog::resolve)
            val confirmedSets = record.sets.filter { set -> set.confirmed }
            if (confirmedSets.isEmpty()) return@forEach
            datesWithConfirmedSets += date

            confirmedSets.forEach { set ->
                e1rmFor(set)?.let { e1rm ->
                    if (MuscleLoadInputBuilder.isMainBenchPress(exercise, record.entry)) {
                        benchE1rmByDate.merge(date, e1rm, ::maxOf)
                    }
                    if (MuscleLoadInputBuilder.isMainSquat(exercise, record.entry)) {
                        squatE1rmByDate.merge(date, e1rm, ::maxOf)
                    }
                    if (MuscleLoadInputBuilder.isMainDeadlift(exercise, record.entry)) {
                        deadliftE1rmByDate.merge(date, e1rm, ::maxOf)
                    }
                }

                if ((set.reps > 0 && set.weightKg >= 0.0) || set.seconds > 0) {
                    val bodyWeight = BodyweightEffectiveLoadCalculator.bodyWeightFor(
                        date = record.entry.date,
                        dailyMetrics = dailyMetrics,
                        initialProfile = null
                    )
                    val rpe = set.rpe ?: record.entry.rpe
                    val durationHoldLoad = exercise?.let { item ->
                        DurationHoldLoadCalculator.holdLoad(item, set, rpe)
                    }
                    val setLoad = exercise?.let { item ->
                        durationHoldLoad ?: BodyweightEffectiveLoadCalculator.volumeLoad(item, set, bodyWeight)
                    } ?: set.weightKg * set.reps
                    val load = if (durationHoldLoad != null) setLoad else setLoad * rpeWeight(rpe)
                    if (load > 0.0) {
                        val contributions = durationHoldContributions(exercise, record.entry)
                            ?: MuscleLoadInputBuilder.contributions(exercise, record.entry, runtimeMetadata)
                        contributions.forEach { (bucket, weight) ->
                            dailyLoads.getValue(bucket).merge(date, load * weight, Double::plus)
                        }
                    }
                }
            }
        }

        val result = mutableMapOf(
            TrendMetricId.BENCH_PRESS_E1RM to weeklyBestSeries(benchE1rmByDate),
            TrendMetricId.SQUAT_E1RM to weeklyBestSeries(squatE1rmByDate),
            TrendMetricId.DEADLIFT_E1RM to weeklyBestSeries(deadliftE1rmByDate)
        )
        if (datesWithConfirmedSets.isEmpty()) return result

        val start = datesWithConfirmedSets.minOrNull() ?: return result
        val end = datesWithConfirmedSets.maxOrNull() ?: return result
        val dates = generateSequence(start) { date -> date.plusDays(1) }
            .takeWhile { date -> !date.isAfter(end) }
            .toList()

        MuscleBucket.values().forEach { bucket ->
            val daily = dailyLoads.getValue(bucket)
            if (daily.values.none { value -> value > 0.0 }) return@forEach
            result[bucket.dailyMetric] = weeklySumSeries(daily)
            result[bucket.threeDayMetric] = weeklySumSeries(rollingMap(dates, daily, 3), daily.keys)
            result[bucket.sevenDayMetric] = weeklySumSeries(rollingMap(dates, daily, 7), daily.keys)
        }
        return result
    }

    private fun durationHoldContributions(exercise: Exercise?, entry: WorkoutEntry): Map<MuscleBucket, Double>? {
        val policy = DurationHoldLoadCalculator.policyFor(
            stableKey = exercise?.stableKey.orEmpty(),
            displayName = exercise?.name ?: entry.exerciseName,
            movementPattern = exercise?.movementPattern.orEmpty(),
            movementCategory = exercise?.movementCategory.orEmpty(),
            equipment = exercise?.equipment?.ifBlank { exercise.equipmentTags }.orEmpty(),
            mode = exercise?.mode.orEmpty(),
            category = exercise?.category ?: entry.category
        )
        return when (policy) {
            DurationHoldPolicy.PLANK -> mapOf(
                MuscleBucket.ANTERIOR_CORE to 0.65,
                MuscleBucket.GLUTES to 0.15,
                MuscleBucket.SHOULDERS to 0.10,
                MuscleBucket.ROTATION_CORE to 0.10
            )
            DurationHoldPolicy.SIDE_PLANK -> mapOf(
                MuscleBucket.LATERAL_CORE to 0.55,
                MuscleBucket.ADDUCTOR_ABDUCTOR to 0.25,
                MuscleBucket.SHOULDERS to 0.10,
                MuscleBucket.ANTERIOR_CORE to 0.10
            )
            null -> null
        }
    }

    private fun e1rmFor(set: WorkoutSet): Double? =
        if (set.confirmed && set.weightKg > 0.0 && set.reps in 1..12) {
            set.weightKg * (1.0 + set.reps / 30.0)
        } else {
            null
        }

    private fun weeklyBestSeries(values: Map<LocalDate, Double>): List<TrendDataPoint> =
        values.entries
            .groupBy { (date, _) -> date.weekStart() }
            .toSortedMap()
            .map { (week, rows) -> TrendDataPoint(week, rows.maxOf { (_, value) -> value }) }

    private fun rollingMap(
        dates: List<LocalDate>,
        daily: Map<LocalDate, Double>,
        days: Long
    ): Map<LocalDate, Double> = dates.associateWith { date ->
        generateSequence(date.minusDays(days - 1)) { it.plusDays(1) }
            .takeWhile { day -> !day.isAfter(date) }
            .sumOf { day -> daily[day] ?: 0.0 }
    }

    private fun weeklySumSeries(values: Map<LocalDate, Double>, sourceDates: Set<LocalDate> = values.keys): List<TrendDataPoint> {
        val sourceWeeks = sourceDates.map { date -> date.weekStart() }.toSet()
        return values.entries
            .filter { (date, _) -> date.weekStart() in sourceWeeks }
            .groupBy { (date, _) -> date.weekStart() }
            .toSortedMap()
            .mapNotNull { (week, rows) ->
                val value = rows.sumOf { (_, value) -> value }
                if (value > 0.0) TrendDataPoint(week, value) else null
            }
    }

    private fun LocalDate.weekStart(): LocalDate =
        with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun rpeWeight(rpe: Double?): Double = when {
        rpe == null -> 1.0
        rpe <= 6.0 -> 0.85
        rpe < 8.0 -> 1.0
        rpe < 9.0 -> 1.15
        rpe < 10.0 -> 1.30
        else -> 1.45
    }

    enum class MuscleBucket(
        val label: String,
        val dailyMetric: TrendMetricId,
        val threeDayMetric: TrendMetricId,
        val sevenDayMetric: TrendMetricId
    ) {
        QUADS("대퇴사두", TrendMetricId.MUSCLE_QUADS_LOAD_DAILY, TrendMetricId.MUSCLE_QUADS_LOAD_3D, TrendMetricId.MUSCLE_QUADS_LOAD_7D),
        HAMSTRINGS("햄스트링", TrendMetricId.MUSCLE_HAMSTRINGS_LOAD_DAILY, TrendMetricId.MUSCLE_HAMSTRINGS_LOAD_3D, TrendMetricId.MUSCLE_HAMSTRINGS_LOAD_7D),
        GLUTES("둔근", TrendMetricId.MUSCLE_GLUTES_LOAD_DAILY, TrendMetricId.MUSCLE_GLUTES_LOAD_3D, TrendMetricId.MUSCLE_GLUTES_LOAD_7D),
        CALVES("종아리", TrendMetricId.MUSCLE_CALVES_LOAD_DAILY, TrendMetricId.MUSCLE_CALVES_LOAD_3D, TrendMetricId.MUSCLE_CALVES_LOAD_7D),
        ADDUCTOR_ABDUCTOR("내전근/외전근", TrendMetricId.MUSCLE_ADDUCTOR_ABDUCTOR_LOAD_DAILY, TrendMetricId.MUSCLE_ADDUCTOR_ABDUCTOR_LOAD_3D, TrendMetricId.MUSCLE_ADDUCTOR_ABDUCTOR_LOAD_7D),
        POSTERIOR_CHAIN_ERECTORS("후면사슬/척추기립근", TrendMetricId.MUSCLE_POSTERIOR_CHAIN_ERECTORS_LOAD_DAILY, TrendMetricId.MUSCLE_POSTERIOR_CHAIN_ERECTORS_LOAD_3D, TrendMetricId.MUSCLE_POSTERIOR_CHAIN_ERECTORS_LOAD_7D),
        CHEST("가슴", TrendMetricId.MUSCLE_CHEST_LOAD_DAILY, TrendMetricId.MUSCLE_CHEST_LOAD_3D, TrendMetricId.MUSCLE_CHEST_LOAD_7D),
        BACK_LATS("등/광배", TrendMetricId.MUSCLE_BACK_LATS_LOAD_DAILY, TrendMetricId.MUSCLE_BACK_LATS_LOAD_3D, TrendMetricId.MUSCLE_BACK_LATS_LOAD_7D),
        SHOULDERS("어깨", TrendMetricId.MUSCLE_SHOULDERS_LOAD_DAILY, TrendMetricId.MUSCLE_SHOULDERS_LOAD_3D, TrendMetricId.MUSCLE_SHOULDERS_LOAD_7D),
        BICEPS("이두", TrendMetricId.MUSCLE_BICEPS_LOAD_DAILY, TrendMetricId.MUSCLE_BICEPS_LOAD_3D, TrendMetricId.MUSCLE_BICEPS_LOAD_7D),
        TRICEPS("삼두", TrendMetricId.MUSCLE_TRICEPS_LOAD_DAILY, TrendMetricId.MUSCLE_TRICEPS_LOAD_3D, TrendMetricId.MUSCLE_TRICEPS_LOAD_7D),
        FOREARM_GRIP("전완/그립", TrendMetricId.MUSCLE_FOREARM_GRIP_LOAD_DAILY, TrendMetricId.MUSCLE_FOREARM_GRIP_LOAD_3D, TrendMetricId.MUSCLE_FOREARM_GRIP_LOAD_7D),
        ANTERIOR_CORE("복근/전면코어", TrendMetricId.MUSCLE_ANTERIOR_CORE_LOAD_DAILY, TrendMetricId.MUSCLE_ANTERIOR_CORE_LOAD_3D, TrendMetricId.MUSCLE_ANTERIOR_CORE_LOAD_7D),
        LATERAL_CORE("측면코어", TrendMetricId.MUSCLE_LATERAL_CORE_LOAD_DAILY, TrendMetricId.MUSCLE_LATERAL_CORE_LOAD_3D, TrendMetricId.MUSCLE_LATERAL_CORE_LOAD_7D),
        ROTATION_CORE("항회전/회전코어", TrendMetricId.MUSCLE_ROTATION_CORE_LOAD_DAILY, TrendMetricId.MUSCLE_ROTATION_CORE_LOAD_3D, TrendMetricId.MUSCLE_ROTATION_CORE_LOAD_7D)
    }
}

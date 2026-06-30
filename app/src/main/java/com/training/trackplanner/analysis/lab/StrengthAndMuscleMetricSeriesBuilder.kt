package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object StrengthAndMuscleMetricSeriesBuilder {
    fun build(
        entriesWithSets: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>
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
            val confirmedSets = record.sets.filter { set -> set.confirmed }
            if (confirmedSets.isEmpty()) return@forEach
            datesWithConfirmedSets += date

            confirmedSets.forEach { set ->
                e1rmFor(set)?.let { e1rm ->
                    if (isMainBenchPress(exercise, record.entry)) {
                        benchE1rmByDate.merge(date, e1rm, ::maxOf)
                    }
                    if (isMainSquat(exercise, record.entry)) {
                        squatE1rmByDate.merge(date, e1rm, ::maxOf)
                    }
                    if (isMainDeadlift(exercise, record.entry)) {
                        deadliftE1rmByDate.merge(date, e1rm, ::maxOf)
                    }
                }

                if (set.reps > 0 && set.weightKg >= 0.0) {
                    val load = set.weightKg * set.reps * rpeWeight(set.rpe ?: record.entry.rpe)
                    if (load > 0.0) {
                        muscleContributions(exercise, record.entry).forEach { (bucket, weight) ->
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

    private fun isMainSquat(exercise: Exercise?, entry: WorkoutEntry): Boolean {
        val key = exercise?.stableKey.orEmpty().lowercase()
        if (key.isNotBlank()) {
            if (key in setOf("squat", "back_squat", "barbell_squat", "barbell_back_squat")) return true
            if ("back_squat" in key || "barbell_squat" in key) return true
            return false
        }
        val name = entry.exerciseName.lowercase()
        return ("스쿼트" in name || "squat" in name) &&
            listOf("런지", "lunge", "레그 프레스", "leg press", "스플릿", "split", "불가리안", "bulgarian", "점프", "jump", "고블릿", "goblet", "프론트", "front")
                .none { token -> token in name }
    }

    private fun isMainBenchPress(exercise: Exercise?, entry: WorkoutEntry): Boolean {
        val key = exercise?.stableKey.orEmpty().lowercase()
        if (key.isNotBlank()) {
            if (key in setOf("bench_press", "barbell_bench_press", "flat_barbell_bench_press")) return true
            if ("bench_press" in key && listOf("dumbbell", "incline", "decline", "close_grip", "floor").none { token -> token in key }) return true
            return false
        }
        val name = entry.exerciseName.lowercase()
        return ("벤치프레스" in name || "벤치 프레스" in name || "bench press" in name) &&
            listOf("덤벨", "dumbbell", "인클라인", "incline", "디클라인", "decline", "클로즈", "close", "플로어", "floor", "플라이", "fly")
                .none { token -> token in name }
    }

    private fun isMainDeadlift(exercise: Exercise?, entry: WorkoutEntry): Boolean {
        val key = exercise?.stableKey.orEmpty().lowercase()
        if (key.isNotBlank()) {
            if (key in setOf("deadlift", "barbell_deadlift", "conventional_deadlift")) return true
            return ("deadlift" in key) && listOf("rdl", "romanian", "stiff", "good_morning", "swing")
                .none { token -> token in key }
        }
        val name = entry.exerciseName.lowercase()
        return ("데드리프트" in name || "deadlift" in name) &&
            listOf("루마니안", "rdl", "romanian", "스티프", "stiff", "굿모닝", "good morning", "스윙", "swing")
                .none { token -> token in name }
    }

    private fun muscleContributions(exercise: Exercise?, entry: WorkoutEntry): Map<MuscleBucket, Double> {
        if (exercise != null) {
            val fromMetadata = mutableMapOf<MuscleBucket, Double>()
            exercise.primaryMuscles.toMuscleTokens().forEach { token ->
                bucketForToken(token)?.let { bucket -> fromMetadata.merge(bucket, 1.0, ::maxOf) }
            }
            exercise.secondaryMuscles.toMuscleTokens().forEach { token ->
                bucketForToken(token)?.let { bucket -> fromMetadata.merge(bucket, 0.5, ::maxOf) }
            }
            if (fromMetadata.isNotEmpty()) return fromMetadata
        }
        return fallbackContributions(exercise, entry)
    }

    private fun String.toMuscleTokens(): List<String> =
        split(',', '|', '/', ';')
            .map { token -> token.trim().uppercase() }
            .filter { token -> token.isNotBlank() }

    private fun bucketForToken(token: String): MuscleBucket? = when {
        "QUAD" in token || "대퇴사두" in token -> MuscleBucket.QUADS
        "HAMSTRING" in token || "햄스트링" in token -> MuscleBucket.HAMSTRINGS
        "GLUTE" in token || "둔근" in token -> MuscleBucket.GLUTES
        "CALF" in token || "CALVES" in token || "GASTROCNEMIUS" in token || "SOLEUS" in token || "종아리" in token -> MuscleBucket.CALVES
        "ADDUCTOR" in token || "ABDUCTOR" in token || "내전" in token || "외전" in token -> MuscleBucket.ADDUCTOR_ABDUCTOR
        "ERECTOR" in token || "SPINAL" in token || "LOW_BACK" in token || "POSTERIOR_CHAIN" in token || "척추" in token || "후면사슬" in token -> MuscleBucket.POSTERIOR_CHAIN_ERECTORS
        "CHEST" in token || "PECTORAL" in token || "가슴" in token -> MuscleBucket.CHEST
        "BACK" in token || "LAT" in token || "광배" in token || token == "등" -> MuscleBucket.BACK_LATS
        "SHOULDER" in token || "DELT" in token || "어깨" in token -> MuscleBucket.SHOULDERS
        "BICEP" in token || "이두" in token -> MuscleBucket.BICEPS
        "TRICEP" in token || "삼두" in token -> MuscleBucket.TRICEPS
        "FOREARM" in token || "GRIP" in token || "전완" in token || "그립" in token -> MuscleBucket.FOREARM_GRIP
        "ANTERIOR_CORE" in token || "ABS" in token || "ABDOMINAL" in token || "복근" in token || "전면코어" in token -> MuscleBucket.ANTERIOR_CORE
        "LATERAL_CORE" in token || "OBLIQUE" in token || "SIDE_CORE" in token || "측면코어" in token -> MuscleBucket.LATERAL_CORE
        "ANTI_ROTATION" in token || "ROTATION" in token || "ROTATIONAL" in token || "항회전" in token || "회전코어" in token -> MuscleBucket.ROTATION_CORE
        else -> null
    }

    private fun fallbackContributions(exercise: Exercise?, entry: WorkoutEntry): Map<MuscleBucket, Double> {
        val text = listOfNotNull(
            exercise?.stableKey,
            exercise?.movementPattern,
            exercise?.strengthProgressionGroup,
            exercise?.mainLiftGroup,
            entry.exerciseName
        ).joinToString(" ").lowercase()
        return when {
            isMainSquat(exercise, entry) -> mapOf(
                MuscleBucket.QUADS to 1.0,
                MuscleBucket.GLUTES to 0.5,
                MuscleBucket.HAMSTRINGS to 0.25,
                MuscleBucket.POSTERIOR_CHAIN_ERECTORS to 0.25
            )
            isMainDeadlift(exercise, entry) -> mapOf(
                MuscleBucket.POSTERIOR_CHAIN_ERECTORS to 1.0,
                MuscleBucket.GLUTES to 0.75,
                MuscleBucket.HAMSTRINGS to 0.75,
                MuscleBucket.FOREARM_GRIP to 0.25
            )
            "bench" in text || "벤치" in text -> mapOf(
                MuscleBucket.CHEST to 1.0,
                MuscleBucket.TRICEPS to 0.5,
                MuscleBucket.SHOULDERS to 0.25
            )
            "pull_up" in text || "pull-up" in text || "풀업" in text || "턱걸이" in text -> mapOf(
                MuscleBucket.BACK_LATS to 1.0,
                MuscleBucket.BICEPS to 0.5,
                MuscleBucket.FOREARM_GRIP to 0.5
            )
            "row" in text || "로우" in text -> mapOf(
                MuscleBucket.BACK_LATS to 1.0,
                MuscleBucket.BICEPS to 0.5,
                MuscleBucket.FOREARM_GRIP to 0.25
            )
            ("overhead" in text || "shoulder_press" in text || "숄더" in text) && "press" in text || "오버헤드프레스" in text -> mapOf(
                MuscleBucket.SHOULDERS to 1.0,
                MuscleBucket.TRICEPS to 0.5,
                MuscleBucket.ANTERIOR_CORE to 0.25
            )
            "pallof" in text || "팔로프" in text || "anti_rotation" in text -> mapOf(
                MuscleBucket.ROTATION_CORE to 1.0,
                MuscleBucket.LATERAL_CORE to 0.5
            )
            "russian" in text || "트위스트" in text || "rotation_core" in text -> mapOf(
                MuscleBucket.ROTATION_CORE to 1.0,
                MuscleBucket.ANTERIOR_CORE to 0.5,
                MuscleBucket.LATERAL_CORE to 0.5
            )
            else -> emptyMap()
        }
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

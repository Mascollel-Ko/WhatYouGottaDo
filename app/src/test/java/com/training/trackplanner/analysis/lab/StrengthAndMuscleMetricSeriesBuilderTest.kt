package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthAndMuscleMetricSeriesBuilderTest {
    @Test
    fun benchPressE1rmUsesConfirmedMainBenchSetsOnly() {
        val bench = exercise(1, "벤치프레스", "barbell_bench_press")
        val dumbbellBench = exercise(2, "덤벨 벤치프레스", "dumbbell_bench_press")
        val series = build(
            exercises = listOf(bench, dumbbellBench),
            records = listOf(
                record(
                    "2026-06-10",
                    bench,
                    set(1, 100.0, 5),
                    set(2, 200.0, 5, confirmed = false)
                ),
                record("2026-06-10", dumbbellBench, set(3, 50.0, 8))
            )
        )

        assertEquals(116.666, series.value(TrendMetricId.BENCH_PRESS_E1RM, "2026-06-08"), 0.01)
        assertEquals(1, series.getValue(TrendMetricId.BENCH_PRESS_E1RM).size)
    }

    @Test
    fun squatE1rmUsesEpleyAndDailyMax() {
        val exercise = exercise(1, "바벨 백스쿼트", "barbell_back_squat")
        val series = build(
            exercises = listOf(exercise),
            records = listOf(
                record("2026-06-10", exercise, set(1, 100.0, 5), set(2, 110.0, 2))
            )
        )

        assertEquals(117.333, series.value(TrendMetricId.SQUAT_E1RM, "2026-06-08"), 0.01)
    }

    @Test
    fun squatE1rmExcludesLungeAndLegPress() {
        val lunge = exercise(1, "워킹 런지", "walking_lunge")
        val legPress = exercise(2, "레그 프레스", "leg_press")
        val series = build(
            exercises = listOf(lunge, legPress),
            records = listOf(
                record("2026-06-10", lunge, set(1, 60.0, 8)),
                record("2026-06-11", legPress, set(2, 160.0, 10))
            )
        )

        assertTrue(series.getValue(TrendMetricId.SQUAT_E1RM).isEmpty())
    }

    @Test
    fun deadliftE1rmUsesEpleyAndExcludesRdl() {
        val deadlift = exercise(1, "데드리프트", "conventional_deadlift")
        val rdl = exercise(2, "루마니안 데드리프트", "romanian_deadlift")
        val series = build(
            exercises = listOf(deadlift, rdl),
            records = listOf(
                record("2026-06-10", deadlift, set(1, 160.0, 3)),
                record("2026-06-11", rdl, set(2, 120.0, 8))
            )
        )

        assertEquals(176.0, series.value(TrendMetricId.DEADLIFT_E1RM, "2026-06-08"), 0.01)
        assertEquals(1, series.getValue(TrendMetricId.DEADLIFT_E1RM).size)
    }

    @Test
    fun stableExerciseMetadataResolvesImportedLookingDisplayName() {
        val squat = exercise(1, "바벨 백스쿼트", "barbell_back_squat")
        val series = build(
            exercises = listOf(squat),
            records = listOf(
                WorkoutEntryWithSets(
                    entry = WorkoutEntry(date = "2026-06-10", exerciseId = squat.id, exerciseName = "운동113", category = "근력"),
                    sets = listOf(set(1, 100.0, 5))
                )
            )
        )

        assertEquals(116.666, series.value(TrendMetricId.SQUAT_E1RM, "2026-06-08"), 0.01)
    }

    @Test
    fun missingStrengthPerformanceDateIsNotFilledWithZero() {
        val rdl = exercise(1, "루마니안 데드리프트", "romanian_deadlift")
        val series = build(listOf(record("2026-06-10", rdl, set(1, 120.0, 8))), listOf(rdl))

        assertTrue(series.getValue(TrendMetricId.DEADLIFT_E1RM).isEmpty())
    }

    @Test
    fun squatMuscleLoadUsesFallbackContributionsAndRpe() {
        val squat = exercise(1, "바벨 백스쿼트", "barbell_back_squat")
        val series = build(listOf(record("2026-06-10", squat, set(1, 100.0, 5, rpe = 8.0))), listOf(squat))

        assertEquals(575.0, series.value(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY, "2026-06-08"), 0.01)
        assertEquals(287.5, series.value(TrendMetricId.MUSCLE_GLUTES_LOAD_DAILY, "2026-06-08"), 0.01)
        assertEquals(143.75, series.value(TrendMetricId.MUSCLE_HAMSTRINGS_LOAD_DAILY, "2026-06-08"), 0.01)
        assertEquals(143.75, series.value(TrendMetricId.MUSCLE_POSTERIOR_CHAIN_ERECTORS_LOAD_DAILY, "2026-06-08"), 0.01)
    }

    @Test
    fun deadliftMuscleLoadUsesFallbackContributionsAndRpe() {
        val deadlift = exercise(1, "데드리프트", "conventional_deadlift")
        val series = build(listOf(record("2026-06-10", deadlift, set(1, 160.0, 3, rpe = 9.0))), listOf(deadlift))

        assertEquals(624.0, series.value(TrendMetricId.MUSCLE_POSTERIOR_CHAIN_ERECTORS_LOAD_DAILY, "2026-06-08"), 0.01)
        assertEquals(468.0, series.value(TrendMetricId.MUSCLE_GLUTES_LOAD_DAILY, "2026-06-08"), 0.01)
        assertEquals(468.0, series.value(TrendMetricId.MUSCLE_HAMSTRINGS_LOAD_DAILY, "2026-06-08"), 0.01)
        assertEquals(156.0, series.value(TrendMetricId.MUSCLE_FOREARM_GRIP_LOAD_DAILY, "2026-06-08"), 0.01)
    }

    @Test
    fun deadliftQuadricepsOverrideIncreasesQuadLoad() {
        val seedDeadlift = exercise(1, "데드리프트", "conventional_deadlift").copy(
            primaryMuscles = "HAMSTRING|GLUTE|ERECTOR_SPINAE",
            secondaryMuscles = "FOREARM"
        )
        val overriddenDeadlift = seedDeadlift.copy(
            primaryMuscles = "HAMSTRING|GLUTE|ERECTOR_SPINAE|quadriceps"
        )
        val records = listOf(record("2026-06-10", seedDeadlift, set(1, 160.0, 3, rpe = 9.0)))

        val seedSeries = build(records, listOf(seedDeadlift))
        val overrideSeries = build(records, listOf(overriddenDeadlift))

        assertTrue(seedSeries[TrendMetricId.MUSCLE_QUADS_LOAD_DAILY].orEmpty().isEmpty())
        assertEquals(624.0, overrideSeries.value(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY, "2026-06-08"), 0.01)
    }

    @Test
    fun koreanAndEnglishMuscleAliasesNormalizeToSameQuadBucket() {
        val koreanAlias = exercise(1, "대퇴근 테스트", "user_ex_korean_quad").copy(primaryMuscles = "대퇴근")
        val englishAlias = exercise(2, "Quadriceps test", "user_ex_english_quad").copy(primaryMuscles = "quadriceps")

        val koreanSeries = build(listOf(record("2026-06-10", koreanAlias, set(1, 100.0, 5, rpe = 8.0))), listOf(koreanAlias))
        val englishSeries = build(listOf(record("2026-06-10", englishAlias, set(1, 100.0, 5, rpe = 8.0))), listOf(englishAlias))

        assertEquals(575.0, koreanSeries.value(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY, "2026-06-08"), 0.01)
        assertEquals(575.0, englishSeries.value(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY, "2026-06-08"), 0.01)
    }

    @Test
    fun muscleLoadAggregatesByWeekAndDoesNotCarryForwardMissingWeeks() {
        val squat = exercise(1, "바벨 백스쿼트", "barbell_back_squat")
        val series = build(
            exercises = listOf(squat),
            records = listOf(
                record("2026-06-08", squat, set(1, 100.0, 1, rpe = 7.0)),
                record("2026-06-09", squat, set(2, 200.0, 1, rpe = 7.0)),
                record("2026-06-10", squat, set(3, 300.0, 1, rpe = 7.0)),
                record("2026-06-22", squat, set(4, 400.0, 1, rpe = 7.0))
            )
        )

        assertEquals(600.0, series.value(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY, "2026-06-08"), 0.01)
        assertEquals(400.0, series.value(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY, "2026-06-22"), 0.01)
        assertTrue(series.getValue(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY).none { point -> point.weekStart.toString() == "2026-06-15" })
    }

    @Test
    fun labRegistryIncludesStrengthPerformanceAndMuscleLoadCandidates() {
        assertEquals("주간 스쿼트 e1RM 최고", AnalysisMetricRegistry.descriptor(TrendMetricId.SQUAT_E1RM)?.displayName)
        assertEquals("주간 벤치프레스 e1RM 최고", AnalysisMetricRegistry.descriptor(TrendMetricId.BENCH_PRESS_E1RM)?.displayName)
        assertEquals("kg", AnalysisMetricRegistry.descriptor(TrendMetricId.BENCH_PRESS_E1RM)?.unit)
        assertEquals("kg", AnalysisMetricRegistry.descriptor(TrendMetricId.SQUAT_E1RM)?.unit)
        assertEquals("kg", AnalysisMetricRegistry.descriptor(TrendMetricId.DEADLIFT_E1RM)?.unit)
        assertEquals(AnalysisTimeGrain.WEEKLY, AnalysisMetricRegistry.descriptor(TrendMetricId.SQUAT_E1RM)?.timeGrain)
        assertEquals(AnalysisMetricCategory.PERFORMANCE, AnalysisMetricRegistry.descriptor(TrendMetricId.SQUAT_E1RM)?.category)
        assertEquals(AnalysisMetricCategory.MUSCLE_LOAD, AnalysisMetricRegistry.descriptor(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY)?.category)
        assertEquals("운동량 지수", AnalysisMetricRegistry.descriptor(TrendMetricId.MUSCLE_QUADS_LOAD_7D)?.unit)
    }

    @Test
    fun labSelectorExposesWeeklyGeneratedMetricsWithoutKeyMismatch() {
        val squat = exercise(1, "바벨 백스쿼트", "barbell_back_squat")
        val series = build(listOf(record("2026-06-10", squat, set(1, 100.0, 5))), listOf(squat))

        val available = AnalysisMetricRegistry.scatterMetrics(series).map { descriptor -> descriptor.id }

        assertTrue(TrendMetricId.SQUAT_E1RM in available)
        assertTrue(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY in available)
        assertTrue(series.keys.all { id -> AnalysisMetricRegistry.descriptor(id) != null })
    }

    @Test
    fun customExerciseMuscleMetadataDrivesWeeklyLoad() {
        val custom = exercise(1, "내 커스텀 하체운동", "user_ex_custom_lower").copy(
            isCustom = true,
            primaryMuscles = "QUADRICEPS",
            secondaryMuscles = "GLUTE"
        )
        val series = build(listOf(record("2026-06-10", custom, set(1, 100.0, 5, rpe = 8.0))), listOf(custom))

        assertEquals(575.0, series.value(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY, "2026-06-08"), 0.01)
        assertEquals(287.5, series.value(TrendMetricId.MUSCLE_GLUTES_LOAD_DAILY, "2026-06-08"), 0.01)
        assertTrue(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY in AnalysisMetricRegistry.scatterMetrics(series).map { it.id })
    }

    @Test
    fun sidePlankDurationLoadContributesToLateralCoreAndHipStability() {
        val sidePlank = exercise(1, "side plank", "side_plank")
        val series = build(
            listOf(record("2026-06-10", sidePlank, set(1, 0.0, 0, seconds = 30, rpe = 8.0))),
            listOf(sidePlank)
        )

        assertEquals(18.975, series.value(TrendMetricId.MUSCLE_LATERAL_CORE_LOAD_DAILY, "2026-06-08"), 0.001)
        assertEquals(8.625, series.value(TrendMetricId.MUSCLE_ADDUCTOR_ABDUCTOR_LOAD_DAILY, "2026-06-08"), 0.001)
        assertEquals(3.45, series.value(TrendMetricId.MUSCLE_SHOULDERS_LOAD_DAILY, "2026-06-08"), 0.001)
    }

    private fun build(
        records: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>
    ): Map<TrendMetricId, List<com.training.trackplanner.analysis.trends.TrendDataPoint>> =
        StrengthAndMuscleMetricSeriesBuilder.build(records, exercises)

    private fun exercise(id: Long, name: String, stableKey: String): Exercise =
        Exercise(id = id, name = name, category = "근력", stableKey = stableKey)

    private fun record(date: String, exercise: Exercise, vararg sets: WorkoutSet): WorkoutEntryWithSets =
        WorkoutEntryWithSets(
            entry = WorkoutEntry(date = date, exerciseId = exercise.id, exerciseName = exercise.name, category = exercise.category),
            sets = sets.toList()
        )

    private fun set(
        index: Int,
        weight: Double,
        reps: Int,
        rpe: Double? = null,
        confirmed: Boolean = true,
        seconds: Int = 0
    ): WorkoutSet =
        WorkoutSet(entryId = 1, setIndex = index, weightKg = weight, reps = reps, seconds = seconds, confirmed = confirmed, rpe = rpe)

    private fun Map<TrendMetricId, List<com.training.trackplanner.analysis.trends.TrendDataPoint>>.value(
        metric: TrendMetricId,
        date: String
    ): Double = getValue(metric).first { point -> point.weekStart.toString() == date }.value!!
}

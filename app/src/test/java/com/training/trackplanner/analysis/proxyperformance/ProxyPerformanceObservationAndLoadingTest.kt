package com.training.trackplanner.analysis.proxyperformance

import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyPerformanceObservationAndLoadingTest {
    @Test
    fun actualBenchE1rmMatchesExistingWeeklySeriesExactly() {
        val bench = exercise(1, "벤치프레스", "barbell_bench_press")
        val record = record(
            id = 11,
            date = "2026-01-07",
            exercise = bench,
            sets = listOf(
                set(101, 11, 0, 100.0, 5, rpe = 8.0),
                set(102, 11, 1, 105.0, 3, rpe = 9.0)
            )
        )
        val existing = StrengthAndMuscleMetricSeriesBuilder.build(listOf(record), listOf(bench))
            .getValue(TrendMetricId.BENCH_PRESS_E1RM)
            .single()
            .value
        val observation = observations(listOf(record), listOf(bench)).single()

        assertEquals(requireNotNull(existing), observation.canonicalE1rmKg, 0.0)
        assertEquals(116.66666666666667, observation.canonicalE1rmKg, 0.000001)
    }

    @Test
    fun lowerRpeProducesStrongerSignalAndMissingRpeHasLargerVariance() {
        val bench = exercise(1, "벤치프레스", "barbell_bench_press")
        val records = listOf(
            record(1, "2026-01-01", bench, listOf(set(1, 1, 0, 100.0, 6, rpe = 7.0))),
            record(2, "2026-01-08", bench, listOf(set(2, 2, 0, 100.0, 6, rpe = 9.0))),
            record(3, "2026-01-15", bench, listOf(set(3, 3, 0, 100.0, 6)))
        )
        val observations = observations(records, listOf(bench)).associateBy(ProxyPerformanceObservation::workoutEntryId)

        assertTrue(observations.getValue(1).effortAdjustedPerformanceKg > observations.getValue(2).effortAdjustedPerformanceKg)
        assertEquals(120.0, observations.getValue(1).canonicalE1rmKg, 0.000001)
        assertEquals(120.0, observations.getValue(2).canonicalE1rmKg, 0.000001)
        assertTrue(observations.getValue(3).observationVariance > observations.getValue(1).observationVariance)
    }

    @Test
    fun confirmedSupportedSetsBecomeOneRobustSessionObservation() {
        val squat = exercise(1, "스쿼트", "barbell_back_squat")
        val record = record(
            1,
            "2026-01-01",
            squat,
            listOf(
                set(1, 1, 0, 100.0, 5, rpe = 8.0),
                set(2, 1, 1, 500.0, 5, rpe = 8.0, confirmed = false),
                set(3, 1, 2, 60.0, 15, rpe = 8.0),
                set(4, 1, 3, 105.0, 3, rpe = 8.0)
            )
        )

        val observation = observations(listOf(record), listOf(squat)).single()

        assertEquals(listOf(1L, 4L), observation.sourceSetIds)
        assertEquals(116.66666666666667, observation.canonicalE1rmKg, 0.000001)
    }

    @Test
    fun canonicalBodyweightPolicySuppliesDipEffectiveLoad() {
        val dip = exercise(1, "딥스", "ex_dip").copy(
            familyId = "DIP_COMPOUND_PUSH_VARIANTS",
            movementPattern = "DIP_COMPOUND_PUSH"
        )
        val record = record(1, "2026-01-02", dip, listOf(set(1, 1, 0, 0.0, 8, rpe = 8.0)))

        val observation = observations(
            listOf(record),
            listOf(dip),
            dailyMetrics = listOf(DailyMetric("2026-01-01", bodyWeightKg = 80.0))
        ).single()

        assertTrue(observation.canonicalE1rmKg > 100.0)
        assertTrue(observation.loading.isUsable)
    }

    @Test
    fun unsupportedOrUnconfirmedOnlyEntryCreatesNoObservation() {
        val bench = exercise(1, "벤치프레스", "barbell_bench_press")
        val records = listOf(
            record(1, "2026-01-01", bench, listOf(set(1, 1, 0, 100.0, 15, rpe = 8.0))),
            record(2, "2026-01-02", bench, listOf(set(2, 2, 0, 100.0, 5, rpe = 8.0, confirmed = false)))
        )

        assertTrue(observations(records, listOf(bench)).isEmpty())
    }

    @Test
    fun pressAndLowerBodyLoadingsRespectMetadataDistance() {
        val dumbbell = exercise(1, "덤벨 벤치프레스", "ex_db_bench").copy(
            familyId = "BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS",
            movementCategory = "DUMBBELL_BENCH_PRESS"
        )
        val overhead = exercise(2, "오버헤드 프레스", "ex_ohp").copy(
            familyId = "OVERHEAD_PRESS_VARIANTS",
            movementCategory = "BARBELL_OVERHEAD_PRESS"
        )
        val machine = exercise(3, "머신 체스트프레스", "ex_machine_press").copy(
            familyId = "BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS",
            movementCategory = "MACHINE_CHEST_PRESS"
        )
        val squat = exercise(4, "스쿼트", "barbell_back_squat")
        val deadlift = exercise(5, "데드리프트", "conventional_deadlift")
        val rdl = exercise(6, "루마니안 데드리프트", "ex_rdl").copy(
            familyId = "DEADLIFT_HINGE_VARIANTS",
            movementCategory = "ROMANIAN_DEADLIFT"
        )
        val lunge = exercise(7, "런지", "ex_lunge").copy(
            familyId = "LUNGE_SPLIT_SQUAT_VARIANTS",
            movementCategory = "GENERIC_LUNGE"
        )
        val legPress = exercise(8, "레그 프레스", "ex_leg_press").copy(
            familyId = "LEG_PRESS_KNEE_DOMINANT_MACHINE_VARIANTS",
            movementCategory = "LEG_PRESS"
        )
        val dbLoading = loading(dumbbell)
        val ohpLoading = loading(overhead)
        val machineLoading = loading(machine)
        val squatLoading = loading(squat)
        val deadliftLoading = loading(deadlift)
        val rdlLoading = loading(rdl)
        val lungeLoading = loading(lunge)
        val legPressLoading = loading(legPress)

        assertTrue(dbLoading.factors.getValue(ProxyLatentFactor.PRESS_SHARED) > ohpLoading.factors.getValue(ProxyLatentFactor.PRESS_SHARED))
        assertTrue(ohpLoading.factors.getValue(ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC) > 0.0)
        assertTrue(machineLoading.factors.getValue(ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC) > 0.0)
        assertTrue(ProxyPerformanceLoadingBuilder.targetAffinity(squatLoading, MajorLiftTarget.DEADLIFT) > 0.0)
        assertTrue(ProxyPerformanceLoadingBuilder.targetAffinity(deadliftLoading, MajorLiftTarget.SQUAT) > 0.0)
        assertTrue(
            ProxyPerformanceLoadingBuilder.targetAffinity(rdlLoading, MajorLiftTarget.DEADLIFT) >
                ProxyPerformanceLoadingBuilder.targetAffinity(rdlLoading, MajorLiftTarget.SQUAT)
        )
        assertTrue(
            ProxyPerformanceLoadingBuilder.targetAffinity(lungeLoading, MajorLiftTarget.SQUAT) >
                ProxyPerformanceLoadingBuilder.targetAffinity(lungeLoading, MajorLiftTarget.DEADLIFT)
        )
        assertTrue(
            legPressLoading.factors.getValue(ProxyLatentFactor.TRUNK_BRACING) <
                squatLoading.factors.getValue(ProxyLatentFactor.TRUNK_BRACING)
        )
    }

    @Test
    fun tissueStressAloneCannotCreateStrengthTransferLoading() {
        val tissueOnly = exercise(1, "조직 부하 테스트", "ex_tissue_only").copy(
            jointStressTags = "PATELLAR_TENDON_STRESS|KNEE_IMPACT_STRESS",
            primaryMuscles = "QUADRICEPS",
            metadataConfidence = "HIGH"
        )

        assertFalse(loading(tissueOnly).isUsable)
    }

    private fun loading(exercise: Exercise): ProxyPerformanceLoading =
        ProxyPerformanceLoadingBuilder.build(
            exercise,
            WorkoutEntry(id = exercise.id, date = "2026-01-01", exerciseId = exercise.id, exerciseName = exercise.name, category = "근력"),
            runtimeMetadata = null
        )

    private fun observations(
        records: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        dailyMetrics: List<DailyMetric> = emptyList()
    ): List<ProxyPerformanceObservation> = ProxyPerformanceObservationBuilder.build(
        entriesWithSets = records,
        exercises = exercises,
        runtimeMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
        dailyMetrics = dailyMetrics
    ).observations

    private fun exercise(id: Long, name: String, stableKey: String): Exercise = Exercise(
        id = id,
        name = name,
        category = "근력",
        stableKey = stableKey,
        metadataConfidence = "HIGH"
    )

    private fun record(
        id: Long,
        date: String,
        exercise: Exercise,
        sets: List<WorkoutSet>
    ): WorkoutEntryWithSets = WorkoutEntryWithSets(
        entry = WorkoutEntry(
            id = id,
            date = date,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            category = exercise.category,
            displayOrder = id.toInt()
        ),
        sets = sets
    )

    private fun set(
        id: Long,
        entryId: Long,
        index: Int,
        weight: Double,
        reps: Int,
        rpe: Double? = null,
        confirmed: Boolean = true
    ): WorkoutSet = WorkoutSet(
        id = id,
        entryId = entryId,
        setIndex = index,
        weightKg = weight,
        reps = reps,
        rpe = rpe,
        confirmed = confirmed
    )
}

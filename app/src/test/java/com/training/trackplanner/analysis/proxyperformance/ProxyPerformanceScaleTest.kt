package com.training.trackplanner.analysis.proxyperformance

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyPerformanceScaleTest {
    @Test
    fun threeYearHistoryKeepsFixedStateAndBuildsWithinBoundedTime() {
        val related = listOf(
            exercise(1, "barbell_bench_press"),
            exercise(2, "db_bench").copy(familyId = "BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS", movementCategory = "DUMBBELL_BENCH_PRESS"),
            exercise(3, "barbell_back_squat"),
            exercise(4, "rdl").copy(familyId = "DEADLIFT_HINGE_VARIANTS", movementCategory = "ROMANIAN_DEADLIFT"),
            exercise(5, "conventional_deadlift"),
            exercise(6, "lunge").copy(familyId = "LUNGE_SPLIT_SQUAT_VARIANTS", movementCategory = "GENERIC_LUNGE")
        )
        val exercises = related + (7L..239L).map { id -> exercise(id, "unrelated_$id") }
        val start = LocalDate.parse("2023-01-02")
        val records = (0 until 2_400).map { index ->
            val exercise = related[index % related.size]
            val id = index.toLong() + 1
            WorkoutEntryWithSets(
                entry = WorkoutEntry(
                    id = id,
                    date = start.plusDays((index % 1_095).toLong()).toString(),
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    category = "근력",
                    performedAt = id,
                    displayOrder = index % 4
                ),
                sets = listOf(
                    WorkoutSet(
                        id = id,
                        entryId = id,
                        setIndex = 0,
                        reps = 3 + index % 5,
                        weightKg = 60.0 + index % 90,
                        confirmed = true,
                        rpe = if (index % 4 == 0) null else 7.0 + index % 3
                    )
                )
            )
        }
        lateinit var summary: ProxyPerformanceSummary
        val elapsed = measureTimeMillis {
            summary = ProxyPerformanceSummaryBuilder.build(
                today = start.plusDays(1_094),
                exercises = exercises,
                entriesWithSets = records,
                dailyMetrics = emptyList(),
                runtimeMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
            )
        }

        assertEquals(8, ProxyPerformanceStateModel.config(MajorLiftTarget.BENCH_PRESS, ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC).factors.size)
        assertEquals(3, summary.targets.size)
        assertTrue("Proxy summary took ${elapsed}ms", elapsed < 10_000)
        assertTrue(summary.targets.values.flatMap(MajorLiftProxySummary::weeklyPosterior).none { point ->
            listOf(point.posteriorMedianKg, point.posteriorLow80Kg, point.posteriorHigh80Kg)
                .filterNotNull()
                .any { value -> !value.isFinite() }
        })
    }

    private fun exercise(id: Long, stableKey: String): Exercise = Exercise(
        id = id,
        name = stableKey,
        category = "근력",
        stableKey = stableKey,
        metadataConfidence = "HIGH"
    )
}

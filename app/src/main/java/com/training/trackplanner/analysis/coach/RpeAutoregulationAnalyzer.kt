package com.training.trackplanner.analysis.coach

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate
import kotlin.math.abs

class RpeAutoregulationAnalyzer {
    fun analyze(
        today: LocalDate,
        entriesWithSets: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        sleepSignal: SleepRecoverySignal
    ): RpeAutoregulationSignal? {
        val exerciseById = exercises.associateBy { it.id }
        val observations = entriesWithSets.flatMap { record ->
            val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull() ?: return@flatMap emptyList()
            val exercise = exerciseById[record.entry.exerciseId]
            val key = exercise?.stableKey?.takeIf { it.isNotBlank() } ?: record.entry.exerciseName
            record.sets.filter { it.confirmed }.mapNotNull { set ->
                val rpe = set.rpe ?: record.entry.rpe ?: return@mapNotNull null
                RpeObservation(
                    date = date,
                    key = key,
                    exerciseName = record.entry.exerciseName,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    rpe = rpe
                )
            }
        }.filter { it.date <= today && it.reps > 0 }

        val recentStart = today.minusDays(6)
        val baselineStart = today.minusDays(34)
        val candidates = observations.groupBy { it.key }.mapNotNull { (_, group) ->
            val recent = group.filter { it.date in recentStart..today }
            if (recent.isEmpty()) return@mapNotNull null
            val recentReps = recent.map { it.reps }.average()
            val recentWeight = recent.map { it.weightKg }.average()
            val baseline = group.filter { observation ->
                observation.date in baselineStart..recentStart.minusDays(1) &&
                    observation.isComparableTo(recentReps, recentWeight)
            }
            if (baseline.size < 2 || recent.size < 1) return@mapNotNull null
            val recentRpe = recent.map { it.rpe }.average()
            val baselineRpe = baseline.map { it.rpe }.average()
            val delta = recentRpe - baselineRpe
            if (delta < 1.0 || recentRpe < 7.5) return@mapNotNull null
            RpeCandidate(
                exerciseName = recent.maxBy { it.date }.exerciseName,
                delta = delta,
                recentRpe = recentRpe,
                baselineRpe = baselineRpe,
                sampleSize = recent.size + baseline.size
            )
        }.sortedWith(compareByDescending<RpeCandidate> { it.delta }.thenByDescending { it.recentRpe })

        val top = candidates.firstOrNull() ?: return null
        val severity = if (top.delta >= 1.5 || top.recentRpe >= 9.0) {
            CoachingSignalSeverity.CAUTION
        } else {
            CoachingSignalSeverity.WATCH
        }
        val sleepContext = if (sleepSignal.severity.priority() >= CoachingSignalSeverity.WATCH.priority()) {
            "최근 수면 입력이 낮아 RPE 해석을 보수적으로 봅니다."
        } else {
            null
        }
        return RpeAutoregulationSignal(
            exerciseName = top.exerciseName,
            severity = severity,
            headline = "비슷한 부하에서 RPE가 상승했습니다",
            detail = "${top.exerciseName}의 최근 RPE 평균 ${top.recentRpe.formatOneDecimal()}, 이전 비슷한 세트 ${top.baselineRpe.formatOneDecimal()}입니다. 오늘은 같은 무게라도 체감 강도를 확인하며 조절합니다.",
            sleepContext = sleepContext,
            sampleSize = top.sampleSize
        )
    }

    private data class RpeObservation(
        val date: LocalDate,
        val key: String,
        val exerciseName: String,
        val reps: Int,
        val weightKg: Double,
        val rpe: Double
    ) {
        fun isComparableTo(recentReps: Double, recentWeight: Double): Boolean {
            val repsComparable = abs(reps - recentReps) <= 2.0
            val weightTolerance = maxOf(2.5, recentWeight * 0.075)
            val weightComparable = if (recentWeight <= 0.0 && weightKg <= 0.0) {
                true
            } else {
                abs(weightKg - recentWeight) <= weightTolerance
            }
            return repsComparable && weightComparable
        }
    }

    private data class RpeCandidate(
        val exerciseName: String,
        val delta: Double,
        val recentRpe: Double,
        val baselineRpe: Double,
        val sampleSize: Int
    )
}

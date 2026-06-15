package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.analysis.features.ExerciseAnalysisMapper
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

class PerformanceDropDetector {
    fun detect(
        entriesWithSets: List<WorkoutEntryWithSets>,
        exerciseMap: Map<Long, Exercise>,
        today: LocalDate
    ): PerformanceSignalSnapshot {
        val completedRecords = entriesWithSets
            .filter { record -> record.sets.any { set -> set.confirmed } }
            .filter { record -> runCatching { LocalDate.parse(record.entry.date) }.getOrNull()?.let { it <= today } == true }
            .groupBy { record -> record.entry.exerciseId }

        var sameLoadRpeIncrease = false
        var sameLoadRepsDrop = false
        var estimated1RmDrop = false
        val reasons = mutableListOf<String>()

        completedRecords.forEach { (exerciseId, records) ->
            val exercise = exerciseMap[exerciseId] ?: return@forEach
            val sorted = records.sortedBy { record -> record.entry.date }
            if (sorted.size < 2) return@forEach
            val latest = sorted.last()
            val previous = sorted.dropLast(1).lastOrNull() ?: return@forEach
            val latestFeatures = ExerciseAnalysisMapper.fromRecord(exercise, latest.entry, latest.sets)
            val previousFeatures = ExerciseAnalysisMapper.fromRecord(exercise, previous.entry, previous.sets)

            val latestMainSet = latest.sets.filter { set -> set.confirmed }.maxByOrNull { set -> set.weightKg }
            val previousMainSet = previous.sets.filter { set -> set.confirmed }.maxByOrNull { set -> set.weightKg }
            if (latestMainSet != null && previousMainSet != null && latestMainSet.weightKg > 0.0) {
                val sameLoad = kotlin.math.abs(latestMainSet.weightKg - previousMainSet.weightKg) <= 0.5
                if (sameLoad) {
                    val latestRpe = latestMainSet.rpe ?: latest.entry.rpe
                    val previousRpe = previousMainSet.rpe ?: previous.entry.rpe
                    if (latestRpe != null && previousRpe != null && latestRpe - previousRpe >= 2.0) {
                        sameLoadRpeIncrease = true
                    }
                    if (previousMainSet.reps > 0) {
                        val dropRatio = (previousMainSet.reps - latestMainSet.reps).toDouble() / previousMainSet.reps
                        if (dropRatio >= 0.15) sameLoadRepsDrop = true
                    }
                }
            }
            val latestE1rm = latestFeatures.estimated1Rm
            val previousE1rm = previousFeatures.estimated1Rm
            if (latestE1rm != null && previousE1rm != null && previousE1rm > 0.0) {
                val dropRatio = (previousE1rm - latestE1rm) / previousE1rm
                if (dropRatio >= 0.05) estimated1RmDrop = true
            }
        }

        if (sameLoadRpeIncrease) reasons += "같은 중량에서 체감 강도가 높아졌습니다."
        if (sameLoadRepsDrop) reasons += "같은 중량 반복수가 줄었습니다."
        if (estimated1RmDrop) reasons += "최근 근력 지표가 낮게 잡혔습니다."
        val level = when {
            reasons.size >= 2 -> FatigueLevel.HIGH
            reasons.size == 1 -> FatigueLevel.ELEVATED
            completedRecords.values.sumOf { it.size } < 2 -> FatigueLevel.LOW
            else -> FatigueLevel.NORMAL
        }
        val confidence = when {
            completedRecords.values.sumOf { it.size } >= 8 -> AnalysisConfidence.MEDIUM
            completedRecords.values.sumOf { it.size } >= 2 -> AnalysisConfidence.MEDIUM_LOW
            else -> AnalysisConfidence.LOW
        }

        return PerformanceSignalSnapshot(
            sameLoadRpeIncrease = sameLoadRpeIncrease,
            sameLoadRepsDrop = sameLoadRepsDrop,
            estimated1RmDrop = estimated1RmDrop,
            plannedSetFailure = false,
            testPerformanceDrop = false,
            footworkTestDrop = false,
            level = level,
            confidence = confidence,
            reasons = reasons
        )
    }
}

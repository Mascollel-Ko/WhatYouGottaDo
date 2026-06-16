package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class InitialProfileReadinessAdjuster {
    fun adjust(
        summary: TodayReadinessSummary,
        today: LocalDate,
        completedEntries: List<WorkoutEntryWithSets>,
        dailyMetrics: List<DailyMetric>,
        initialProfile: InitialUserProfile?
    ): TodayReadinessSummary {
        if (summary.status != ReadinessStatus.FATIGUED) return summary

        val mode = mode(today, completedEntries)
        if (mode == InitialReadinessMode.PERSONAL_BASELINE) return summary

        val strongSignals = strongSignals(today, completedEntries, dailyMetrics, initialProfile)
        if (strongSignals >= 2) return summary

        val note = if (initialProfile == null) {
            "기록이 더 쌓이면 판단이 안정됩니다."
        } else {
            "초기 프로필과 최근 기록을 함께 반영했습니다."
        }
        return summary.copy(
            status = ReadinessStatus.CAUTION,
            headline = "오늘은 강도를 조금 조절하세요.",
            shortReason = "초기 기록 구간이라 보수적으로 해석했습니다.",
            adaptiveBaselineNotes = (summary.adaptiveBaselineNotes + note).distinct()
        )
    }

    private fun mode(
        today: LocalDate,
        completedEntries: List<WorkoutEntryWithSets>
    ): InitialReadinessMode {
        val dates = completedEntries
            .mapNotNull { runCatching { LocalDate.parse(it.entry.date) }.getOrNull() }
            .distinct()
            .sorted()
        if (dates.isEmpty() || dates.size < 4) return InitialReadinessMode.COLD_START
        val lastGap = ChronoUnit.DAYS.between(dates.last(), today)
        if (lastGap >= 60) return InitialReadinessMode.RETURNING_AFTER_BREAK
        return if (dates.size < 42) InitialReadinessMode.BASELINE_BUILDING else InitialReadinessMode.PERSONAL_BASELINE
    }

    private fun strongSignals(
        today: LocalDate,
        completedEntries: List<WorkoutEntryWithSets>,
        dailyMetrics: List<DailyMetric>,
        initialProfile: InitialUserProfile?
    ): Int {
        var count = 0
        val recentConfirmedSets = completedEntries
            .filter { item ->
                val date = runCatching { LocalDate.parse(item.entry.date) }.getOrNull()
                date != null && ChronoUnit.DAYS.between(date, today) in 0..2
            }
            .sumOf { item -> item.sets.count { set -> set.confirmed } }
        if (recentConfirmedSets >= 10) count += 1

        val recentHighRpeLoad = completedEntries.any { item ->
            val date = runCatching { LocalDate.parse(item.entry.date) }.getOrNull()
            date != null &&
                ChronoUnit.DAYS.between(date, today) in 0..2 &&
                item.sets.any { set ->
                    set.confirmed &&
                        (set.rpe ?: item.entry.rpe ?: 0.0) >= 9.0 &&
                        (set.weightKg >= 100.0 || set.seconds >= 600)
                }
        }
        if (recentHighRpeLoad) count += 1

        val recentSleepValues = dailyMetrics
            .filter { metric ->
                val date = runCatching { LocalDate.parse(metric.date) }.getOrNull()
                date != null && ChronoUnit.DAYS.between(date, today) in 0..3
            }
            .mapNotNull { it.sleepHours }
        val recentSleepLow = recentSleepValues.count { it < 6.0 } >= 2 || recentSleepValues.any { it < 5.0 }
        if (recentSleepLow || (initialProfile?.typicalSleepHours ?: 8.0) < 6.0) count += 1

        val recoveryBad = listOf(
            initialProfile?.currentFatigue,
            initialProfile?.currentSoreness,
            initialProfile?.currentStress
        ).filterNotNull().count { it >= 4 } >= 2
        if (recoveryBad) count += 1

        if (initialProfile?.hadRecentTrainingBreak == true &&
            ((initialProfile.breakWeeks ?: 0) >= 8 || initialProfile.breakDueToPain)
        ) {
            count += 1
        }
        if (initialProfile?.painAreas?.isNotBlank() == true) count += 1
        return count
    }
}

private enum class InitialReadinessMode {
    COLD_START,
    BASELINE_BUILDING,
    PERSONAL_BASELINE,
    RETURNING_AFTER_BREAK
}

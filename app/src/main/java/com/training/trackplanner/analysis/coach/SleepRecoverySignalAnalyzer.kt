package com.training.trackplanner.analysis.coach

import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.data.DailyMetric
import java.time.LocalDate

class SleepRecoverySignalAnalyzer {
    fun analyze(
        today: LocalDate,
        dailyMetrics: List<DailyMetric>,
        checkIns: List<DailyCheckIn> = emptyList()
    ): SleepRecoverySignal {
        val sleepByDate = canonicalSleepByDate(dailyMetrics, checkIns)
            .filterKeys { date -> date <= today }
        val recentStart = today.minusDays(2)
        val recentValues = sleepByDate
            .filterKeys { date -> date in recentStart..today }
            .values
            .toList()
        if (recentValues.isEmpty()) {
            return CoachingSignalsSummary.empty().sleep
        }

        val baselineStart = today.minusDays(27)
        val baselineEnd = today.minusDays(3)
        val baselineValues = sleepByDate
            .filterKeys { date -> date in baselineStart..baselineEnd }
            .values
            .toList()
        val recentAverage = recentValues.average()
        val baselineAverage = baselineValues.takeIf { it.size >= 3 }?.average()
        val deficit = baselineAverage?.let { baseline -> (baseline - recentAverage).coerceAtLeast(0.0) }
        val severity = when {
            recentAverage < 5.0 -> CoachingSignalSeverity.CAUTION
            recentAverage < 6.0 -> CoachingSignalSeverity.WATCH
            deficit != null && deficit >= 1.5 -> CoachingSignalSeverity.WATCH
            else -> CoachingSignalSeverity.INFO
        }

        return SleepRecoverySignal(
            recentAverageHours = recentAverage,
            baselineAverageHours = baselineAverage,
            sleepDeficitHours = deficit,
            severity = severity,
            headline = when (severity) {
                CoachingSignalSeverity.CAUTION -> "최근 수면이 많이 낮습니다"
                CoachingSignalSeverity.WATCH -> "최근 수면을 보수적으로 봅니다"
                CoachingSignalSeverity.INFO -> "최근 수면은 큰 경고 신호가 아닙니다"
                CoachingSignalSeverity.NONE -> "수면 기록 부족"
            },
            detail = detailText(recentAverage, baselineAverage, deficit, severity)
        )
    }

    private fun canonicalSleepByDate(
        dailyMetrics: List<DailyMetric>,
        checkIns: List<DailyCheckIn>
    ): Map<LocalDate, Double> {
        val checkInFallback = checkIns.mapNotNull { checkIn ->
            val date = runCatching { LocalDate.parse(checkIn.date) }.getOrNull() ?: return@mapNotNull null
            val hours = checkIn.sleepHours ?: return@mapNotNull null
            date to hours
        }.toMap()
        val metricSleep = dailyMetrics.mapNotNull { metric ->
            val date = runCatching { LocalDate.parse(metric.date) }.getOrNull() ?: return@mapNotNull null
            val hours = metric.sleepHours ?: return@mapNotNull null
            date to hours
        }.toMap()
        return checkInFallback + metricSleep
    }

    private fun detailText(
        recentAverage: Double,
        baselineAverage: Double?,
        deficit: Double?,
        severity: CoachingSignalSeverity
    ): String {
        val recent = recentAverage.formatOneDecimal()
        val baseline = baselineAverage?.formatOneDecimal()
        return when {
            baseline != null && deficit != null && deficit > 0.0 ->
                "최근 3일 평균 ${recent}시간, 기준 ${baseline}시간입니다. 오늘 강도 해석을 보수적으로 적용합니다."
            severity == CoachingSignalSeverity.CAUTION ->
                "최근 3일 평균 ${recent}시간입니다. 고강도 훈련 판단에는 여유를 둡니다."
            severity == CoachingSignalSeverity.WATCH ->
                "최근 3일 평균 ${recent}시간입니다. 피로 신호를 조금 더 보수적으로 봅니다."
            else ->
                "최근 3일 평균 ${recent}시간입니다. 수면만으로 강한 조절 신호를 만들지는 않습니다."
        }
    }
}

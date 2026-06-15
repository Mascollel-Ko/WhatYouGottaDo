package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.data.DailyMetric
import java.time.LocalDate

class RecoverySignalInterpreter {
    fun interpret(
        today: LocalDate,
        dailyMetrics: List<DailyMetric>
    ): RecoverySignalSnapshot {
        val todayMetric = dailyMetrics.firstOrNull { metric -> metric.date == today.toString() }
        val recentSleep = dailyMetrics
            .takeLast(7)
            .mapNotNull { metric -> metric.sleepHours }
        val sleepHours = todayMetric?.sleepHours ?: recentSleep.lastOrNull()
        val sleepSignal = sleepLevel(sleepHours)
        val poorSignals = listOf(sleepSignal).count { level ->
            level == FatigueLevel.ELEVATED || level == FatigueLevel.HIGH || level == FatigueLevel.VERY_HIGH
        }
        val recoveryPenalty = when {
            sleepSignal == FatigueLevel.HIGH || sleepSignal == FatigueLevel.VERY_HIGH -> 2
            sleepSignal == FatigueLevel.ELEVATED -> 1
            else -> 0
        }
        val confidence = if (sleepHours == null) AnalysisConfidence.LOW else AnalysisConfidence.MEDIUM_LOW
        val reasons = buildList {
            when (sleepSignal) {
                FatigueLevel.HIGH,
                FatigueLevel.VERY_HIGH -> add("수면 시간이 낮게 기록됐습니다.")
                FatigueLevel.ELEVATED -> add("수면이 평소보다 짧게 잡혔습니다.")
                else -> Unit
            }
            if (sleepHours == null) add("회복 입력이 적어 보수적으로 봅니다.")
            if (poorSignals >= 2) add("회복 신호가 여러 개 겹쳤습니다.")
        }

        return RecoverySignalSnapshot(
            sleepSignal = sleepSignal,
            fatigueSignal = FatigueLevel.NORMAL,
            sorenessSignal = FatigueLevel.NORMAL,
            stressSignal = FatigueLevel.NORMAL,
            moodSignal = FatigueLevel.NORMAL,
            overallRecoveryLevel = when {
                recoveryPenalty >= 2 -> FatigueLevel.HIGH
                recoveryPenalty == 1 -> FatigueLevel.ELEVATED
                else -> FatigueLevel.NORMAL
            },
            recoveryPenalty = recoveryPenalty,
            affectedBodyParts = emptyList(),
            confidence = confidence,
            reasons = reasons
        )
    }

    private fun sleepLevel(sleepHours: Double?): FatigueLevel =
        when {
            sleepHours == null -> FatigueLevel.NORMAL
            sleepHours < 5.5 -> FatigueLevel.HIGH
            sleepHours < 6.5 -> FatigueLevel.ELEVATED
            else -> FatigueLevel.NORMAL
        }
}

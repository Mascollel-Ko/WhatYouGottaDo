package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.DailyCheckIn
import java.time.LocalDate

class RecoverySignalInterpreter {
    fun interpret(
        today: LocalDate,
        dailyMetrics: List<DailyMetric>,
        dailyCheckIns: List<DailyCheckIn> = emptyList()
    ): RecoverySignalSnapshot {
        val todayMetric = dailyMetrics.firstOrNull { metric -> metric.date == today.toString() }
        val todayCheckIn = dailyCheckIns.firstOrNull { checkIn -> checkIn.date == today.toString() }
        val recentSleep = dailyMetrics
            .takeLast(7)
            .mapNotNull { metric -> metric.sleepHours }
        val sleepHours = todayMetric?.sleepHours ?: recentSleep.lastOrNull()
        val sleepSignal = sleepLevel(sleepHours)
        val fatigueSignal = checkInLevel(todayCheckIn?.overallFatigue)
        val sorenessSignal = maxOf(
            checkInLevel(todayCheckIn?.lowerBodyFatigue),
            checkInLevel(todayCheckIn?.jointTendonDiscomfort)
        )
        val moodSignal = focusLevel(todayCheckIn?.focusMotivation)
        val poorSignals = listOf(sleepSignal, fatigueSignal, sorenessSignal, moodSignal).count { level ->
            level == FatigueLevel.ELEVATED || level == FatigueLevel.HIGH || level == FatigueLevel.VERY_HIGH
        }
        val sleepPenalty = when {
            sleepSignal == FatigueLevel.HIGH || sleepSignal == FatigueLevel.VERY_HIGH -> 2
            sleepSignal == FatigueLevel.ELEVATED -> 1
            else -> 0
        }
        val checkInPenalty = listOf(fatigueSignal, sorenessSignal, moodSignal).map { level ->
            when (level) {
                FatigueLevel.VERY_HIGH,
                FatigueLevel.HIGH -> 1
                FatigueLevel.ELEVATED -> 1
                else -> 0
            }
        }.sum()
        val recoveryPenalty = (sleepPenalty + checkInPenalty).coerceAtMost(3)
        val confidence = when {
            sleepHours != null && todayCheckIn != null -> AnalysisConfidence.MEDIUM
            sleepHours != null || todayCheckIn != null -> AnalysisConfidence.MEDIUM_LOW
            else -> AnalysisConfidence.LOW
        }
        val reasons = buildList {
            when (sleepSignal) {
                FatigueLevel.HIGH,
                FatigueLevel.VERY_HIGH -> add("수면 시간이 낮게 기록됐습니다.")
                FatigueLevel.ELEVATED -> add("수면이 평소보다 짧게 잡혔습니다.")
                else -> Unit
            }
            if (fatigueSignal >= FatigueLevel.HIGH) add("전신 피로 입력이 높습니다.")
            if (todayCheckIn?.lowerBodyFatigue != null && checkInLevel(todayCheckIn.lowerBodyFatigue) >= FatigueLevel.HIGH) {
                add("하체 피로 입력이 높습니다.")
            }
            if (todayCheckIn?.jointTendonDiscomfort != null && checkInLevel(todayCheckIn.jointTendonDiscomfort) >= FatigueLevel.HIGH) {
                add("관절/건 불편감 입력이 높습니다.")
            }
            if (moodSignal >= FatigueLevel.HIGH) add("집중력/의욕 입력이 낮습니다.")
            if (sleepHours == null) add("회복 입력이 적어 보수적으로 봅니다.")
            if (poorSignals >= 2) add("회복 신호가 여러 개 겹쳤습니다.")
        }

        return RecoverySignalSnapshot(
            sleepSignal = sleepSignal,
            fatigueSignal = fatigueSignal,
            sorenessSignal = sorenessSignal,
            stressSignal = FatigueLevel.NORMAL,
            moodSignal = moodSignal,
            overallRecoveryLevel = when {
                recoveryPenalty >= 2 -> FatigueLevel.HIGH
                recoveryPenalty == 1 -> FatigueLevel.ELEVATED
                else -> FatigueLevel.NORMAL
            },
            recoveryPenalty = recoveryPenalty,
            affectedBodyParts = buildList {
                if ((todayCheckIn?.lowerBodyFatigue ?: 0) >= 4) add("하체")
                if ((todayCheckIn?.jointTendonDiscomfort ?: 0) >= 4) add("관절/건")
            },
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

    private fun checkInLevel(score: Int?): FatigueLevel =
        when (score) {
            null -> FatigueLevel.NORMAL
            5 -> FatigueLevel.HIGH
            4 -> FatigueLevel.HIGH
            3 -> FatigueLevel.ELEVATED
            else -> FatigueLevel.NORMAL
        }

    private fun focusLevel(score: Int?): FatigueLevel =
        when (score) {
            null -> FatigueLevel.NORMAL
            1 -> FatigueLevel.HIGH
            2 -> FatigueLevel.ELEVATED
            else -> FatigueLevel.NORMAL
        }
}

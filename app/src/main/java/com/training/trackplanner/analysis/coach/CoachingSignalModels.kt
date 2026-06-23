package com.training.trackplanner.analysis.coach

data class SleepRecoverySignal(
    val recentAverageHours: Double?,
    val baselineAverageHours: Double?,
    val sleepDeficitHours: Double?,
    val severity: CoachingSignalSeverity,
    val headline: String,
    val detail: String
)

enum class CoachingSignalSeverity {
    NONE,
    INFO,
    WATCH,
    CAUTION
}

data class RpeAutoregulationSignal(
    val exerciseName: String?,
    val severity: CoachingSignalSeverity,
    val headline: String,
    val detail: String,
    val sleepContext: String?,
    val sampleSize: Int
)

data class JointTendonWarningSignal(
    val severity: CoachingSignalSeverity,
    val headline: String,
    val detail: String,
    val relatedStressLabels: List<String>,
    val sleepContext: String?,
    val sampleSize: Int
)

data class CourtDurationRecoverySignal(
    val severity: CoachingSignalSeverity,
    val headline: String,
    val detail: String,
    val observedThresholdMinutes: Int?,
    val sampleSize: Int,
    val sleepContext: String?
)

data class CoachingSignalsSummary(
    val sleep: SleepRecoverySignal,
    val rpe: RpeAutoregulationSignal?,
    val jointTendon: JointTendonWarningSignal?,
    val courtRecovery: CourtDurationRecoverySignal?
) {
    companion object {
        fun empty() = CoachingSignalsSummary(
            sleep = SleepRecoverySignal(
                recentAverageHours = null,
                baselineAverageHours = null,
                sleepDeficitHours = null,
                severity = CoachingSignalSeverity.NONE,
                headline = "수면 기록 부족",
                detail = "최근 수면 입력이 부족해 수면 보정 신호를 계산하지 않았습니다."
            ),
            rpe = null,
            jointTendon = null,
            courtRecovery = null
        )
    }
}

internal fun CoachingSignalSeverity.priority(): Int = when (this) {
    CoachingSignalSeverity.NONE -> 0
    CoachingSignalSeverity.INFO -> 1
    CoachingSignalSeverity.WATCH -> 2
    CoachingSignalSeverity.CAUTION -> 3
}

internal fun Double.formatOneDecimal(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)

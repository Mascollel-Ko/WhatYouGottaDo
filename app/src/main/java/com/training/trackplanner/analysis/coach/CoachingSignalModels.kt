package com.training.trackplanner.analysis.coach

data class SleepRecoverySignal(
    val recentAverageHours: Double?,
    val baselineAverageHours: Double?,
    val sleepDeficitHours: Double?,
    val severity: CoachingSignalSeverity,
    val headline: String,
    val detail: String,
    val messageCode: SleepRecoveryMessageCode = when (severity) {
        CoachingSignalSeverity.NONE -> SleepRecoveryMessageCode.INSUFFICIENT_DATA
        CoachingSignalSeverity.CAUTION -> SleepRecoveryMessageCode.CAUTION
        CoachingSignalSeverity.WATCH -> SleepRecoveryMessageCode.WATCH
        CoachingSignalSeverity.INFO -> SleepRecoveryMessageCode.INFO
    }
)

enum class SleepRecoveryMessageCode {
    INSUFFICIENT_DATA,
    CAUTION,
    WATCH,
    INFO
}

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
    val sampleSize: Int,
    val recentAverageRpe: Double? = null,
    val baselineAverageRpe: Double? = null,
    val messageCode: RpeAutoregulationMessageCode = RpeAutoregulationMessageCode.INCREASED_AT_SIMILAR_LOAD
)

enum class RpeAutoregulationMessageCode {
    INCREASED_AT_SIMILAR_LOAD
}

data class JointTendonWarningSignal(
    val severity: CoachingSignalSeverity,
    val headline: String,
    val detail: String,
    val relatedStressLabels: List<String>,
    val sleepContext: String?,
    val sampleSize: Int,
    val messageCode: JointTendonWarningMessageCode = JointTendonWarningMessageCode.DISCOMFORT_ONLY
)

enum class JointTendonWarningMessageCode {
    RELATED_EXERCISE_STRESS,
    DISCOMFORT_ONLY
}

data class CourtDurationRecoverySignal(
    val severity: CoachingSignalSeverity,
    val headline: String,
    val detail: String,
    val observedThresholdMinutes: Int?,
    val sampleSize: Int,
    val sleepContext: String?,
    val messageCode: CourtDurationRecoveryMessageCode = CourtDurationRecoveryMessageCode.REFERENCE
)

enum class CourtDurationRecoveryMessageCode {
    INSUFFICIENT_DATA,
    LONG_DURATION_CAUTION,
    LONG_DURATION_WATCH,
    REFERENCE
}

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
                detail = "최근 수면 입력이 부족해 수면 보정 신호를 계산하지 않았습니다.",
                messageCode = SleepRecoveryMessageCode.INSUFFICIENT_DATA
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

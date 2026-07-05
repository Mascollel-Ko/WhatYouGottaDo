package com.training.trackplanner.data

data class ProgramEvaluation(
    val overallScore: Int,
    val weeklyScores: List<WeeklyProgramEvaluation>,
    val fatigueScore: Int,
    val strengthDistributionScore: Int,
    val badmintonTransferScore: Int,
    val densityScore: Int,
    val intensityDistributionScore: Int,
    val equipmentUtilizationScore: Int,
    val issues: List<ProgramEvaluationIssue>,
    val suggestions: List<String>
)

data class WeeklyProgramEvaluation(
    val weekIndex: Int,
    val score: Int,
    val fatigueScore: Int,
    val strengthDistributionScore: Int,
    val badmintonTransferScore: Int,
    val densityScore: Int,
    val intensityDistributionScore: Int,
    val issues: List<ProgramEvaluationIssue>
)

data class ProgramEvaluationIssue(
    val type: ProgramEvaluationIssueType,
    val severity: ProgramEvaluationIssueSeverity,
    val message: String
)

enum class ProgramEvaluationIssueSeverity { INFO, WARNING, SEVERE }

enum class ProgramEvaluationIssueType {
    SELECTED_MAIN_MISSING,
    LOW_STRENGTH_ANCHOR,
    LOADED_STRENGTH_UNDERUSED,
    LOW_SESSION_DENSITY,
    TOO_MUCH_CORE_REPETITION,
    MUSCLE_GROUP_OVERUSE,
    TRANSFER_GOAL_OVERUSE,
    HIGH_LOWER_BODY_FATIGUE_CLUSTER,
    NO_RECOVERY_AFTER_HIGH_FATIGUE_WEEK,
    NO_WEEK_VARIATION,
    WEEKLY_BALANCE_RECOVERS_LATER,
    PROGRAM_WIDE_IMBALANCE
}

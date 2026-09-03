package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.RuntimeExerciseMetadata
import java.time.LocalDate

internal const val PERSONALIZED_PLANNER_PROTOCOL = "RECORD_BASED_PLANNER_0.8.0_KOTLIN_1"
internal const val PERSONALIZED_AUTHORITY_VERSION = "canonical-v1+reference-planner-v0.8-reviewed"

enum class ObservedTrainingBehavior { HYPERTROPHY_DOMINANT, STRENGTH_DOMINANT, MIXED_STRENGTH_HYPERTROPHY, GENERAL_MIXED, UNKNOWN }
enum class StrengthExposure { PRESENT, LOW, ABSENT, UNKNOWN }
enum class StrengthIntent { STRENGTH_PRIORITY, MIXED, HYPERTROPHY_PRIORITY, AVOID_HEAVY, UNRESOLVED }
enum class BadmintonPlanningIntent { ENABLED, DISABLED, UNRESOLVED }
enum class FreeWeightWillingness { WILLING, AVOID, UNRESOLVED }
enum class StrengthProgrammingStyle { NONE, TOP_SET_HYPERTROPHY, TOP_SET_BACKOFF, STRAIGHT_5X5, STRAIGHT_STRENGTH_SETS, MADCOW_LIKE_HLM_RAMPING, HEAVY_LIGHT_MEDIUM, DUP_LIKE_UNDULATING, UNRESOLVED }
enum class PlanningConfidence { LOW, MODERATE, HIGH }

data class PersonalizedPlanningPreferences(
    val strengthIntent: StrengthIntent? = null,
    val badmintonIntent: BadmintonPlanningIntent? = null,
    val freeWeightWillingness: FreeWeightWillingness? = null
)

data class PersonalizedPlanningAnswerOption(val value: String, val label: String)

data class PersonalizedPlanningQuestion(
    val id: String,
    val prompt: String,
    val options: List<PersonalizedPlanningAnswerOption>
)

data class PersonalizedPlanningAnswers(val values: Map<String, String> = emptyMap())

data class PlanningSetRecord(
    val date: LocalDate,
    val stableKey: String,
    val exerciseName: String,
    val category: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int,
    val rpe: Double?
)

data class PlanningHistorySnapshot(
    val cutoff: LocalDate,
    val allConfirmedSets: List<PlanningSetRecord>,
    val exercises: Map<String, Exercise>,
    val metadata: Map<String, RuntimeExerciseMetadata>,
    val badmintonObjectives: Map<String, Map<String, Double>>,
    val profilePrimaryGoal: String,
    val strengthTrainingYears: Double,
    val badmintonTrainingYears: Double,
    val preferences: PersonalizedPlanningPreferences
) {
    val historyStart: LocalDate get() = allConfirmedSets.minOf(PlanningSetRecord::date)
    val historyDays: Int get() = java.time.temporal.ChronoUnit.DAYS.between(historyStart, cutoff).toInt() + 1
}

data class UserAnchor(
    val stableKey: String,
    val exerciseName: String,
    val sessions: Int,
    val sets: Int,
    val movementGroup: String,
    val metric: String,
    val response: String,
    val score: Double
)

data class AthletePlanningState(
    val observedBehavior: ObservedTrainingBehavior,
    val strengthExposure: StrengthExposure,
    val strengthIntent: StrengthIntent,
    val badmintonIntent: BadmintonPlanningIntent,
    val freeWeightWillingness: FreeWeightWillingness,
    val primaryAdaptation: String,
    val historyDays: Int,
    val recentTrainingDaysPerWeek: Double,
    val scheduleVolatility: Double,
    val machineSetRatio: Double,
    val freeWeightSetRatio: Double,
    val anchors: List<UserAnchor>,
    val observedStrengthStyle: StrengthProgrammingStyle,
    val observedStyleConfidence: PlanningConfidence,
    val structuredBadmintonSessions: Int,
    val recoveryConstraint: String,
    val confidence: PlanningConfidence
)

data class AdaptationGap(val code: String, val priority: String, val reason: String)

data class PersonalizedPlanningDecision(
    val decisionId: String,
    val protocolVersion: String,
    val generatedProgramId: Long? = null,
    val generatedAtEpochMillis: Long,
    val historyCutoff: String,
    val historyWindowDays: Int,
    val planningHorizonWeeks: Int,
    val adaptationIntentMinWeeks: Int,
    val adaptationIntentMaxWeeks: Int,
    val observedTrainingBehavior: String,
    val strengthIntent: String,
    val strengthIntentProvenance: String,
    val badmintonIntent: String,
    val badmintonIntentProvenance: String,
    val primaryAdaptation: String,
    val secondaryTargets: List<String>,
    val strengthStyle: String,
    val strengthStyleProvenance: String,
    val weeklyFrequency: Int,
    val confidence: String,
    val reasonCodes: List<String>,
    val reasons: List<String>,
    val constraints: List<String>,
    val metadataAuthorityVersion: String,
    val priorDecisionId: String? = null,
    val userAnswers: Map<String, String> = emptyMap()
)

sealed interface PersonalizedPlanningOutcome {
    data class Questions(val questions: List<PersonalizedPlanningQuestion>) : PersonalizedPlanningOutcome
    data class Generated(val skeleton: GeneratedProgramSkeleton) : PersonalizedPlanningOutcome
}

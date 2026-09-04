package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseRoleRelationCatalog
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.ProgramGoal
import java.time.LocalDate

internal const val PERSONALIZED_PLANNER_PROTOCOL = "RECORD_BASED_PLANNER_0.11.1_KOTLIN_1"
internal const val PERSONALIZED_AUTHORITY_VERSION = "canonical-v1+reference-planner-v0.11.1-reviewed"

enum class ObservedTrainingBehavior { HYPERTROPHY_DOMINANT, STRENGTH_DOMINANT, MIXED_STRENGTH_HYPERTROPHY, GENERAL_MIXED, UNKNOWN }
enum class StrengthExposure { PRESENT, LOW, ABSENT, UNKNOWN }
enum class StrengthIntent { STRENGTH_PRIORITY, MIXED, HYPERTROPHY_PRIORITY, AVOID_HEAVY, UNRESOLVED }
enum class BadmintonPlanningIntent { ENABLED, DISABLED, UNRESOLVED }
enum class FreeWeightWillingness { WILLING, PREFER_FAMILIAR, AVOID, UNRESOLVED }
enum class StrengthProgrammingStyle { NONE, TOP_SET_HYPERTROPHY, TOP_SET_BACKOFF, STRAIGHT_5X5, STRAIGHT_STRENGTH_SETS, MADCOW_LIKE_HLM_RAMPING, HEAVY_LIGHT_MEDIUM, DUP_LIKE_UNDULATING, UNRESOLVED }
enum class PlanningConfidence { LOW, MODERATE, HIGH }
enum class StructureTreatment { PRESERVE, PRESERVE_CORE_REBALANCE, PARTIAL_CONTINUITY, ROTATE_EMPHASIS }
enum class DoseTreatment { MAINTAIN, REDUCE_SLIGHTLY, REDUCE_MODERATELY }
enum class PlannedActivityKind { RESISTANCE, STRUCTURED_BADMINTON_DRILL, ATHLETIC_PERFORMANCE_DRILL, GENERIC_COURT_SESSION, OTHER }
enum class MovementCoverage {
    LOWER_KNEE, POSTERIOR_CHAIN, HORIZONTAL_PUSH, HORIZONTAL_PULL, VERTICAL_PUSH, VERTICAL_PULL,
    CORE_DIRECT, CALVES, ARMS_BICEPS, ARMS_TRICEPS, OTHER
}
enum class ProgressionDecision { ADVANCE, HOLD, REDUCE, REVIEW }
enum class RepresentationState { ABSENT, STRONG_UNDERREPRESENTATION_SIGNAL, UNDERREPRESENTATION_SIGNAL, NO_CLEAR_DEFICIT_SIGNAL, UNKNOWN }
enum class RepresentationPriority { HIGH, MODERATE }

data class MovementExposureRepresentation(
    val movementCoverage: String,
    val basePriority: RepresentationPriority,
    val currentExposure28d: Double,
    val priorExposure28d: Double,
    val currentActiveBins: Int,
    val currentShare: Double?,
    val priorShare: Double?,
    val peerReference: Double?,
    val peerRepresentationRatio: Double?,
    val personalRetentionRatio: Double?,
    val representationState: RepresentationState,
    val evidenceConfidence: PlanningConfidence,
    val reasonCodes: List<String>
)

data class BadmintonObjectiveRepresentation(
    val objective: String,
    val currentWeighted28d: Double,
    val priorWeighted28d: Double,
    val currentDirect28d: Double,
    val priorDirect28d: Double,
    val currentShare: Double?,
    val priorShare: Double?,
    val personalRetentionRatio: Double?,
    val peerMedianCurrent: Double?,
    val peerRepresentationRatio: Double?,
    val currentActiveBins: Int,
    val evidenceConfidence: PlanningConfidence,
    val directDrop: Boolean,
    val neverDirectObserved: Boolean,
    val representationState: RepresentationState,
    val reasonCodes: List<String>
)

data class CanonicalStrengthSignal(
    val posteriorMedianKg: Double? = null,
    val posteriorChangePercent: Double? = null,
    val observationCount: Int = 0,
    val source: String = "UNKNOWN"
)

data class PlanningRecoverySignals(
    val readinessStatus: String = "UNKNOWN",
    val readinessConfidence: String = "LOW",
    val overallFatigueIndex: Int? = null,
    val restrictedModes: Set<String> = emptySet(),
    val tissueStatus: String = "UNKNOWN",
    val tissueRestrictedStableKeys: Set<String> = emptySet(),
    val sourceCodes: Set<String> = emptySet()
) {
    val isConstrained: Boolean
        get() = readinessStatus in setOf("CAUTION", "FATIGUED", "LIMITED") ||
            tissueStatus in setOf("HIGH", "VERY_HIGH", "BLOCKED") ||
            (overallFatigueIndex ?: 0) >= 70
}

data class PersonalizedPlanningPreferences(
    val strengthIntent: StrengthIntent? = null,
    val badmintonIntent: BadmintonPlanningIntent? = null,
    val freeWeightWillingness: FreeWeightWillingness? = null,
    val strengthIntentAnsweredAtEpochMillis: Long? = null,
    val strengthIntentProfileGoal: String? = null
)

data class PersonalizedPlanningAnswerOption(val value: String, val label: String)

data class PersonalizedPlanningQuestion(
    val id: String,
    val prompt: String,
    val options: List<PersonalizedPlanningAnswerOption>
)

data class PersonalizedPlanningAnswers(val values: Map<String, String> = emptyMap())

data class PersonalizedGenerationConstraints(
    val explicitGoal: ProgramGoal? = null,
    val explicitWeeklyTrainingDays: Int? = null,
    val explicitDurationWeeks: Int? = null,
    val explicitSessionMinutes: Int? = null
)

data class PersonalizedPlanningPreflight(
    val preparationId: String,
    val cutoff: LocalDate,
    val request: ProgramSkeletonRequest,
    val constraints: PersonalizedGenerationConstraints,
    val questions: List<PersonalizedPlanningQuestion>,
    val preparedAtEpochMillis: Long
)

data class StyleFeatures(
    val weeklyFrequency: Double = 0.0,
    val frequencyStability: Double = 0.0,
    val loadUndulation: Double = 0.0,
    val repZoneUndulation: Double = 0.0,
    val hlmOrdering: Double = 0.0,
    val withinSessionRamping: Double = 0.0,
    val topSetBackoff: Double = 0.0,
    val straightSetConsistency: Double = 0.0,
    val heavyExposure: Double = 0.0,
    val moderateHighRepExposure: Double = 0.0,
    val weeksObserved: Int = 0
)

data class AdaptationState(
    val strengthResponse: Double,
    val responseConfidence: Double,
    val styleMaturity: Double,
    val rotationReadinessEvidence: Double,
    val gapPressure: Double,
    val systemicRecoveryPressure: Double,
    val sportInterferencePressure: Double,
    val goalAlignment: Double,
    val styleDemand: Double
)

data class AnchorTransition(
    val stableKey: String,
    val observedStyle: StrengthProgrammingStyle,
    val observedConfidence: PlanningConfidence,
    val styleFeatures: StyleFeatures,
    val adaptation: AdaptationState,
    val structureTreatment: StructureTreatment,
    val doseTreatment: DoseTreatment,
    val continuityScore: Double,
    val localDoseFactor: Double,
    val rotationPressure: Double,
    val preservedFeatures: List<String>,
    val moderatedFeatures: List<String>,
    val reasons: List<String>
)

data class PlanningBudget(
    val baselineResistanceSets: Double,
    val targetResistanceSets: Int,
    val plannedResistanceSets: Int,
    val targetStructuredBadmintonBouts: Int,
    val plannedStructuredBadmintonBouts: Int,
    val systemicDoseFactor: Double,
    val targetAthleticPerformanceBouts: Int = 0,
    val plannedAthleticPerformanceBouts: Int = 0
)

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
    val preferences: PersonalizedPlanningPreferences,
    val genericCourtLoad: Double = 0.0,
    val genericCourtLoad28d: Double = 0.0,
    val objectiveExposure: Map<String, Double> = emptyMap(),
    val canonicalStrengthSignals: Map<String, CanonicalStrengthSignal> = emptyMap(),
    val recoverySignals: PlanningRecoverySignals = PlanningRecoverySignals(),
    val badmintonDirectObjectives: Map<String, Set<String>> = emptyMap(),
    val exerciseRoleCatalog: ExerciseRoleRelationCatalog = ExerciseRoleRelationCatalog.EMPTY
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
    val score: Double,
    val style: StrengthProgrammingStyle = StrengthProgrammingStyle.UNRESOLVED,
    val styleConfidence: PlanningConfidence = PlanningConfidence.LOW,
    val canonicalPerformanceSource: String = "UNKNOWN",
    val posteriorChangePercent: Double? = null,
    val posteriorObservationCount: Int = 0
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
    val confidence: PlanningConfidence,
    val profileGoal: String = "MIXED",
    val programGoal: ProgramGoal = ProgramGoal.FUNCTIONAL_CONDITIONING,
    val objectiveExposure: Map<String, Double> = emptyMap(),
    val objectiveDropGaps: Set<String> = emptySet(),
    val objectiveDevelopmentalGaps: Set<String> = emptySet(),
    val genericCourtLoad: Double = 0.0,
    val recoverySignals: PlanningRecoverySignals = PlanningRecoverySignals(),
    val hypertrophyStimulusByMovement: Map<MovementCoverage, Double> = emptyMap(),
    val styleFeaturesByAnchor: Map<String, StyleFeatures> = emptyMap(),
    val movementRepresentations: List<MovementExposureRepresentation> = emptyList(),
    val badmintonObjectiveRepresentations: List<BadmintonObjectiveRepresentation> = emptyList(),
    val resistanceFoundationalOnramp: Boolean = false,
    val badmintonFoundationalOnramp: Boolean = false
)

data class AdaptationGap(
    val code: String,
    val priority: String,
    val reason: String,
    val sourceType: String = "LEGACY",
    val representationState: RepresentationState? = null,
    val evidenceConfidence: PlanningConfidence? = null,
    val currentExposure: Double? = null,
    val priorExposure: Double? = null,
    val currentShare: Double? = null,
    val priorShare: Double? = null,
    val peerRatio: Double? = null,
    val personalRetentionRatio: Double? = null,
    val reasonCodes: List<String> = emptyList(),
    val contributesTransitionPressure: Boolean = true
)

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
    val userAnswers: Map<String, String> = emptyMap(),
    val generatedProgramStableKey: String? = null,
    val originalGenerationFingerprint: String = "",
    val userEditedAfterGeneration: Boolean = false,
    val finalSavedFingerprint: String = "",
    val recoverySignalCodes: List<String> = emptyList(),
    val genericCourtLoad: Double = 0.0,
    val objectiveExposure: Map<String, Double> = emptyMap(),
    val anchorTransitions: List<AnchorTransition> = emptyList(),
    val planningBudget: PlanningBudget? = null,
    val movementRepresentations: List<MovementExposureRepresentation> = emptyList(),
    val badmintonObjectiveRepresentations: List<BadmintonObjectiveRepresentation> = emptyList(),
    val adaptationGaps: List<AdaptationGap> = emptyList()
)

sealed interface PersonalizedPlanningOutcome {
    data class Questions(val questions: List<PersonalizedPlanningQuestion>) : PersonalizedPlanningOutcome
    data class Generated(val skeleton: GeneratedProgramSkeleton) : PersonalizedPlanningOutcome
}

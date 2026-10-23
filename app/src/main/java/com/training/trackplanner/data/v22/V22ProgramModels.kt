package com.training.trackplanner.data.v22

import com.training.trackplanner.data.GeneratedProgramSkeleton
import java.time.LocalDate

enum class V22Mode { A, B, C }

enum class BBalancedGoal { STRENGTH, HYPERTROPHY, CORE, SPORT_SUPPORT }

/** Null means that B should derive a recommendation from the available record. */
data class BBalancedGoalImportance(
    val strength: Int? = null,
    val hypertrophy: Int? = null,
    val core: Int? = null,
    val sportSupport: Int? = null
) {
    init {
        listOfNotNull(strength, hypertrophy, core, sportSupport).forEach {
            require(it in 1..5) { "B goal importance must be 1..5" }
        }
    }

    fun value(goal: BBalancedGoal): Int? = when (goal) {
        BBalancedGoal.STRENGTH -> strength
        BBalancedGoal.HYPERTROPHY -> hypertrophy
        BBalancedGoal.CORE -> core
        BBalancedGoal.SPORT_SUPPORT -> sportSupport
    }
}

data class V22GenerationRequest(
    val name: String,
    val weeks: Int,
    val days: Int,
    val mode: V22Mode,
    val asOf: LocalDate,
    val focusStableKey: String? = null,
    val sessionMinutes: Int? = null,
    val backupName: String = "APP_WORKOUT_HISTORY",
    val bGoalImportance: BBalancedGoalImportance = BBalancedGoalImportance(),
    val bMaxTrainingDays: Int? = null // explicit permission to expand B from three to four days
)

/**
 * The new-program B-only UI contract.
 * A and C remain available to older plans, parity tests and the B baseline comparator.
 */
internal fun newBBalancedGenerationRequest(
    name: String,
    days: Int,
    asOf: LocalDate,
    sessionMinutes: Int?,
    allowFourthDay: Boolean,
    importance: BBalancedGoalImportance
): V22GenerationRequest = V22GenerationRequest(
    name = name,
    weeks = 12,
    days = days,
    mode = V22Mode.B,
    asOf = asOf,
    sessionMinutes = sessionMinutes,
    bMaxTrainingDays = if (days == 3 && allowFourthDay) 4 else null,
    bGoalImportance = importance
)

data class V22HistoryRow(
    val date: LocalDate,
    val exerciseName: String,
    val stableKey: String,
    val category: String,
    val setIndex: Int,
    val reps: Double?,
    val weightKg: Double?,
    val seconds: Double?,
    val rpe: Double?
)

data class V22ObservedSet(
    val weightKg: Double?,
    val reps: Int?,
    val seconds: Int?,
    val observedRpe: Double?,
    val sourceDate: String,
    val loadStatus: String
)

data class V22ExercisePlan(
    val exercise: String,
    val stableKey: String,
    val category: String,
    val role: String,
    val setCount: Int,
    val sets: List<V22ObservedSet>,
    val historicalSets28d: Int,
    val historicalSets56d: Int,
    val source: String = "LATEST_COMPLETED_SESSION_REUSE_NOT_NEW_PRESCRIPTION",
    val prescriptionAuthority: String? = null,
    val plannedProgression: String? = null,
    val block: Int? = null
)

data class V22Session(
    val session: Int,
    val items: List<V22ExercisePlan>,
    val nominalSets: Int
)

data class V22BaselineProgram(
    val status: String,
    val reasons: List<String> = emptyList(),
    val sessions: List<V22Session> = emptyList(),
    val weeklyNominalSets: Int? = null,
    val exerciseCount: Int? = null,
    val retainedHistoryExerciseCount: Int? = null,
    val omittedEligibleExercises: List<String> = emptyList(),
    val roleSets: Map<String, Int> = emptyMap(),
    val placementError: V22PlacementError? = null
)

data class V22PlacementError(
    val exercise: String,
    val reason: String,
    val remainingSets: Int
)

data class V22TrainingWeekEvidence(
    val start: String,
    val end: String,
    val trainingDays: Int,
    val excludedBottom30Percent: Boolean
)

data class V22TrainingDayInference(
    val window: String,
    val categories: List<String>,
    val weeks: List<V22TrainingWeekEvidence>,
    val excludedWeekCount: Int,
    val rounding: String,
    val retainedMean: Double
)

data class V22BaselineGeneration(
    val schema: String,
    val source: String,
    val asOf: String,
    val trainingDays: Int,
    val trainingDaysSource: String,
    val trainingDayInference: V22TrainingDayInference,
    val profiles: List<V22ExerciseProfile>,
    val programs: Map<V22Mode, V22BaselineProgram>,
    val limitations: List<String>
)

data class V22ExerciseProfile(
    val key: String,
    val name: String,
    val category: String,
    val role: String,
    val sets28: Int,
    val sets56: Int,
    val days28: Int,
    val days56: Int,
    val lastDate: String,
    val ageDays: Long,
    val weekly28: Double,
    val weekly56: Double,
    val priority: Double,
    internal val recentShape: List<V22HistoryRow>
)

data class V22RoleEvidence(
    val historyWeeklyNominal: Double,
    val baselinePlanned: Int,
    val gap: Double
)

data class V22Reallocation(
    val from: String,
    val to: String,
    val donorRole: String,
    val receiverRole: String,
    val amountNominalSets: Int,
    val reason: String
)

data class V22Block(
    val block: Int,
    val startWeek: Int,
    val endWeek: Int,
    val phase: String,
    val plannedReallocations: List<V22Reallocation>,
    val strengthPolicy: String,
    val hypertrophyPolicy: String,
    val recoveryPolicy: String,
    val plannedRoleSets: Map<String, Int>
)

data class V22Week(
    val week: Int,
    val block: Int,
    val sessions: List<V22Session>,
    val weeklyNominalSets: Int
)

data class V22PlanInput(
    val backup: String,
    val asOf: String,
    val weeks: Int,
    val days: Int,
    val mode: String,
    val focusStableKey: String?,
    val sessionMinutes: Int?
)

data class V22Authority(
    val stimulus: String,
    val prescription: String,
    val executionMissCarryover: Boolean,
    val structuralPlanningOnly: Boolean
)

data class V22Plan(
    val schema: String,
    val input: V22PlanInput,
    val historyEvidence: Map<String, V22RoleEvidence>,
    val blocks: List<V22Block>,
    val weeks: List<V22Week>,
    val baselineWeeklyNominalSets: Int,
    val authority: V22Authority,
    val limitations: List<String>
)

data class V22GeneratedProgram(
    /** Frozen comparator. It is null only when B has no personal history from which to build it. */
    val plan: V22Plan?,
    val skeleton: GeneratedProgramSkeleton,
    /** Present only for the live B path. [plan] remains the frozen Python-parity comparator. */
    val balanced12WeekPlan: BBalanced12WeekPlan? = null
)

enum class BBalancedNumericAuthority {
    RESEARCH_REFERENCE_AND_PERSONAL_HISTORY,
    PERSONAL_HISTORY_ONLY,
    DIRECTION_ONLY,
    UNRESOLVED
}

data class BBalancedConfidence(
    val history: Double,
    val personalResponse: Double,
    val reasons: List<String>
)

data class BBalancedGoalTarget(
    val goal: BBalancedGoal,
    val unit: String,
    val importance: Int,
    val importanceSource: String,
    val researchReference: Double?,
    val researchReferenceSource: String?,
    val recentHistory: Double?,
    val personalResponseCandidate: Double?,
    val confidence: BBalancedConfidence,
    val developmentTarget: Double?,
    val maintenanceFloor: Double?,
    val maintenanceSource: String?,
    val numericAuthority: BBalancedNumericAuthority,
    val reasons: List<String>
)

data class BBalancedGoalEvaluation(
    val goal: BBalancedGoal,
    val plannedDose: Double,
    val developmentTarget: Double?,
    val maintenanceFloor: Double?,
    val developmentShortfall: Double?,
    val maintenanceShortfall: Double?,
    val status: String
)

data class BBalancedBlock(
    val block: Int,
    val startWeek: Int,
    val endWeek: Int,
    val focus: BBalancedGoal,
    val focusReasons: List<String>,
    val sessions: List<V22Session>,
    val evaluation: List<BBalancedGoalEvaluation>
)

data class BBalancedSearchStats(
    val evaluatedCandidates: Int,
    val feasibleCandidates: Int,
    val acceptedImprovements: Int,
    val minimumImprovement: Double,
    val terminationReason: String
)

data class BBalanced12WeekPlan(
    val schema: String,
    val input: V22PlanInput,
    val goalTargets: List<BBalancedGoalTarget>,
    val blocks: List<BBalancedBlock>,
    val weeks: List<V22Week>,
    val search: BBalancedSearchStats,
    val legacyComparatorSchema: String,
    val limitations: List<String>,
    val metadataJson: String
)
